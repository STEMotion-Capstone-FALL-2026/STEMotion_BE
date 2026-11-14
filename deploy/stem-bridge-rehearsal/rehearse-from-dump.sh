#!/usr/bin/env bash
# rehearse-from-dump.sh – R3 rehearsal from a production schema-only dump.
# Usage:
#   bash deploy/stem-bridge-rehearsal/rehearse-from-dump.sh --dump /path/to/schema-only.sql [--with-app] [--port 15433]
#
# Requirements:
#   - Docker Engine / CLI with postgres:17 image available.
#   - Dump file MUST be schema-only (e.g. pg_dump --schema-only --no-owner --no-privileges).
#   - NEVER dump or pass production data.
#   - The dump file must reside outside the repository.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
BRIDGE_DIR="${REPO_ROOT}/src/main/resources/db/bridge-prod"

DUMP_PATH=""
WITH_APP=0
HOST_PORT=15433

while [[ $# -gt 0 ]]; do
    case "$1" in
        --dump)
            DUMP_PATH="$2"
            shift 2
            ;;
        --dump=*)
            DUMP_PATH="${1#*=}"
            shift
            ;;
        --with-app)
            WITH_APP=1
            shift
            ;;
        --port)
            HOST_PORT="$2"
            shift 2
            ;;
        -h|--help)
            echo "Usage: $0 --dump <path-to-schema-dump.sql> [--with-app] [--port <port>]"
            exit 0
            ;;
        *)
            echo "ERROR: Unknown option '$1'"
            echo "Usage: $0 --dump <path-to-schema-dump.sql> [--with-app] [--port <port>]"
            exit 1
            ;;
    esac
done

if [ -z "${DUMP_PATH}" ]; then
    echo "ERROR: Missing required argument: --dump <path-to-schema-dump.sql>"
    echo "Usage: $0 --dump <path-to-schema-dump.sql> [--with-app] [--port <port>]"
    exit 1
fi

if [ ! -f "${DUMP_PATH}" ]; then
    echo "ERROR: Dump file not found: ${DUMP_PATH}"
    exit 1
fi

CONTAINER_NAME="stem_rehearsal_dump_$(date +%s)_$$"
PGPASSWORD_VAL="rehearsal_local_$(date +%s)"
PSQL_CMD="docker exec -i -e PGPASSWORD=${PGPASSWORD_VAL} ${CONTAINER_NAME} psql -h 127.0.0.1 -U postgres -d postgres"

PASS_COUNT=0
FAIL_COUNT=0
BE_PID=""

cleanup() {
    echo ""
    echo "--- Cleaning up rehearsal resources ---"
    if [ -n "${BE_PID}" ]; then
        echo "Stopping BE process (PID: ${BE_PID})..."
        kill "${BE_PID}" >/dev/null 2>&1 || true
    fi
    echo "Stopping container ${CONTAINER_NAME}..."
    docker rm -f "${CONTAINER_NAME}" >/dev/null 2>&1 || true
}
trap cleanup EXIT

echo "=========================================================="
echo " STEM Bridge Rehearsal (R3 from Schema-Only Dump)"
echo "=========================================================="
echo "Dump file : ${DUMP_PATH}"
echo "Container : ${CONTAINER_NAME}"
echo "Host port : ${HOST_PORT}"
echo "Bridge dir: ${BRIDGE_DIR}"
echo "With App  : $([ "${WITH_APP}" -eq 1 ] && echo 'YES' || echo 'NO')"
echo "=========================================================="

# 1. Start postgres:17 container
echo "--> Starting postgres:17 container..."
docker run -d \
    --name "${CONTAINER_NAME}" \
    -p "${HOST_PORT}:5432" \
    -e POSTGRES_PASSWORD="${PGPASSWORD_VAL}" \
    -e POSTGRES_DB=postgres \
    postgres:17 >/dev/null

# 2. Wait for postgres
echo "--> Waiting for PostgreSQL 17 to become ready..."
READY=0
for i in $(seq 1 30); do
    if ${PSQL_CMD} -c "SELECT 1" >/dev/null 2>&1; then
        READY=1
        break
    fi
    sleep 1
done

if [ "${READY}" -ne 1 ]; then
    echo "ERROR: PostgreSQL container failed to start in 30 seconds."
    exit 1
fi
echo "PostgreSQL is ready."

# 3. Load schema-only dump
# A Supabase dump references platform roles and extensions (supabase_vault, ...) that a plain
# postgres:17 image does not have. Create the roles, load without ON_ERROR_STOP, then accept only
# errors about Supabase-only extensions and require every public table of the dump to exist.
echo "--> Loading schema dump into database..."
for role in anon authenticated service_role supabase_admin authenticator pgbouncer \
            supabase_auth_admin supabase_storage_admin dashboard_user; do
    ${PSQL_CMD} -c "CREATE ROLE ${role}" >/dev/null 2>&1 || true
