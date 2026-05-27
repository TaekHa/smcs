#!/usr/bin/env bash
# Story 4.6 — daily Postgres backup (custom format).
# Cron entry (operations.md §4):
#   0 3 * * * /opt/smcs/scripts/backup-db.sh >> /var/log/smcs/backup-db.log 2>&1
# Keeps 30 days of dumps; older files are removed automatically.
#
# DR caveat: PO Deviation #3 — backups live on the same host as the database.
# Disk loss = simultaneous loss of dump + live DB. Mirror to NAS/S3 in v2.

set -euo pipefail

BACKUP_ROOT="${BACKUP_ROOT:-/backup}"
BACKUP_DIR="$BACKUP_ROOT/db"
CONTAINER="${POSTGRES_CONTAINER:-smcs_postgres}"
DB_NAME="${POSTGRES_DB:-smcs}"
DB_USER="${POSTGRES_USER:-smcs}"
DATE="$(date +%F)"
OUT="$BACKUP_DIR/smcs-db-$DATE.dump"

mkdir -p "$BACKUP_DIR"

if ! docker inspect -f '{{.State.Running}}' "$CONTAINER" 2>/dev/null | grep -q true; then
    echo "[backup-db] ERROR: container '$CONTAINER' is not running" >&2
    exit 1
fi

# Stream pg_dump output from inside the container to a file on the host.
docker exec -i "$CONTAINER" pg_dump \
    --format=custom \
    --no-acl \
    --no-owner \
    -U "$DB_USER" \
    -d "$DB_NAME" > "$OUT"

# Rotate: keep last 30 days.
find "$BACKUP_DIR" -name 'smcs-db-*.dump' -type f -mtime +30 -delete

SIZE="$(du -h "$OUT" | cut -f1)"
echo "[backup-db] wrote $OUT ($SIZE)"
