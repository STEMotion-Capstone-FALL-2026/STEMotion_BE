-- Flat-explainer scene: STEM icons composed as an illustration on a moving
-- backdrop. Its props (headline, caption, ambience, layout, icons) live in the
-- existing JSONB column, so only the scene type check needs to widen.

ALTER TABLE scenes DROP CONSTRAINT scenes_scene_type_check;

ALTER TABLE scenes ADD CONSTRAINT scenes_scene_type_check CHECK (scene_type IN (
    'TITLE_HERO', 'MATH_FORMULA', 'DIAGRAM_EXPLAINER', 'DATA_CHART',
    'ALGORITHM_WALKTHROUGH', 'STEM_QUIZ', 'OUTRO',
    'CHEMICAL_REACTION', 'COMPARISON_SPLIT', 'PROCESS_TIMELINE', 'GEOMETRY_SPACE',
    'ILLUSTRATED_EXPLAINER'
));
