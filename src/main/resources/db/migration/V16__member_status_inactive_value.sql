-- Adds INACTIVE to the workspace member status enum on databases that store
-- status as an enum (the diagram-first Supabase baseline).
--
-- PostgreSQL refuses to use an enum value in the same transaction that added
-- it, and Flyway runs each migration in its own transaction. V17 moves pending
-- seats to INACTIVE, so the value is added here, one migration earlier.
-- On the Flyway baseline status is varchar with a CHECK, and this is a no-op.
DO $$
DECLARE col_type text;
BEGIN
    SELECT format_type(atttypid, atttypmod) INTO col_type FROM pg_attribute
        WHERE attrelid = 'workspace_members'::regclass AND attname = 'status';
    IF NOT (col_type LIKE 'character varying%' OR col_type = 'text') THEN
        EXECUTE format('ALTER TYPE %s ADD VALUE IF NOT EXISTS %L', col_type, 'INACTIVE');
    END IF;
END $$;
