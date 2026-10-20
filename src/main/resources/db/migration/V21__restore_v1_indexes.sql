-- DB performance plan, step 3 (docs/DB_PERFORMANCE_PLAN_2026-10-19.md).
--
-- The shared Supabase database was baselined at V1 by Flyway, so V1__initial_schema.sql never
-- ran there and none of its secondary indexes exist (confirmed with pg_indexes on 2026-10-19).
-- They are created here with the same names; on a database built from V1 every statement is a
-- no-op. Two more serve the reworked queries: the library filters projects by video status, and
-- every request reads the current user's memberships by user_id (the unique index on
-- (workspace_id, user_id) cannot serve that lookup).
--
-- Plain CREATE INDEX: the tables hold a few hundred rows, so the short lock is harmless.

CREATE INDEX IF NOT EXISTS idx_clip_projects_workspace ON clip_projects (workspace_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_clip_projects_owner ON clip_projects (owner_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_clip_projects_script_status ON clip_projects (script_status);
CREATE INDEX IF NOT EXISTS idx_clip_projects_video_status ON clip_projects (video_status);
CREATE INDEX IF NOT EXISTS idx_script_versions_project ON script_versions (project_id, version_no DESC);
CREATE INDEX IF NOT EXISTS idx_scenes_version ON scenes (version_id, order_no);
CREATE INDEX IF NOT EXISTS idx_feedback_project ON feedback_comments (project_id, timestamp_sec);
CREATE INDEX IF NOT EXISTS idx_feedback_scene ON feedback_comments (scene_id);
CREATE INDEX IF NOT EXISTS idx_render_jobs_project ON render_jobs (project_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_render_jobs_external ON render_jobs (external_job_id);
CREATE INDEX IF NOT EXISTS idx_publish_project ON publish_records (project_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_status_histories_project ON status_histories (project_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_workspace_members_user ON workspace_members (user_id);
