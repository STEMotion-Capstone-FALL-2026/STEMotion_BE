-- ONLY for a baseline without ai_requests before V12. Use shared.down.sql on
-- Supabase. Export tracking rows first; do not discard real AI calls.
BEGIN;
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM ai_requests) THEN
        RAISE EXCEPTION 'AI tracking rows exist; retain/export data before rollback';
    END IF;
END $$;
DROP TABLE ai_requests;
COMMIT;
