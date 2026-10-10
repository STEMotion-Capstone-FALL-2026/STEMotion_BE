-- ONLY if publications did not exist before V13; use shared.down.sql on Supabase.
BEGIN;
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM publications) THEN
        RAISE EXCEPTION 'Published artifacts exist; retain/export data before rollback';
    END IF;
END $$;
DROP TABLE publications;
COMMIT;
