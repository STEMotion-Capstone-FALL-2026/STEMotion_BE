-- Production composition (proposal, Production Workspace; Conceptual ERD:
-- Producer edits Composition, ScriptVersion mapped to Composition, Composition
-- contains Scene, Composition triggers RenderJob): the producer opens
-- an approved script as an auto-generated draft and edits each scene's
-- properties there. The approved script version itself stays frozen.
--
-- One composition per approved script version. Every save increments
-- revision, and a render job keeps the exact props it was sent, so a render
-- is reproducible per composition version (proposal, reliability).
--
-- The Flyway baseline uses varchar(36) keys; the shared Supabase baseline uses
-- uuid. Foreign keys take the type of the column they reference.
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
        project_id %s NOT NULL REFERENCES clip_projects(id) ON DELETE CASCADE,
        version_id %s NOT NULL UNIQUE REFERENCES script_versions(id) ON DELETE CASCADE,
        revision integer NOT NULL CHECK (revision >= 1),
        scenes jsonb NOT NULL CHECK (jsonb_typeof(scenes) = ''array''),
        updated_by %s REFERENCES users(id),
        created_at timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
        updated_at timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP
    )', project_id_type, version_id_type, user_id_type);
END $$;

CREATE INDEX IF NOT EXISTS idx_compositions_project ON compositions (project_id);

-- Additive only: existing jobs keep NULL (rendered straight from the script).
ALTER TABLE render_jobs ADD COLUMN IF NOT EXISTS composition_id uuid
    REFERENCES compositions(id) ON DELETE SET NULL;
CREATE INDEX IF NOT EXISTS idx_render_jobs_composition ON render_jobs (composition_id);
ALTER TABLE render_jobs ADD COLUMN IF NOT EXISTS composition_revision integer;
ALTER TABLE render_jobs ADD COLUMN IF NOT EXISTS input_props jsonb;
ALTER TABLE render_jobs DROP CONSTRAINT IF EXISTS render_jobs_composition_revision_check;
ALTER TABLE render_jobs ADD CONSTRAINT render_jobs_composition_revision_check
    CHECK (composition_revision IS NULL OR composition_revision >= 1);
