-- Composed scenes: assembled from blocks instead of a fixed template. Their
-- frame and blocks live in the existing JSONB props, so only the scene type
-- needs to widen.
--
-- The Flyway baseline stores scene_type as varchar with a CHECK constraint;
-- the diagram-first Supabase baseline uses an enum type. Handle both.
DO $$
DECLARE col_type text;
BEGIN
    SELECT format_type(atttypid, atttypmod) INTO col_type FROM pg_attribute
        WHERE attrelid = 'scenes'::regclass AND attname = 'scene_type';
    IF col_type LIKE 'character varying%' OR col_type = 'text' THEN
        ALTER TABLE scenes DROP CONSTRAINT IF EXISTS scenes_scene_type_check;
        ALTER TABLE scenes ADD CONSTRAINT scenes_scene_type_check CHECK (scene_type IN (
            'TITLE_HERO', 'MATH_FORMULA', 'DIAGRAM_EXPLAINER', 'DATA_CHART',
            'ALGORITHM_WALKTHROUGH', 'STEM_QUIZ', 'OUTRO',
            'CHEMICAL_REACTION', 'COMPARISON_SPLIT', 'PROCESS_TIMELINE', 'GEOMETRY_SPACE',
            'ILLUSTRATED_EXPLAINER', 'COMPOSED'
        ));
    ELSE
        EXECUTE format('ALTER TYPE %s ADD VALUE IF NOT EXISTS %L', col_type, 'COMPOSED');
    END IF;
END $$;
