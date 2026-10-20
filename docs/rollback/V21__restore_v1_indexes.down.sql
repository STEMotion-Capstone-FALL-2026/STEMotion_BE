-- Manual rollback on a database built from V1: only the two indexes V21 added beyond V1.
DROP INDEX IF EXISTS idx_clip_projects_video_status;
DROP INDEX IF EXISTS idx_workspace_members_user;
