-- Completed artifacts are distinct from mutable project/video workflow state.
-- The shared Supabase schema uses UUID keys; the legacy V1 baseline uses varchar.
DO $$
DECLARE job_id_type text;
BEGIN
    SELECT format_type(atttypid, atttypmod) INTO job_id_type
      FROM pg_attribute WHERE attrelid = 'render_jobs'::regclass
      AND attname = 'id' AND NOT attisdropped;
    EXECUTE format('CREATE TABLE IF NOT EXISTS clips (
        id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
        render_job_id %s NOT NULL UNIQUE REFERENCES render_jobs(id),
        video_url text NOT NULL CHECK (length(btrim(video_url)) > 0),
        duration_sec integer CHECK (duration_sec >= 0),
        qa_status varchar(16) NOT NULL DEFAULT ''PENDING''
            CHECK (qa_status IN (''PENDING'', ''PASS'', ''FAIL'')),
        qa_report jsonb,
        created_at timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
        updated_at timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP
    )', job_id_type);
END $$;

-- The diagram-first Supabase baseline already has clips (native qa_result enum).
-- Retain that table, its constraints, IDs and enum; BaseEntity also needs this column.
ALTER TABLE clips ADD COLUMN IF NOT EXISTS updated_at timestamp(6);
UPDATE clips SET updated_at = created_at WHERE updated_at IS NULL;
ALTER TABLE clips ALTER COLUMN updated_at SET DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE clips ALTER COLUMN updated_at SET NOT NULL;
-- The shared ERD schema requires duration_sec, but workers do not yet report a
-- measured duration. Unknown is NULL, never a fabricated zero.
ALTER TABLE clips ALTER COLUMN duration_sec DROP NOT NULL;
ALTER TABLE clips ALTER COLUMN qa_report DROP DEFAULT;

ALTER TABLE clips ADD CONSTRAINT clips_measured_duration_check
    CHECK (duration_sec IS NULL OR duration_sec >= 0);
ALTER TABLE clips ADD CONSTRAINT clips_artifact_url_check
    CHECK (length(btrim(video_url)) > 0);

-- Preserve existing job URLs; do not invent measured durations or QA results.
INSERT INTO clips (render_job_id, video_url, created_at, updated_at)
SELECT id, video_url, created_at, updated_at FROM render_jobs
WHERE status::text = 'COMPLETED' AND length(btrim(video_url)) > 0
ON CONFLICT (render_job_id) DO NOTHING;
