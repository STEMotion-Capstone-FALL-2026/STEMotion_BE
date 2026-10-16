-- Rollback of V19 on the shared Supabase baseline, where compositions and
-- render_jobs.composition_id existed before V19: only what V19 added is dropped.
-- Manual rollback only after reviewing backup and application compatibility.
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM compositions WHERE revision > 1) THEN
        RAISE EXCEPTION 'Export compositions (producer edits) before rollback; refusing data loss';
    END IF;
END $$;
ALTER TABLE render_jobs DROP CONSTRAINT IF EXISTS render_jobs_composition_revision_check;
ALTER TABLE render_jobs DROP COLUMN IF EXISTS input_props;
ALTER TABLE render_jobs DROP COLUMN IF EXISTS composition_revision;
DROP INDEX IF EXISTS idx_render_jobs_composition;
DROP INDEX IF EXISTS idx_compositions_project;
ALTER TABLE compositions DROP CONSTRAINT IF EXISTS compositions_scenes_check;
ALTER TABLE compositions DROP CONSTRAINT IF EXISTS compositions_revision_check;
ALTER TABLE compositions DROP COLUMN IF EXISTS scenes;
ALTER TABLE compositions DROP COLUMN IF EXISTS revision;
ALTER TABLE compositions DROP COLUMN IF EXISTS project_id;
ALTER TABLE compositions ALTER COLUMN script_version_id DROP NOT NULL;
