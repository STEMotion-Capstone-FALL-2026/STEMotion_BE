-- Existing Supabase table: never DROP ai_requests. Refuse to discard new usage.
BEGIN;
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM ai_requests WHERE call_status <> 'UNKNOWN') THEN
        RAISE EXCEPTION 'AI tracking rows exist; rollback would lose usage/failure data';
    END IF;
    IF EXISTS (SELECT 1 FROM ai_requests WHERE num_nonnulls(script_version_id,scene_id,clip_id) <> 1) THEN
        RAISE EXCEPTION 'Legacy exactly-one target constraint cannot be restored';
    END IF;
END $$;
DROP INDEX idx_ai_request_requester_created;
ALTER TABLE ai_requests DROP CONSTRAINT ai_request_cost_currency,
    DROP CONSTRAINT ai_request_nonnegative_usage, DROP CONSTRAINT ai_request_call_status,
    DROP CONSTRAINT ai_request_single_target;
ALTER TABLE ai_requests ADD CONSTRAINT chk_ai_target CHECK (
    num_nonnulls(script_version_id,scene_id,clip_id) = 1);
ALTER TABLE ai_requests ALTER COLUMN cost_tokens SET DEFAULT 0;
ALTER TABLE ai_requests ALTER COLUMN latency_ms SET DEFAULT 0;
ALTER TABLE ai_requests DROP COLUMN updated_at, DROP COLUMN request_data,
    DROP COLUMN call_status, DROP COLUMN input_tokens, DROP COLUMN output_tokens,
    DROP COLUMN monetary_cost, DROP COLUMN cost_currency;
COMMIT;
