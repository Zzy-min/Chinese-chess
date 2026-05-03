#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
BACKUP_DIR="${BACKUP_DIR:-${ROOT_DIR}/backups}"
KEEP_COUNT="${KEEP_COUNT:-7}"

cd "${ROOT_DIR}"

if [[ ! -f compose.yaml ]]; then
  echo "compose.yaml not found in ${ROOT_DIR}" >&2
  exit 1
fi

mkdir -p "${BACKUP_DIR}"

timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
outfile="${BACKUP_DIR}/postgres-${timestamp}.sql.gz"

docker compose exec -T db sh -lc 'pg_dump -U "$POSTGRES_USER" "$POSTGRES_DB"' | gzip -c > "${outfile}"

if [[ ! -s "${outfile}" ]]; then
  echo "backup file is empty: ${outfile}" >&2
  exit 1
fi

mapfile -t backups < <(find "${BACKUP_DIR}" -maxdepth 1 -type f -name 'postgres-*.sql.gz' | sort)
if (( ${#backups[@]} > KEEP_COUNT )); then
  remove_count=$(( ${#backups[@]} - KEEP_COUNT ))
  for old_file in "${backups[@]:0:${remove_count}}"; do
    rm -f "${old_file}"
  done
fi

echo "Backup created: ${outfile}"
