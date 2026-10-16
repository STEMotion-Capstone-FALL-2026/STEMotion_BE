-- Production composition (proposal, Production Workspace; Conceptual ERD:
-- Producer edits Composition, ScriptVersion mapped to Composition, Composition
-- contains Scene, Composition triggers RenderJob): the producer opens an
-- approved script as an auto-generated draft and edits each scene's properties
-- there. The approved script version itself stays frozen.
--
-- One composition per approved script version. Every save increments
-- revision, and a render job keeps the exact props it was sent, so a render is
-- reproducible per composition version (proposal, reliability).
--
-- The shared Supabase baseline already has compositions (script_version_id,
-- producer_id, resolution, fps) and render_jobs.composition_id; they are kept
-- and extended. The Flyway baseline (varchar(36) keys) gets the same table.
DO $$
DECLARE project_id_type text; version_id_type text; user_id_type text;
BEGIN
    SELECT format_type(atttypid, atttypmod) INTO project_id_type FROM pg_attribute
        WHERE attrelid = 'clip_projects'::regclass AND attname = 'id' AND NOT attisdropped;
    SELECT format_type(atttypid, atttypmod) INTO version_id_type FROM pg_attribute
        WHERE attrelid = 'script_versions'::regclass AND attname = 'id' AND NOT attisdropped;
    SELECT format_type(atttypid, atttypmod) INTO user_id_type FROM pg_attribute
        WHERE attrelid = 'users'::regclass AND attname = 'id' AND NOT attisdropped;
    EXECUTE format('CREATE TABLE IF NOT EXISTS compositions (
        id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
        script_version_id %s UNIQUE REFERENCES script_versions(id) ON DELETE CASCADE,
        producer_id %s REFERENCES users(id) ON DELETE SET NULL,
        resolution varchar(50) DEFAULT ''1080p'',
        fps integer DEFAULT 30,
        created_at timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
        updated_at timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP
    )', version_id_type, user_id_type);
    EXECUTE format('ALTER TABLE compositions ADD COLUMN IF NOT EXISTS project_id %s
        REFERENCES clip_projects(id) ON DELETE CASCADE', project_id_type);
END $$;

ALTER TABLE compositions ADD COLUMN IF NOT EXISTS revision integer;
ALTER TABLE compositions ADD COLUMN IF NOT EXISTS scenes jsonb;

-- Rows from before this migration (shared baseline): the project comes from the
-- script version, the scenes from that version's scene rows, as revision 1.
UPDATE compositions c SET project_id = v.project_id
    FROM script_versions v WHERE v.id = c.script_version_id AND c.project_id IS NULL;
UPDATE compositions c SET scenes = coalesce((
        SELECT jsonb_agg(coalesce(s.props, '{}'::jsonb) || jsonb_build_object(
                   'id', s.id::text, 'type', s.scene_type::text, 'title', s.title,
                   'narration', s.narration, 'durationInFrames', s.duration_in_frames)
               ORDER BY s.order_no)
        FROM scenes s WHERE s.version_id = c.script_version_id), '[]'::jsonb)
    WHERE c.scenes IS NULL;
UPDATE compositions SET revision = 1 WHERE revision IS NULL;

DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM compositions WHERE script_version_id IS NULL OR project_id IS NULL) THEN
        RAISE EXCEPTION 'compositions rows without a script version or project: link or remove them, then rerun V19';
    END IF;
END $$;

ALTER TABLE compositions ALTER COLUMN script_version_id SET NOT NULL;
ALTER TABLE compositions ALTER COLUMN project_id SET NOT NULL;
ALTER TABLE compositions ALTER COLUMN revision SET NOT NULL;
ALTER TABLE compositions ALTER COLUMN scenes SET NOT NULL;
ALTER TABLE compositions DROP CONSTRAINT IF EXISTS compositions_revision_check;
ALTER TABLE compositions ADD CONSTRAINT compositions_revision_check CHECK (revision >= 1);
ALTER TABLE compositions DROP CONSTRAINT IF EXISTS compositions_scenes_check;
ALTER TABLE compositions ADD CONSTRAINT compositions_scenes_check CHECK (jsonb_typeof(scenes) = 'array');
CREATE INDEX IF NOT EXISTS idx_compositions_project ON compositions (project_id);

-- Additive only: existing jobs keep NULL (rendered straight from the script).
-- The shared baseline already has composition_id; it is kept as it is.
ALTER TABLE render_jobs ADD COLUMN IF NOT EXISTS composition_id uuid
    REFERENCES compositions(id) ON DELETE SET NULL;
CREATE INDEX IF NOT EXISTS idx_render_jobs_composition ON render_jobs (composition_id);
ALTER TABLE render_jobs ADD COLUMN IF NOT EXISTS composition_revision integer;
ALTER TABLE render_jobs ADD COLUMN IF NOT EXISTS input_props jsonb;
ALTER TABLE render_jobs DROP CONSTRAINT IF EXISTS render_jobs_composition_revision_check;
ALTER TABLE render_jobs ADD CONSTRAINT render_jobs_composition_revision_check
    CHECK (composition_revision IS NULL OR composition_revision >= 1);
