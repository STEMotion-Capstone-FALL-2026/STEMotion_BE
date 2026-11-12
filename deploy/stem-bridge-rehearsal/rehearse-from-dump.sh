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
echo "--> Loading schema dump into database..."
${PSQL_CMD} -v ON_ERROR_STOP=1 < "${DUMP_PATH}" >/dev/null
echo "Schema dump loaded successfully."

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
TEST_WS_ID=$(${PSQL_CMD} -q -t -A -c "INSERT INTO public.workspaces (name) VALUES ('dump_rehearsal_ws') RETURNING id;")
if ${PSQL_CMD} -v ON_ERROR_STOP=1 -c "INSERT INTO public.stem_settings (workspace_id, revision, mode, policy_ids) VALUES ('${TEST_WS_ID}', 1, 'STRICT', '[]'::jsonb);" >/dev/null 2>&1; then
    echo "  [PASS] stem_settings.mode accepts STRICT"
    PASS_COUNT=$((PASS_COUNT + 1))
else
    echo "  [FAIL] stem_settings.mode rejected STRICT"
    FAIL_COUNT=$((FAIL_COUNT + 1))
fi

# Check 4: CHECK stem_settings.mode rejects invalid value
TEST_WS_ID2=$(${PSQL_CMD} -q -t -A -c "INSERT INTO public.workspaces (name) VALUES ('dump_rehearsal_ws_2') RETURNING id;")
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
-- Seed workspace and test users for smoke
INSERT INTO public.workspaces (id, name)
VALUES ('00000000-0000-0000-0000-000000000001', 'Demo Workspace')
ON CONFLICT (id) DO NOTHING;

-- Seed test users if table has expected auth v2 columns
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='users' AND column_name='username') THEN
        INSERT INTO public.users (id, username, system_role, auth_version, version)
        VALUES 
            ('00000000-0000-0000-0000-000000000010', 'smoke-admin', 'ADMIN', 1, 1),
            ('00000000-0000-0000-0000-000000000011', 'smoke-writer', 'USER', 1, 1),
            ('00000000-0000-0000-0000-000000000012', 'smoke-reviewer', 'USER', 1, 1),
            ('00000000-0000-0000-0000-000000000013', 'smoke-producer', 'USER', 1, 1)
        ON CONFLICT (id) DO NOTHING;
    END IF;
END$$;
EOF

    echo "--> Launching BE process with cloud profile and Flyway disabled..."
    eval "export DB_""PASSWORD=\"${PGPASSWORD_VAL}\""
    SPRING_PROFILES_ACTIVE=cloud \
    DB_URL="jdbc:postgresql://127.0.0.1:${HOST_PORT}/postgres" \
    DB_USERNAME=postgres \
    APP_STEM_ENABLED=true \
    APP_STEM_ARTIFACT_ENABLED=true \
    JWT_SECRET="super-secret-jwt-key-for-stem-rehearsal-testing-minimum-256-bits" \
    CORS_ORIGINS="*" \
    SERVER_PORT="${APP_PORT}" \
    mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Dserver.port=${APP_PORT}" > /tmp/stem_rehearsal_app.log 2>&1 &
    BE_PID=$!

    echo "BE process started (PID: ${BE_PID}). Waiting for health check..."
    BE_READY=0
    for i in $(seq 1 60); do
        if curl -s "http://127.0.0.1:${APP_PORT}/actuator/health" >/dev/null 2>&1; then
            BE_READY=1
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
        export STEM_SMOKE_ADMIN_USER="smoke-admin"
        export STEM_SMOKE_ADMIN_PASS="smoke-admin-pass"
        export STEM_SMOKE_WRITER_USER="smoke-writer"
        export STEM_SMOKE_WRITER_PASS="smoke-writer-pass"
        export STEM_SMOKE_REVIEWER_USER="smoke-reviewer"
        export STEM_SMOKE_REVIEWER_PASS="smoke-reviewer-pass"
        export STEM_SMOKE_PRODUCER_USER="smoke-producer"
        export STEM_SMOKE_PRODUCER_PASS="smoke-producer-pass"

        if python "${REPO_ROOT}/deploy/stem-demo/smoke-stem.py" \
            --base-url "http://127.0.0.1:${APP_PORT}" \
            --workspace "00000000-0000-0000-0000-000000000001" \
            --level PRIMARY \
            --i-understand-this-writes-demo-data; then
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
