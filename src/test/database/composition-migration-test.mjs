import {PGlite} from '@electric-sql/pglite';
import {readFileSync} from 'node:fs';
import assert from 'node:assert/strict';

const up = readFileSync('../../main/resources/db/migration/V19__production_composition.sql', 'utf8');
const down = readFileSync('../../../docs/rollback/V19__production_composition.down.sql', 'utf8');
const P = '00000000-0000-0000-0000-000000000001';
const V = '00000000-0000-0000-0000-000000000002';
const U = '00000000-0000-0000-0000-000000000003';
const J = '00000000-0000-0000-0000-000000000004';

// Flyway baseline (varchar keys) and shared Supabase baseline (uuid keys).
for (const keyType of ['varchar(36)', 'uuid']) {
  const db = new PGlite();
  await db.exec(`CREATE TABLE users (id ${keyType} PRIMARY KEY);
    CREATE TABLE clip_projects (id ${keyType} PRIMARY KEY);
    CREATE TABLE script_versions (id ${keyType} PRIMARY KEY, project_id ${keyType} REFERENCES clip_projects(id));
    CREATE TABLE render_jobs (id ${keyType} PRIMARY KEY, project_id ${keyType} REFERENCES clip_projects(id), status text);
    INSERT INTO users VALUES ('${U}'); INSERT INTO clip_projects VALUES ('${P}');
    INSERT INTO script_versions VALUES ('${V}','${P}'); INSERT INTO render_jobs VALUES ('${J}','${P}','COMPLETED');`);
  await db.exec(up);

  const job = (await db.query('SELECT composition_revision, input_props FROM render_jobs')).rows[0];
  assert.equal(job.composition_revision, null, 'existing jobs keep NULL: rendered from the script');
  assert.equal(job.input_props, null);

  await db.exec(`INSERT INTO compositions (project_id, version_id, revision, scenes, updated_by)
    VALUES ('${P}','${V}',1,'[{"id":"s1","type":"TITLE_HERO"}]','${U}')`);
  await assert.rejects(db.exec(`INSERT INTO compositions (project_id, version_id, revision, scenes)
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
  await db.exec(`INSERT INTO compositions (project_id, version_id, revision, scenes)
    VALUES ('${P}','${V}',1,'[]')`);
  await db.exec(`DELETE FROM render_jobs; DELETE FROM script_versions; DELETE FROM clip_projects`);
  assert.equal((await db.query('SELECT count(*)::int AS n FROM compositions')).rows[0].n, 0, 'deleted with its project');
  await db.close();
  console.log(`${keyType}: V19 up/unique/checks/guarded down/reapply/cascade PASS`);
}
