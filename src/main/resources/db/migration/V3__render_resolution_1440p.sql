-- 1440p publishing preset. The render service keeps the 1920x1080 canvas and
-- scales the output, so every resolution shares one layout.

ALTER TABLE render_jobs DROP CONSTRAINT render_jobs_resolution_check;

ALTER TABLE render_jobs ADD CONSTRAINT render_jobs_resolution_check
    CHECK (resolution IN ('RES_720P', 'RES_1080P', 'RES_1440P', 'RES_4K'));