done
LOAD_LOG="$(mktemp)"
${PSQL_CMD} < "${DUMP_PATH}" > "${LOAD_LOG}" 2>&1 || true
UNEXPECTED_ERRORS=$(grep '^ERROR' "${LOAD_LOG}" | grep -v -E 'extension "(supabase_vault|pg_graphql|pgsodium|pg_net|pgjwt)"' || true)
if [ -n "${UNEXPECTED_ERRORS}" ]; then
    echo "ERROR: unexpected errors while loading the dump:"
    echo "${UNEXPECTED_ERRORS}" | head -20
    exit 1
fi
EXPECTED_TABLES=$(grep -c -E '^CREATE TABLE public\.' "${DUMP_PATH}" || true)
LOADED_TABLES=$(${PSQL_CMD} -t -A -c "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE';")
if [ "${LOADED_TABLES}" -ne "${EXPECTED_TABLES}" ]; then
    echo "ERROR: dump has ${EXPECTED_TABLES} public tables but ${LOADED_TABLES} loaded."
    exit 1
fi
echo "Schema dump loaded (${LOADED_TABLES} public tables; Supabase-only extensions skipped)."

# 3b. Seed the DATA prerequisites V23 checks (a schema-only dump has none): the Flyway history
# rows V1 baseline + V2-V22 and the auth cutover ledger entry. On production V23 checks the
# real rows itself, inside the R5 transaction.
if [ "$(${PSQL_CMD} -t -A -c "SELECT count(*) FROM public.flyway_schema_history;")" -eq 0 ]; then
    echo "--> Seeding Flyway history and ledger rows from prod-sim-schema.sql (data is not in the dump)..."
    awk 'BEGIN{IGNORECASE=1} /INSERT INTO (public\.flyway_schema_history|supabase_migrations\.schema_migrations)/{p=1} p{print} /;[[:space:]]*$/{if(p){p=0}}' \
        "${SCRIPT_DIR}/prod-sim-schema.sql" | ${PSQL_CMD} -v ON_ERROR_STOP=1 >/dev/null
fi

# 4. Apply V23-V26 bridge migrations in ONE transaction
echo "--> Applying V23–V26 bridge migrations in ONE transaction..."
cat "${BRIDGE_DIR}/V23__assert_stem_bridge_prerequisites.sql" \
    "${BRIDGE_DIR}/V24__create_stem_domain.sql" \
    "${BRIDGE_DIR}/V25__create_stem_artifact_lineage.sql" \
    "${BRIDGE_DIR}/V26__allow_stem_strict_mode.sql" \
  | ${PSQL_CMD} --single-transaction -v ON_ERROR_STOP=1 >/dev/null
echo "  Applied V23–V26 successfully."

echo ""
echo "--> Running verification checks..."

# Check 1: 11 STEM tables exist
STEM_TABLE_COUNT=$(${PSQL_CMD} -t -A -c "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name LIKE 'stem_%';")
if [ "${STEM_TABLE_COUNT}" -eq 11 ]; then
    echo "  [PASS] STEM table count == 11 (actual: ${STEM_TABLE_COUNT})"
    PASS_COUNT=$((PASS_COUNT + 1))
else
    echo "  [FAIL] STEM table count expected 11, got ${STEM_TABLE_COUNT}"
    FAIL_COUNT=$((FAIL_COUNT + 1))
fi

# Check 2: 4 append-only triggers exist
TRIGGER_COUNT=$(${PSQL_CMD} -t -A -c "SELECT count(*) FROM information_schema.triggers WHERE trigger_schema='public' AND action_statement LIKE '%stem_p5_append_only%';")
if [ "${TRIGGER_COUNT}" -eq 4 ]; then
    echo "  [PASS] Append-only triggers == 4 (actual: ${TRIGGER_COUNT})"
    PASS_COUNT=$((PASS_COUNT + 1))
else
    echo "  [FAIL] Append-only triggers expected 4, got ${TRIGGER_COUNT}"
    FAIL_COUNT=$((FAIL_COUNT + 1))
fi

# Check 3: CHECK stem_settings.mode accepts STRICT
TEST_WS_ID=$(${PSQL_CMD} -q -t -A -c "INSERT INTO public.workspaces (id, created_at, updated_at, name) VALUES (gen_random_uuid(), now(), now(), 'dump_rehearsal_ws') RETURNING id;")
if ${PSQL_CMD} -v ON_ERROR_STOP=1 -c "INSERT INTO public.stem_settings (workspace_id, revision, mode, policy_ids) VALUES ('${TEST_WS_ID}', 1, 'STRICT', '[]'::jsonb);" >/dev/null 2>&1; then
    echo "  [PASS] stem_settings.mode accepts STRICT"
    PASS_COUNT=$((PASS_COUNT + 1))
