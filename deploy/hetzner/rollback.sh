#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/../.."
snapshot=$(realpath "${1:?Usage: rollback.sh deploy/hetzner/releases/TIMESTAMP}")
case $snapshot in "$PWD/deploy/hetzner/releases/"*) ;; *) echo 'Snapshot must be inside this release directory' >&2; exit 1;; esac
for file in .env images.json compose.yml Caddyfile redis-auth.cjs redis-start.sh; do
  [[ -f $snapshot/$file ]] || { echo 'Incomplete rollback snapshot' >&2; exit 1; }
done
dc=(docker compose --project-directory "$PWD" --env-file "$snapshot/.env" -f "$snapshot/compose.yml" -f "$snapshot/images.json")
echo 'Quiesce BE requests and wait for active render/AI work to finish before rollback.'
[[ ${2:-} == --quiesced ]] || { echo 'Retry with --quiesced only after stopping incoming work' >&2; exit 1; }
"${dc[@]}" down # Deliberately no -v: keep queue, videos and certificates.
cp "$snapshot/Caddyfile" deploy/hetzner/Caddyfile
cp "$snapshot/redis-auth.cjs" deploy/hetzner/redis-auth.cjs
cp "$snapshot/redis-start.sh" deploy/hetzner/redis-start.sh
cp -a "$snapshot/caddy/." deploy/hetzner/caddy/
"${dc[@]}" up -d --no-build --wait --wait-timeout 180
echo 'Previous image IDs/config restored. No database migration or persistent-data rollback was run.'
