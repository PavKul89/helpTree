#!/usr/bin/env bash
set -Eeuo pipefail

force=false
dump_path=""
for argument in "$@"; do
  case "$argument" in
    --force) force=true ;;
    -*)
      echo "Usage: $0 [--force] <backup.dump>" >&2
      exit 2
      ;;
    *)
      if [[ -n "$dump_path" ]]; then
        echo "Only one dump file may be restored at a time" >&2
        exit 2
      fi
      dump_path="$argument"
      ;;
  esac
done

if [[ -z "$dump_path" || ! -f "$dump_path" ]]; then
  echo "Usage: $0 [--force] <existing-backup.dump>" >&2
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
for tool in pg_restore psql; do
  if ! command -v "$tool" >/dev/null 2>&1; then
    echo "$tool is required" >&2
    exit 127
  fi
done

pg_restore --list "$dump_path" >/dev/null
if [[ "$force" != true ]]; then
  has_tables="$(psql --no-psqlrc --tuples-only --no-align \
    --command="SELECT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema NOT IN ('pg_catalog', 'information_schema') AND table_type = 'BASE TABLE')" \
    "$PGDATABASE")"
  if [[ "$has_tables" == "t" ]]; then
    echo "Target database '$PGDATABASE' already contains tables; use --force to continue" >&2
    exit 1
  fi
fi

restore_options=(--exit-on-error --no-owner --no-acl)
if [[ "$force" == true ]]; then
  restore_options+=(--clean --if-exists)
fi
pg_restore "${restore_options[@]}" --dbname="$PGDATABASE" "$dump_path"
echo "Restored $dump_path into database '$PGDATABASE'"
