#!/usr/bin/env bash
# rehearse.sh – Local rehearsal of V23–V26 bridge migrations on simulated production schema.
# Requires: Docker CLI, postgres:17 image, psql CLI.
# Usage: bash deploy/stem-bridge-rehearsal/rehearse.sh
# WARNING: Local simulation only. Does NOT replace R3 on production schema-only dump.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
BRIDGE_DIR="${REPO_ROOT}/src/main/resources/db/bridge-prod"

CONTAINER_NAME="stem_rehearsal_$(date +%s)_$$"
PORT=15433
PGPASSWORD_VAL="rehearsal_only_local_$(date +%s)"
PSQL_CMD="psql -h 127.0.0.1 -p ${PORT} -U postgres -d postgres"

PASS_COUNT=0
FAIL_COUNT=0

cleanup() {
    echo ""
    echo "--- Cleaning up container ---"
    docker rm -f "${CONTAINER_NAME}" >/dev/null 2>&1 || true
}
trap cleanup EXIT

echo "=================================================="
echo " STEM Bridge Rehearsal (V23–V26) - Local Prod Sim"
echo "=================================================="
echo "Container: ${CONTAINER_NAME}"
echo "Port     : ${PORT}"
echo "Bridge   : ${BRIDGE_DIR}"
echo ""

# 1. Start clean postgres:17 container
echo "--> Starting postgres:17 container..."
docker run -d \
    --name "${CONTAINER_NAME}" \
    -p "${PORT}:5432" \
    -e POSTGRES_PASSWORD="${PGPASSWORD_VAL}" \
    -e POSTGRES_DB=postgres \
    postgres:17 >/dev/null

export PGPASSWORD="${PGPASSWORD_VAL}"

# 2. Wait for postgres to be ready
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

# 3. Load simulated production schema
echo "--> Loading prod-sim-schema.sql..."
${PSQL_CMD} -v ON_ERROR_STOP=1 -f "${SCRIPT_DIR}/prod-sim-schema.sql" >/dev/null
echo "prod-sim-schema.sql loaded successfully."

# 4. Apply bridge migrations V23–V26 in order
echo "--> Applying V23–V26 bridge migrations..."
for sql_file in \
    "${BRIDGE_DIR}/V23__assert_stem_bridge_prerequisites.sql" \
    "${BRIDGE_DIR}/V24__create_stem_domain.sql" \
    "${BRIDGE_DIR}/V25__create_stem_artifact_lineage.sql" \
    "${BRIDGE_DIR}/V26__allow_stem_strict_mode.sql"; do
    filename="$(basename "${sql_file}")"
    echo "  Applying ${filename}..."
    ${PSQL_CMD} --single-transaction -v ON_ERROR_STOP=1 -f "${sql_file}" >/dev/null
    echo "  Applied ${filename} OK."
done

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
TEST_WS_ID=$(${PSQL_CMD} -t -A -c "INSERT INTO public.workspaces (name) VALUES ('rehearsal_test_ws') RETURNING id;")
if ${PSQL_CMD} -v ON_ERROR_STOP=1 -c "INSERT INTO public.stem_settings (workspace_id, revision, mode, policy_ids) VALUES ('${TEST_WS_ID}', 1, 'STRICT', '[]'::jsonb);" >/dev/null 2>&1; then
    echo "  [PASS] stem_settings.mode accepts STRICT"
    PASS_COUNT=$((PASS_COUNT + 1))
else
    echo "  [FAIL] stem_settings.mode rejected STRICT"
    FAIL_COUNT=$((FAIL_COUNT + 1))
fi

# Check 4: CHECK stem_settings.mode rejects invalid value
TEST_WS_ID2=$(${PSQL_CMD} -t -A -c "INSERT INTO public.workspaces (name) VALUES ('rehearsal_test_ws_2') RETURNING id;")
if ${PSQL_CMD} -v ON_ERROR_STOP=1 -c "INSERT INTO public.stem_settings (workspace_id, revision, mode, policy_ids) VALUES ('${TEST_WS_ID2}', 1, 'INVALID_MODE', '[]'::jsonb);" >/dev/null 2>&1; then
    echo "  [FAIL] stem_settings.mode accepted INVALID_MODE (expected rejection)"
    FAIL_COUNT=$((FAIL_COUNT + 1))
else
    echo "  [PASS] stem_settings.mode rejects invalid value"
    PASS_COUNT=$((PASS_COUNT + 1))
fi

# Check 5: Re-running V23 fails with transaction rollback (idempotency check)
echo "--> Testing re-run failure / transaction rollback..."
if ${PSQL_CMD} --single-transaction -v ON_ERROR_STOP=1 -f "${BRIDGE_DIR}/V23__assert_stem_bridge_prerequisites.sql" >/dev/null 2>&1; then
    echo "  [FAIL] Re-running V23 succeeded when it should have failed"
    FAIL_COUNT=$((FAIL_COUNT + 1))
else
    echo "  [PASS] Re-running V23 failed as expected and rolled back"
    PASS_COUNT=$((PASS_COUNT + 1))
fi

echo ""
echo "=================================================="
echo " Rehearsal Result: ${PASS_COUNT} PASS, ${FAIL_COUNT} FAIL"
echo "=================================================="

if [ "${FAIL_COUNT}" -eq 0 ]; then
    echo "RESULT: ALL PASS"
    exit 0
else
    echo "RESULT: FAILURES DETECTED"
    exit 1
fi
