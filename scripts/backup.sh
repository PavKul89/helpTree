#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

BACKUP_DIR="${BACKUP_DIR:?Set BACKUP_DIR to a secure backup directory}"
BACKUP_KEEP="${BACKUP_KEEP:-14}"
SILO_BUCKET="${SILO_BUCKET:-helptree-images}"
BACKUP_SILO_ALIAS="${BACKUP_SILO_ALIAS:-helptree-silo}"

if [[ ! "$BACKUP_KEEP" =~ ^[1-9][0-9]*$ ]]; then
  echo "BACKUP_KEEP must be a positive integer" >&2
  exit 2
fi

if [[ -n "${DB_URL:-}" ]]; then
  if [[ "$DB_URL" =~ ^jdbc:postgresql://([^/:?#]+)(:([0-9]+))?/([^?]+) ]]; then
    PGHOST="${PGHOST:-${BASH_REMATCH[1]}}"
    PGPORT="${PGPORT:-${BASH_REMATCH[3]:-5432}}"
    PGDATABASE="${PGDATABASE:-${BASH_REMATCH[4]}}"
  else
    echo "DB_URL is not a supported PostgreSQL JDBC URL; set PGHOST/PGPORT/PGDATABASE" >&2
    exit 2
  fi
fi

export PGHOST="${PGHOST:-localhost}"
export PGPORT="${PGPORT:-5432}"
export PGDATABASE="${PGDATABASE:-${DB_NAME:-helpTree_db}}"
export PGUSER="${PGUSER:-${DB_USER:-}}"
if [[ -z "${PGPASSWORD:-}" && -n "${DB_PASS:-}" ]]; then
  export PGPASSWORD="$DB_PASS"
fi

if [[ -z "$PGUSER" ]]; then
  echo "Set PGUSER or DB_USER for PostgreSQL authentication" >&2
  exit 2
fi
if ! command -v pg_dump >/dev/null 2>&1 || ! command -v pg_restore >/dev/null 2>&1; then
  echo "pg_dump and pg_restore are required" >&2
  exit 127
fi

if [[ "${BACKUP_SKIP_SILO:-false}" != "true" ]]; then
  if ! command -v mc >/dev/null 2>&1; then
    echo "mc is required for Silo backup; configure a volume snapshot/rclone or set BACKUP_SKIP_SILO=true explicitly" >&2
    exit 127
  fi
  if ! mc ls "$BACKUP_SILO_ALIAS/$SILO_BUCKET" >/dev/null; then
    echo "Cannot access Silo bucket via configured mc alias '$BACKUP_SILO_ALIAS'" >&2
    exit 1
  fi
fi

mkdir -p "$BACKUP_DIR"
backup_root="$(cd "$BACKUP_DIR" && pwd -P)"
date_stamp="$(date +%Y%m%d_%H%M)"
dump_path="$backup_root/helptree_${date_stamp}.dump"
temp_path="$backup_root/.helptree_${date_stamp}.dump.tmp.$$"
trap 'rm -f -- "$temp_path"' EXIT

pg_dump --format=custom --file="$temp_path"
pg_restore --list "$temp_path" >/dev/null
mv -f -- "$temp_path" "$dump_path"

if [[ "${BACKUP_SKIP_SILO:-false}" != "true" ]]; then
  silo_path="$backup_root/silo/$date_stamp"
  mkdir -p "$silo_path"
  mc mirror --overwrite "$BACKUP_SILO_ALIAS/$SILO_BUCKET" "$silo_path"
fi

shopt -s nullglob
dump_candidates=("$backup_root"/helptree_*.dump)
dump_files=()
for candidate in "${dump_candidates[@]}"; do
  [[ -f "$candidate" ]] && dump_files+=("$candidate")
done

excess=$(( ${#dump_files[@]} - BACKUP_KEEP ))
for ((index = 0; index < excess; index++)); do
  rm -- "${dump_files[index]}"
done

echo "Database backup verified: $dump_path"
if [[ "${BACKUP_SKIP_SILO:-false}" == "true" ]]; then
  echo "WARNING: Silo backup was explicitly skipped; use a host volume snapshot or rclone." >&2
else
  echo "Silo backup mirrored to $backup_root/silo/$date_stamp"
fi
