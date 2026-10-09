ALTER TABLE clip_projects DROP CONSTRAINT IF EXISTS clip_projects_subject_check;
ALTER TABLE clip_projects ADD CONSTRAINT clip_projects_subject_check CHECK (
    subject::text IN ('Math', 'Physics', 'Chemistry', 'Biology', 'CS', 'ComputerScience')
);
-- The original Flyway schema uses varchar with a CHECK; Supabase uses an enum.
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_status_check;
ALTER TABLE users ADD CONSTRAINT users_status_check CHECK (status::text IN ('ACTIVE', 'INACTIVE', 'BANNED'));
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM pg_type t JOIN pg_namespace n ON n.oid=t.typnamespace
               WHERE n.nspname=current_schema() AND t.typname='user_status') THEN
        EXECUTE format('ALTER TYPE %I.user_status ADD VALUE IF NOT EXISTS ''BANNED''', current_schema());
    END IF;
END $$;
