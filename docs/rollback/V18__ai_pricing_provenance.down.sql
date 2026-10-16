-- Manual rollback only after reviewing backup and application compatibility.
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM ai_requests WHERE pricing_version IS NOT NULL
        OR pricing_input_per_million IS NOT NULL OR pricing_output_per_million IS NOT NULL) THEN
        RAISE EXCEPTION 'Export/preserve pricing provenance before rollback; refusing metadata loss';
    END IF;
END $$;
DROP INDEX idx_ai_usage_created_model_user;
ALTER TABLE ai_requests DROP CONSTRAINT ai_request_pricing_provenance;
ALTER TABLE ai_requests DROP COLUMN pricing_version;
ALTER TABLE ai_requests DROP COLUMN pricing_input_per_million;
ALTER TABLE ai_requests DROP COLUMN pricing_output_per_million;
