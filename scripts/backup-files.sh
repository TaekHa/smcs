#!/usr/bin/env bash
# Story 4.6 — daily attachment backup.
# Cron entry (operations.md §4):
#   5 3 * * * /opt/smcs/scripts/backup-files.sh >> /var/log/smcs/backup-files.log 2>&1
# Prefers rsync (incremental); falls back to tar.gz when rsync is unavailable.
# Keeps 30 days; older entries are removed automatically.
#
# DR caveat: PO Deviation #3 — backups live on the same host as the source.
# Disk loss = simultaneous loss of backup + live attachments. Mirror to NAS/S3 in v2.

set -euo pipefail

BACKUP_ROOT="${BACKUP_ROOT:-/backup}"
BACKUP_DIR="$BACKUP_ROOT/files"
SOURCE_DIR="${SMCS_FILES_DIR:-/data/smcs/files}"
DATE="$(date +%F)"

mkdir -p "$BACKUP_DIR"

if [ ! -d "$SOURCE_DIR" ]; then
    echo "[backup-files] ERROR: source dir $SOURCE_DIR not found" >&2
    exit 1
fi

if command -v rsync >/dev/null 2>&1; then
    OUT="$BACKUP_DIR/$DATE"
    mkdir -p "$OUT"
    rsync -a --delete "$SOURCE_DIR/" "$OUT/"
    SIZE="$(du -sh "$OUT" | cut -f1)"
    echo "[backup-files] rsync → $OUT ($SIZE)"
else
    OUT="$BACKUP_DIR/smcs-files-$DATE.tar.gz"
    tar czf "$OUT" -C "$(dirname "$SOURCE_DIR")" "$(basename "$SOURCE_DIR")"
    SIZE="$(du -h "$OUT" | cut -f1)"
    echo "[backup-files] tar → $OUT ($SIZE)"
fi

# Rotate: drop entries (dir or tar) older than 30 days. -mindepth keeps the dir itself.
find "$BACKUP_DIR" -mindepth 1 -maxdepth 1 -mtime +30 -exec rm -rf {} +
