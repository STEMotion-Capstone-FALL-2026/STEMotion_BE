DO $$
DECLARE user_type text; version_type text; scene_type text;
BEGIN
    SELECT format_type(atttypid,atttypmod) INTO user_type FROM pg_attribute
        WHERE attrelid='users'::regclass AND attname='id';
    SELECT format_type(atttypid,atttypmod) INTO version_type FROM pg_attribute
        WHERE attrelid='script_versions'::regclass AND attname='id';
    SELECT format_type(atttypid,atttypmod) INTO scene_type FROM pg_attribute
        WHERE attrelid='scenes'::regclass AND attname='id';
    EXECUTE format('CREATE TABLE IF NOT EXISTS ai_requests (
        id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
        requester_id %s REFERENCES users(id),
        request_type varchar(32) NOT NULL,
        script_version_id %s REFERENCES script_versions(id),
        scene_id %s REFERENCES scenes(id), clip_id uuid REFERENCES clips(id),
        model varchar(255) NOT NULL, response_data jsonb,
        decision varchar(16) DEFAULT ''PENDING'', cost_tokens integer,
        latency_ms integer, created_at timestamptz DEFAULT now()
    )',user_type,version_type,scene_type);
END $$;

ALTER TABLE ai_requests ADD COLUMN updated_at timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE ai_requests ADD COLUMN request_data jsonb;
ALTER TABLE ai_requests ADD COLUMN call_status varchar(16);
ALTER TABLE ai_requests ADD COLUMN input_tokens integer;
ALTER TABLE ai_requests ADD COLUMN output_tokens integer;
ALTER TABLE ai_requests ADD COLUMN monetary_cost numeric(20,10);
ALTER TABLE ai_requests ADD COLUMN cost_currency varchar(3);
UPDATE ai_requests SET call_status='UNKNOWN';
ALTER TABLE ai_requests ALTER COLUMN call_status SET DEFAULT 'PENDING';
ALTER TABLE ai_requests ALTER COLUMN call_status SET NOT NULL;
ALTER TABLE ai_requests ALTER COLUMN cost_tokens DROP DEFAULT;
ALTER TABLE ai_requests ALTER COLUMN latency_ms DROP DEFAULT;
-- Generation is called before a draft exists; failures also need a durable row.
-- Optional artifact FKs in the diagram may therefore all be NULL.
ALTER TABLE ai_requests DROP CONSTRAINT IF EXISTS chk_ai_target;
ALTER TABLE ai_requests ADD CONSTRAINT ai_request_single_target CHECK (
    num_nonnulls(script_version_id,scene_id,clip_id) <= 1);
ALTER TABLE ai_requests ADD CONSTRAINT ai_request_call_status CHECK (
    call_status IN ('UNKNOWN','PENDING','SUCCEEDED','FAILED'));
ALTER TABLE ai_requests ADD CONSTRAINT ai_request_nonnegative_usage CHECK (
    (cost_tokens IS NULL OR cost_tokens >= 0) AND
    (input_tokens IS NULL OR input_tokens >= 0) AND
    (output_tokens IS NULL OR output_tokens >= 0) AND
    (latency_ms IS NULL OR latency_ms >= 0) AND
    (monetary_cost IS NULL OR monetary_cost >= 0));
ALTER TABLE ai_requests ADD CONSTRAINT ai_request_cost_currency CHECK (
    (monetary_cost IS NULL AND cost_currency IS NULL) OR
    (monetary_cost IS NOT NULL AND cost_currency IS NOT NULL));
CREATE INDEX idx_ai_request_requester_created ON ai_requests(requester_id,created_at);
