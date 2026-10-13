-- Existing Supabase publications table: retain original Clip-based rows.
BEGIN;
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM publications WHERE clip_id IS NULL) THEN
        RAISE EXCEPTION 'Project-only publications cannot lose project binding';
    END IF;
END $$;
ALTER TABLE publications DROP CONSTRAINT publications_project_platform_key;
ALTER TABLE publications DROP COLUMN project_id, DROP COLUMN created_at, DROP COLUMN updated_at;
COMMIT;
-- Legacy publish_records are deliberately unchanged.
