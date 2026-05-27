#!/usr/bin/env bash
# Story 4.6 — restore Postgres from a pg_dump custom-format file.
# Usage:
#   ./restore-db.sh /backup/db/smcs-db-YYYY-MM-DD.dump
#
# WARNING: --clean drops existing objects before restore. Stop backend first:
#   docker compose -f docker/docker-compose.prod.yml stop backend
# After a successful restore, restart it:
#   docker compose -f docker/docker-compose.prod.yml start backend

set -euo pipefail

if [ $# -ne 1 ]; then
    echo "Usage: $0 <dump-file>" >&2
    exit 1
fi

DUMP="$1"
CONTAINER="${POSTGRES_CONTAINER:-smcs_postgres}"
DB_NAME="${POSTGRES_DB:-smcs}"
DB_USER="${POSTGRES_USER:-smcs}"

if [ ! -f "$DUMP" ]; then
    echo "ERROR: $DUMP not found" >&2
    exit 1
fi

if ! docker inspect -f '{{.State.Running}}' "$CONTAINER" 2>/dev/null | grep -q true; then
    echo "ERROR: container '$CONTAINER' is not running" >&2
    exit 1
fi

echo "[restore-db] restoring $DUMP → $CONTAINER:$DB_NAME"

# Stream the dump file into pg_restore over stdin.
docker exec -i "$CONTAINER" pg_restore \
    --clean \
    --if-exists \
    --no-acl \
    --no-owner \
    -U "$DB_USER" \
    -d "$DB_NAME" < "$DUMP"

echo "[restore-db] done. Start backend:"
echo "  docker compose -f docker/docker-compose.prod.yml start backend"