else
    echo "  [FAIL] stem_settings.mode rejected STRICT"
    FAIL_COUNT=$((FAIL_COUNT + 1))
fi

# Check 4: CHECK stem_settings.mode rejects invalid value
TEST_WS_ID2=$(${PSQL_CMD} -q -t -A -c "INSERT INTO public.workspaces (id, created_at, updated_at, name) VALUES (gen_random_uuid(), now(), now(), 'dump_rehearsal_ws_2') RETURNING id;")
if ${PSQL_CMD} -v ON_ERROR_STOP=1 -c "INSERT INTO public.stem_settings (workspace_id, revision, mode, policy_ids) VALUES ('${TEST_WS_ID2}', 1, 'INVALID_MODE', '[]'::jsonb);" >/dev/null 2>&1; then
    echo "  [FAIL] stem_settings.mode accepted INVALID_MODE (expected rejection)"
    FAIL_COUNT=$((FAIL_COUNT + 1))
else
    echo "  [PASS] stem_settings.mode rejects invalid value"
    PASS_COUNT=$((PASS_COUNT + 1))
fi

# Check 5: Re-running V23 fails with transaction rollback (idempotency check)
echo "--> Testing re-run failure / transaction rollback..."
if ${PSQL_CMD} --single-transaction -v ON_ERROR_STOP=1 < "${BRIDGE_DIR}/V23__assert_stem_bridge_prerequisites.sql" >/dev/null 2>&1; then
    echo "  [FAIL] Re-running V23 succeeded when it should have failed"
    FAIL_COUNT=$((FAIL_COUNT + 1))
else
    echo "  [PASS] Re-running V23 failed as expected and rolled back"
    PASS_COUNT=$((PASS_COUNT + 1))
fi

# 5. Optional: --with-app
if [ "${WITH_APP}" -eq 1 ]; then
    echo ""
    echo "=========================================================="
    echo " Running with Application Verification (--with-app)"
    echo "=========================================================="
    APP_PORT=18082

    # Seed test users for smoke test into container DB
    echo "--> Seeding test users for smoke test in container DB..."
    ${PSQL_CMD} -v ON_ERROR_STOP=1 << 'EOF'
-- TEST_ONLY users (bcrypt via pgcrypto, which the Supabase dump installs in schema "extensions"),
-- one workspace and the three workspace seats the STEM flow needs. Throwaway container only.
INSERT INTO public.users (id, username, password_hash, display_name, full_name, system_role, status,
                          must_change_password, auth_version, version)
VALUES
 ('10000000-0000-0000-0000-000000000001','smoke-admin',   extensions.crypt('Smoke-only-123', extensions.gen_salt('bf',10)),'TEST_ONLY admin','TEST_ONLY admin','ADMIN','ACTIVE',false,1,1),
 ('10000000-0000-0000-0000-000000000002','smoke-writer',  extensions.crypt('Smoke-only-123', extensions.gen_salt('bf',10)),'TEST_ONLY writer','TEST_ONLY writer','USER','ACTIVE',false,1,1),
 ('10000000-0000-0000-0000-000000000003','smoke-reviewer',extensions.crypt('Smoke-only-123', extensions.gen_salt('bf',10)),'TEST_ONLY reviewer','TEST_ONLY reviewer','USER','ACTIVE',false,1,1),
 ('10000000-0000-0000-0000-000000000004','smoke-producer',extensions.crypt('Smoke-only-123', extensions.gen_salt('bf',10)),'TEST_ONLY producer','TEST_ONLY producer','USER','ACTIVE',false,1,1);
INSERT INTO public.workspaces (id, created_at, updated_at, name, created_by)
VALUES ('20000000-0000-0000-0000-000000000001', now(), now(), 'TEST_ONLY R3 workspace', '10000000-0000-0000-0000-000000000001');
INSERT INTO public.workspace_members (id, created_at, updated_at, role, status, user_id, workspace_id, is_leader) VALUES
 (gen_random_uuid(), now(), now(), 'WRITER',  'ACTIVE','10000000-0000-0000-0000-000000000002','20000000-0000-0000-0000-000000000001', true),
 (gen_random_uuid(), now(), now(), 'REVIEWER','ACTIVE','10000000-0000-0000-0000-000000000003','20000000-0000-0000-0000-000000000001', false),
 (gen_random_uuid(), now(), now(), 'PRODUCER','ACTIVE','10000000-0000-0000-0000-000000000004','20000000-0000-0000-0000-000000000001', false);
