-- Stop schema-dependent BE code first and take a fresh backup.
-- ONLY for a baseline where clips did NOT exist before V10 (legacy V1).
-- For existing diagram/Supabase clips use V10__clip_artifacts.shared.down.sql.
-- This manual rollback removes artifact rows, but retains source jobs and URLs.
-- Later migrations referencing clips must be rolled back first (no CASCADE).
BEGIN;
DROP TABLE clips;
COMMIT;
-- Do not edit Flyway history automatically. Reconcile deployment/version history
-- with the migration operator before running the forward migration again.
