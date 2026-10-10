DO $$
DECLARE project_type text; user_type text;
BEGIN
    SELECT format_type(atttypid,atttypmod) INTO project_type FROM pg_attribute
        WHERE attrelid='clip_projects'::regclass AND attname='id';
    SELECT format_type(atttypid,atttypmod) INTO user_type FROM pg_attribute
        WHERE attrelid='users'::regclass AND attname='id';
    EXECUTE format('CREATE TABLE IF NOT EXISTS publications (
        id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
        clip_id uuid REFERENCES clips(id), publisher_id %s REFERENCES users(id),
        platform varchar(32) NOT NULL CHECK (platform IN (''YOUTUBE'',''INTERNAL_LIBRARY'')),
        metadata jsonb, published_at timestamptz DEFAULT now()
    )',user_type);
    EXECUTE format('ALTER TABLE publications ADD COLUMN project_id %s REFERENCES clip_projects(id)',project_type);
END $$;
ALTER TABLE publications ADD COLUMN created_at timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE publications ADD COLUMN updated_at timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP;
UPDATE publications p SET project_id=r.project_id
    FROM clips c JOIN render_jobs r ON r.id=c.render_job_id WHERE p.clip_id=c.id;
-- No guess for an orphan publication, and no duplicate cleanup by arbitrary deletion.
ALTER TABLE publications ALTER COLUMN project_id SET NOT NULL;
ALTER TABLE publications ADD CONSTRAINT publications_project_platform_key UNIQUE(project_id,platform);
-- Keep publish_records for the API adapter. No automatic YouTube backfill:
-- a legacy record or URL is not proof of a real uploaded/approved publication.
