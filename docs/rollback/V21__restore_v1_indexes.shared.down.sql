-- Manual rollback on the shared Supabase baseline only: removes the indexes V21 created there.
-- Do not run on a database built from V1 (the V1 indexes belong to its schema there).
DROP INDEX IF EXISTS idx_clip_projects_workspace;
DROP INDEX IF EXISTS idx_clip_projects_owner;
DROP INDEX IF EXISTS idx_clip_projects_script_status;
DROP INDEX IF EXISTS idx_clip_projects_video_status;
DROP INDEX IF EXISTS idx_script_versions_project;
DROP INDEX IF EXISTS idx_scenes_version;
DROP INDEX IF EXISTS idx_feedback_project;
DROP INDEX IF EXISTS idx_feedback_scene;
DROP INDEX IF EXISTS idx_render_jobs_project;
DROP INDEX IF EXISTS idx_render_jobs_external;
DROP INDEX IF EXISTS idx_publish_project;
DROP INDEX IF EXISTS idx_status_histories_project;
DROP INDEX IF EXISTS idx_workspace_members_user;
