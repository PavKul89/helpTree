# Database and Silo backups

## Daily backups

Set `BACKUP_DIR` explicitly and run `scripts/backup.sh` once a day from cron on
the server. Install PostgreSQL
client tools (`pg_dump`, `pg_restore`, and `psql`) and `mc`. Set `BACKUP_DIR`
to a directory with enough free space, outside the application checkout; the
script keeps the newest `BACKUP_KEEP` custom-format dumps (14 by default).
Only `helptree_*.dump` files directly inside `BACKUP_DIR` are rotated.

Connection settings use standard `PGHOST`, `PGPORT`, `PGDATABASE`, `PGUSER`,
and `PGPASSWORD` variables. If unset, host/database/user/password can be read
from `DB_URL`, `DB_USER`, and `DB_PASS` respectively. Prefer a root-owned
environment file or secrets manager for cron; never put credentials in the
script, command line, or this document. Restrict its permissions to the
backup account.

Configure an authenticated MinIO Client alias outside the repository and set
`BACKUP_SILO_ALIAS` and `SILO_BUCKET` in the backup environment. The script
mirrors the bucket into `BACKUP_DIR/silo/YYYYMMDD_HHMM/`; these object snapshots
are not rotated by `BACKUP_KEEP`. Apply an appropriate storage lifecycle or
offsite retention policy. The script validates the custom-format dump with
`pg_restore --list` before publishing it. A failure to access Silo is reported
as a failed backup even if the database dump completed.

Example daily cron entry (adjust the install path and schedule/time zone):

```cron
0 2 * * * bash /opt/helptree/scripts/backup.sh >> /var/log/helptree-backup.log 2>&1
```

If `mc` cannot be installed or configured, use a provider-supported snapshot
of the Silo data volume (`silo_data`/`/data`) or configure an S3-compatible
`rclone` remote and mirror the bucket into the same dated backup layout.
Volume snapshots should be consistent and taken while the storage service is
quiesced, or through the hosting provider's documented snapshot mechanism.
`BACKUP_SKIP_SILO=true` explicitly makes `backup.sh` database-only; it is not
a replacement for a Silo backup. Copy the completed backup directory to a
separate server/account or encrypted object-storage location. A backup kept
only on the application server is not a disaster-recovery copy.

## Restore

Create a separate, empty PostgreSQL database for recovery testing, then set
the connection variables to target it and run:

```sh
PGDATABASE=helptree_restore_test \
  bash scripts/restore.sh /var/backups/helptree/helptree_YYYYMMDD_HHMM.dump
```

The actual backup filename uses `YYYYMMDD_HHMM`. Without `--force`, restore refuses
to run when the target database contains tables. `--force` allows
`pg_restore --clean --if-exists` to replace objects included in the archive;
it is destructive and should only be used after verifying the target database
and dump path.

To restore Silo objects, configure `mc` with a separate empty destination
bucket and mirror the desired dated snapshot, for example:

```sh
mc mirror /var/backups/helptree/silo/YYYYMMDD_HHMM <configured-alias>/<empty-bucket>
```

## Recovery verification

Perform a restore drill **at least once a month** in an isolated database and
storage bucket, never over production. Check the dump with `pg_restore --list`,
restore it, compare row counts in representative tables (for example
`users`, `posts`, `notifications`, and `outbox_events`) against the source
captured at backup time, and verify that representative stored objects can be
read. Record the date, dump, counts, and result in the operations log. A backup
is not considered verified until both database and object recovery have been
tested.

## Notification outbox retention

Delivered outbox rows use status `PUBLISHED` (equivalent to a sent event in the
delivery flow); they are removed in bounded batches after
`NOTIFICATIONS_RETENTION_DAYS` days (default 30). Tune the batch limit and
daily cleanup schedule with `NOTIFICATIONS_RETENTION_BATCH_SIZE` and
`NOTIFICATIONS_RETENTION_CRON`. `DEAD` rows are intentionally retained for
investigation; monitor the `helptree.outbox.dead` metric and warning logs.
PostgreSQL reuses space from deleted rows after vacuum; ordinary `DELETE`
does not necessarily shrink the table file on disk.
