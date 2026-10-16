-- Manual rollback only after reviewing backup and application compatibility.
-- Producer edits live only in compositions; refuse to drop them silently.
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM compositions) THEN
        RAISE EXCEPTION 'Export compositions (producer edits) before rollback; refusing data loss';
    END IF;
END $$;
ALTER TABLE render_jobs DROP CONSTRAINT IF EXISTS render_jobs_composition_revision_check;
ALTER TABLE render_jobs DROP COLUMN IF EXISTS input_props;
ALTER TABLE render_jobs DROP COLUMN IF EXISTS composition_revision;
DROP INDEX IF EXISTS idx_render_jobs_composition;
ALTER TABLE render_jobs DROP COLUMN IF EXISTS composition_id;
DROP TABLE compositions;
