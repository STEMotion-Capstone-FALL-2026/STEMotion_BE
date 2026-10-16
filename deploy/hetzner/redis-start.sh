#!/bin/sh
set -eu
# Safe config token only; use openssl rand -hex 32, never arbitrary config text.
case "${REDIS_PASSWORD:-}" in ''|*[!A-Za-z0-9_-]*) echo 'Invalid/missing REDIS_PASSWORD (use hex/base64url)' >&2; exit 1;; esac
umask 077
printf 'bind 0.0.0.0\nprotected-mode yes\nport 6379\ndir /data\nappendonly yes\nappendfsync everysec\nmaxmemory 64mb\nmaxmemory-policy noeviction\nrequirepass %s\n' "$REDIS_PASSWORD" > /tmp/stemotion-redis.conf
chown redis:redis /tmp/stemotion-redis.conf
exec /usr/local/bin/docker-entrypoint.sh redis-server /tmp/stemotion-redis.conf
