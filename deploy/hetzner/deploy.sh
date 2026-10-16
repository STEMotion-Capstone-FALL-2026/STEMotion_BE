#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/../.."
env_file=${HETZNER_ENV_FILE:-deploy/hetzner/.env}
[[ -f $env_file ]] || { echo 'Copy deploy/hetzner/.env.example to .env and fill it privately' >&2; exit 1; }
dc=(docker compose --env-file "$env_file" -f docker-compose.hetzner.yml)
case ${1:-verify} in
  up)
    if [[ ${2:-} == --worker-image-only ]]; then export HETZNER_WORKER_IMAGE_ONLY=1; fi
    "${dc[@]}" config --format json | python3 deploy/hetzner/preflight.py
    # A private rollback snapshot captures running image IDs before tags/builds change.
    if [[ -n $("${dc[@]}" ps -q) ]]; then
      stamp=$(date -u +%Y%m%dT%H%M%SZ)
      backup="deploy/hetzner/releases/$stamp"
      umask 077; mkdir -p "$backup"
      cp "$env_file" "$backup/.env"
      cp docker-compose.hetzner.yml "$backup/compose.yml"
      "${dc[@]}" config --format json | python3 deploy/hetzner/snapshot_images.py "$env_file" > "$backup/images.json"
      cp -a deploy/hetzner/Caddyfile deploy/hetzner/caddy deploy/hetzner/redis-auth.cjs deploy/hetzner/redis-start.sh "$backup/"
      echo "Rollback snapshot: $backup (private; retain prior source bundle too)"
    fi
    # Avoid simultaneous npm/Python builds on 4 GB RAM.
    COMPOSE_PARALLEL_LIMIT=1 "${dc[@]}" build ai-service
    if [[ ${HETZNER_WORKER_IMAGE_ONLY:-0} == 1 ]]; then
      "${dc[@]}" config --format json | python3 -c 'import json,subprocess,sys; subprocess.run(["docker","image","inspect",json.load(sys.stdin)["services"]["worker"]["image"]],stdout=subprocess.DEVNULL,check=True)'
    else
      COMPOSE_PARALLEL_LIMIT=1 "${dc[@]}" build worker
    fi
    "${dc[@]}" up -d --no-build --wait --wait-timeout 180
    bash "$0" verify
    ;;
  verify)
    "${dc[@]}" ps
    # Expand REDIS_PASSWORD inside the container, never in the host shell/argv.
    # shellcheck disable=SC2016
    "${dc[@]}" exec -T redis sh -ec 'REDISCLI_AUTH="$REDIS_PASSWORD" redis-cli ping'
    "${dc[@]}" exec -T ai-service python -c "import urllib.request; print(urllib.request.urlopen('http://127.0.0.1:8000/health').status)"
    "${dc[@]}" exec -T worker node -e "fetch('http://127.0.0.1:4000/health').then(r=>r.json()).then(j=>{console.log(j.status,j.queue);if(j.queue!=='bullmq')process.exit(1)})"
    "${dc[@]}" exec -T caddy wget -q -O - http://127.0.0.1:8081/health
    echo; echo 'Liveness passed. Check /ready separately; provider validity and real heavy workloads are not certified.'
    ;;
  *) echo 'Usage: bash deploy/hetzner/deploy.sh up|verify (HETZNER_ENV_FILE optional)' >&2; exit 2;;
esac
