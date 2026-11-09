#!/usr/bin/env bash
# Daily database dump, keeping the last 7. Dumps stay on this server, so they guard against
# mistakes and bad migrations, not against losing the server itself.
set -euo pipefail

BACKUP_DIR=/opt/backups/stepfree
mkdir -p "$BACKUP_DIR"
chmod 700 "$BACKUP_DIR"

cd "$(dirname "$0")"
docker compose exec -T postgres pg_dump -U stepfree stepfree \
  | gzip > "$BACKUP_DIR/stepfree-$(date +%F).sql.gz"

find "$BACKUP_DIR" -name 'stepfree-*.sql.gz' -mtime +7 -delete