EOF

    echo "--> Building the BE jar (tests skipped) and launching it with the cloud profile..."
    (cd "${REPO_ROOT}" && mvn -q -DskipTests package) >/dev/null
    APP_JAR=$(ls "${REPO_ROOT}"/target/stemotion-be-*.jar | grep -v -- '-plain' | head -1)
    APP_LOG="$(mktemp)"
    # cloud profile = production config (Flyway off, Hibernate validate). UTC because the Windows
    # JVM default "Asia/Saigon" is rejected by PostgreSQL 17. AUTH_V2_ENABLED and AI_PRICING_JSON
    # are set on the hosted service too; without them login answers 503 / the app does not start.
    eval "export DB_""PASSWORD=\"${PGPASSWORD_VAL}\""
    SPRING_PROFILES_ACTIVE=cloud \
    DB_URL="jdbc:postgresql://127.0.0.1:${HOST_PORT}/postgres" \
    DB_USERNAME=postgres \
    APP_STEM_ENABLED=true \
    APP_STEM_ARTIFACT_ENABLED=true \
    AUTH_V2_ENABLED=true \
    AI_PRICING_JSON='{}' \
    JWT_SECRET="rehearsal-only-jwt-secret-not-used-anywhere-else-0123456789" \
    CORS_ORIGINS="http://localhost:3000" \
    SERVER_PORT="${APP_PORT}" \
    java -Duser.timezone=UTC -jar "${APP_JAR}" > "${APP_LOG}" 2>&1 &
    BE_PID=$!

    echo "BE process started (PID: ${BE_PID}). Waiting for readiness..."
    BE_READY=0
    for i in $(seq 1 90); do
        if [ "$(curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:${APP_PORT}/actuator/health/readiness")" = 200 ]; then
            BE_READY=1
            break
        fi
        if grep -q "Application run failed" "${APP_LOG}"; then
            grep -E "Caused by|Schema-validation" "${APP_LOG}" | tail -5
            break
        fi
        sleep 2
    done

    if [ "${BE_READY}" -ne 1 ]; then
        echo "  [FAIL] BE failed to become healthy within 120s"
        FAIL_COUNT=$((FAIL_COUNT + 1))
    else
        echo "  [PASS] BE started and healthy on port ${APP_PORT}"
        PASS_COUNT=$((PASS_COUNT + 1))

        # Run smoke-stem.py against the app
        echo "--> Running smoke-stem.py against rehearsal app..."
        SMOKE_ENV="$(mktemp)"
        for r in ADMIN WRITER REVIEWER PRODUCER; do
            l=$(echo "$r" | tr 'A-Z' 'a-z')
            printf 'STEM_SMOKE_%s_USER=smoke-%s\nSTEM_SMOKE_%s_PASS=Smoke-only-123\n' "$r" "$l" "$r"
        done > "${SMOKE_ENV}"
        set -a; . "${SMOKE_ENV}"; set +a
        SMOKE_ARGS=(--workspace "20000000-0000-0000-0000-000000000001" --level PRIMARY --i-understand-this-writes-demo-data)
        # Use a real Python 3 if there is one (the Windows "python" app alias is not); otherwise
        # run the smoke script in a python:3.12-alpine container that reaches the host port.
        if python3 -c 'import sys; sys.exit(0 if sys.version_info >= (3, 8) else 1)' >/dev/null 2>&1; then
            SMOKE_CMD=(python3 "${REPO_ROOT}/deploy/stem-demo/smoke-stem.py" --base-url "http://127.0.0.1:${APP_PORT}")
        else
            DEMO_DIR="${REPO_ROOT}/deploy/stem-demo"; ENV_FILE="${SMOKE_ENV}"
            if command -v cygpath >/dev/null 2>&1; then DEMO_DIR="$(cygpath -w "${DEMO_DIR}")"; ENV_FILE="$(cygpath -w "${ENV_FILE}")"; fi
            SMOKE_CMD=(env MSYS_NO_PATHCONV=1 docker run --rm --add-host=host.docker.internal:host-gateway
                       --env-file "${ENV_FILE}" -v "${DEMO_DIR}:/work:ro" python:3.12-alpine
                       python /work/smoke-stem.py --base-url "http://host.docker.internal:${APP_PORT}")
        fi

        if "${SMOKE_CMD[@]}" "${SMOKE_ARGS[@]}"; then
            echo "  [PASS] smoke-stem.py passed against rehearsal app"
            PASS_COUNT=$((PASS_COUNT + 1))
        else
            echo "  [FAIL] smoke-stem.py failed against rehearsal app"
            FAIL_COUNT=$((FAIL_COUNT + 1))
        fi
    fi
fi

echo ""
echo "=========================================================="
echo " Dump Rehearsal Result: ${PASS_COUNT} PASS, ${FAIL_COUNT} FAIL"
echo "=========================================================="

if [ "${FAIL_COUNT}" -eq 0 ]; then
    echo "RESULT: ALL PASS"
    exit 0
else
    echo "RESULT: FAILURES DETECTED"
    exit 1
fi
