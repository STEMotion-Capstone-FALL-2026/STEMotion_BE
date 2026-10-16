import {PGlite} from '@electric-sql/pglite';
import {readFileSync} from 'node:fs';
import assert from 'node:assert/strict';

const up = readFileSync('../../main/resources/db/migration/V19__production_composition.sql', 'utf8');
const down = readFileSync('../../../docs/rollback/V19__production_composition.down.sql', 'utf8');
const sharedDown = readFileSync('../../../docs/rollback/V19__production_composition.shared.down.sql', 'utf8');
const P = '00000000-0000-0000-0000-000000000001';
const V = '00000000-0000-0000-0000-000000000002';
const U = '00000000-0000-0000-0000-000000000003';
const J = '00000000-0000-0000-0000-000000000004';
const S = '00000000-0000-0000-0000-000000000005';

const base = (keyType) => `CREATE TABLE users (id ${keyType} PRIMARY KEY);
  CREATE TABLE clip_projects (id ${keyType} PRIMARY KEY);
  CREATE TABLE script_versions (id ${keyType} PRIMARY KEY, project_id ${keyType} REFERENCES clip_projects(id));
  CREATE TABLE scenes (id ${keyType} PRIMARY KEY, version_id ${keyType} REFERENCES script_versions(id),
    order_no integer, scene_type varchar(40), title varchar(255), narration text, duration_in_frames integer, props jsonb);
  CREATE TABLE render_jobs (id ${keyType} PRIMARY KEY, project_id ${keyType} REFERENCES clip_projects(id), status text);
  INSERT INTO users VALUES ('${U}'); INSERT INTO clip_projects VALUES ('${P}');
  INSERT INTO script_versions VALUES ('${V}','${P}'); INSERT INTO render_jobs VALUES ('${J}','${P}','COMPLETED');`;

// Flyway baseline (varchar keys) and a uuid baseline without compositions: V19 creates the table.
for (const keyType of ['varchar(36)', 'uuid']) {
  const db = new PGlite();
  await db.exec(base(keyType));
  await db.exec(up);

  const job = (await db.query('SELECT composition_revision, input_props FROM render_jobs')).rows[0];
  assert.equal(job.composition_revision, null, 'existing jobs keep NULL: rendered from the script');
  assert.equal(job.input_props, null);

  await db.exec(`INSERT INTO compositions (project_id, script_version_id, revision, scenes, producer_id)
    VALUES ('${P}','${V}',1,'[{"id":"s1","type":"TITLE_HERO"}]','${U}')`);
  await assert.rejects(db.exec(`INSERT INTO compositions (project_id, script_version_id, revision, scenes)
    VALUES ('${P}','${V}',1,'[]')`), 'one composition per script version');
  await assert.rejects(db.exec(`UPDATE compositions SET revision = 0`), 'revision starts at 1');
  await assert.rejects(db.exec(`UPDATE compositions SET scenes = '{}'`), 'scenes is a list');
  await assert.rejects(db.exec(`UPDATE render_jobs SET composition_revision = 0`));
  const composition = (await db.query('SELECT id FROM compositions')).rows[0].id;
  await db.exec(`UPDATE render_jobs SET composition_id = '${composition}', composition_revision = 1,
    input_props = '{"scenes":[]}'`);
  await assert.rejects(db.exec(`UPDATE render_jobs SET composition_id = gen_random_uuid()`),
    'a job points at an existing composition (ERD: Composition triggers RenderJob)');

  await assert.rejects(db.exec(down), 'rollback refuses to drop producer edits');
  await db.exec('ROLLBACK');
  await db.exec(`DELETE FROM compositions`);
  await db.exec(down);
  assert.equal((await db.query("SELECT to_regclass('compositions') AS name")).rows[0].name, null);
  await db.exec(up);
  await db.exec(up); // idempotent re-apply
  await db.exec(`INSERT INTO compositions (project_id, script_version_id, revision, scenes)
    VALUES ('${P}','${V}',1,'[]')`);
  await db.exec(`DELETE FROM render_jobs; DELETE FROM script_versions; DELETE FROM clip_projects`);
  assert.equal((await db.query('SELECT count(*)::int AS n FROM compositions')).rows[0].n, 0, 'deleted with its project');
  await db.close();
  console.log(`${keyType}: V19 up/unique/checks/guarded down/reapply/cascade PASS`);
}

// Shared Supabase baseline: compositions and render_jobs.composition_id already exist.
const shared = `${base('uuid')}
  CREATE TABLE compositions (id uuid DEFAULT gen_random_uuid() PRIMARY KEY,
    script_version_id uuid UNIQUE REFERENCES script_versions(id) ON DELETE CASCADE,
    producer_id uuid REFERENCES users(id) ON DELETE SET NULL, resolution varchar(50) DEFAULT '1080p',
    fps integer DEFAULT 30, created_at timestamptz DEFAULT now(), updated_at timestamptz DEFAULT now());
  ALTER TABLE render_jobs ADD COLUMN composition_id uuid REFERENCES compositions(id) ON DELETE CASCADE;
  INSERT INTO scenes VALUES ('${S}','${V}',1,'OUTRO','Kết','Tạm biệt',90,'{"summaryPoints":["a"]}');`;
{
  const db = new PGlite();
  await db.exec(shared);
  await db.exec(`INSERT INTO compositions (script_version_id, producer_id) VALUES ('${V}','${U}')`);
  await db.exec(up);
  const row = (await db.query('SELECT project_id, revision, scenes, resolution FROM compositions')).rows[0];
  assert.equal(row.project_id, P, 'project taken from the script version');
  assert.equal(row.revision, 1);
  assert.equal(row.resolution, '1080p', 'existing columns are kept');
  assert.deepEqual(row.scenes, [{id: S, type: 'OUTRO', title: 'Kết', narration: 'Tạm biệt',
    durationInFrames: 90, summaryPoints: ['a']}], 'scenes taken from the version, in the SceneData shape');
  await db.exec(up); // re-apply on the shared baseline
  await db.exec(sharedDown);
  assert.notEqual((await db.query("SELECT to_regclass('compositions') AS name")).rows[0].name, null,
    'the shared rollback keeps the table that existed before V19');
  assert.equal((await db.query(`SELECT count(*)::int AS n FROM information_schema.columns
    WHERE table_name = 'render_jobs' AND column_name = 'composition_id'`)).rows[0].n, 1);
  await db.exec(up);
  await db.close();
  console.log('shared baseline: keep table, backfill project/scenes/revision, re-apply, shared down PASS');
}
{
  const db = new PGlite();
  await db.exec(shared);
  await db.exec(`INSERT INTO compositions (producer_id) VALUES ('${U}')`);
  await assert.rejects(db.exec(up), /without a script version/, 'an orphan composition stops V19 with a reason');
  await db.close();
  console.log('shared baseline: orphan composition refused PASS');
}
