-- Additive only. Existing model/input_tokens/output_tokens/monetary_cost columns are reused.
-- Do not rewrite V12 or reprice old requests. Production apply requires separate approval/backup.
ALTER TABLE ai_requests ADD COLUMN pricing_version VARCHAR(128);
ALTER TABLE ai_requests ADD COLUMN pricing_input_per_million NUMERIC(20,10);
ALTER TABLE ai_requests ADD COLUMN pricing_output_per_million NUMERIC(20,10);
ALTER TABLE ai_requests ADD CONSTRAINT ai_request_pricing_provenance CHECK (
    (pricing_version IS NULL AND pricing_input_per_million IS NULL AND pricing_output_per_million IS NULL)
    OR (pricing_version IS NOT NULL AND pricing_input_per_million >= 0 AND pricing_output_per_million >= 0
        AND pricing_input_per_million IS NOT NULL AND pricing_output_per_million IS NOT NULL));
CREATE INDEX idx_ai_usage_created_model_user ON ai_requests(created_at, model, requester_id);
