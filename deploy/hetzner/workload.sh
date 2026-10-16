#!/usr/bin/env bash
# Operational serial-workload switch. It never claims automatic cross-service locking.
set -Eeuo pipefail
cd "$(dirname "$0")/../.."
dc=(docker compose --env-file "${HETZNER_ENV_FILE:-deploy/hetzner/.env}" -f docker-compose.hetzner.yml)
[[ ${2:-} == --quiesced ]] || { echo 'Quiesce BE input first, then add --quiesced' >&2; exit 1; }
case ${1:-} in
  ai)
    # Expansion belongs to the container shell, not the host.
    # shellcheck disable=SC2016
    active=$("${dc[@]}" exec -T redis sh -ec 'REDISCLI_AUTH="$REDIS_PASSWORD" redis-cli LLEN bull:stemotion-render:active')
    [[ $active == 0 ]] || { echo 'Render jobs active; wait for completion before switching' >&2; exit 1; }
    # shellcheck disable=SC2016
    waiting=$("${dc[@]}" exec -T redis sh -ec 'REDISCLI_AUTH="$REDIS_PASSWORD" redis-cli LLEN bull:stemotion-render:wait')
    [[ $waiting == 0 ]] || { echo 'Render queue not empty; drain before switching' >&2; exit 1; }
    # Stop the consumer first and wait for graceful shutdown before enabling AI.
    "${dc[@]}" stop worker
    "${dc[@]}" up -d --no-deps ai-service
    ;;
  render)
    "${dc[@]}" stop ai-service
    "${dc[@]}" up -d --no-deps worker
    ;;
  *) echo 'Usage: workload.sh ai|render; quiesce incoming jobs/requests first' >&2; exit 2;;
esac
echo 'Other service intentionally unavailable (proxy returns 502/503). Resume input only for the selected workload.'
