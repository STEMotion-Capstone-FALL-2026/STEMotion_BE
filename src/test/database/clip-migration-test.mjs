import {PGlite} from '@electric-sql/pglite';
import {readFileSync} from 'node:fs';
import assert from 'node:assert/strict';

const up = readFileSync('../../main/resources/db/migration/V10__clip_artifacts.sql', 'utf8');
const down = readFileSync('../../../docs/rollback/V10__clip_artifacts.down.sql', 'utf8');
for (const keyType of ['uuid', 'varchar(36)']) {
  const db = new PGlite();
  await db.exec(`CREATE TYPE render_status AS ENUM ('COMPLETED','PROCESSING','FAILED');
    CREATE TABLE render_jobs (id ${keyType} PRIMARY KEY, status render_status,
      video_url text, created_at timestamp NOT NULL, updated_at timestamp NOT NULL);
    INSERT INTO render_jobs VALUES
    ('00000000-0000-0000-0000-000000000001','COMPLETED','https://storage.example/1.mp4',now(),now()),
    ('00000000-0000-0000-0000-000000000002','PROCESSING','https://storage.example/2.mp4',now(),now()),
    ('00000000-0000-0000-0000-000000000003','COMPLETED',' ',now(),now());`);
  const before = (await db.query('SELECT * FROM render_jobs ORDER BY id')).rows;
  await db.exec(up);
  const clips = (await db.query('SELECT * FROM clips')).rows;
  assert.equal(clips.length, 1);
  assert.equal(clips[0].qa_status, 'PENDING');
  assert.equal(clips[0].duration_sec, null);
  assert.equal(clips[0].qa_report, null);
  await assert.rejects(db.exec(`INSERT INTO clips(render_job_id,video_url)
    VALUES ('00000000-0000-0000-0000-000000000001','https://other.example/duplicate.mp4')`));
  await assert.rejects(db.exec(`INSERT INTO clips(render_job_id,video_url)
    VALUES ('00000000-0000-0000-0000-000000000099','https://other.example/orphan.mp4')`));
  await assert.rejects(db.exec(`UPDATE clips SET qa_status = 'FAKE_PASS'`));
  await assert.rejects(db.exec(`UPDATE clips SET duration_sec = -1`));
  await db.exec(down);
  assert.deepEqual((await db.query('SELECT * FROM render_jobs ORDER BY id')).rows, before);
  assert.equal((await db.query("SELECT to_regclass('clips') AS name")).rows[0].name, null);
  await db.exec(up);
  assert.equal((await db.query('SELECT * FROM clips')).rows.length, 1);
  await db.close();
  console.log(`${keyType}: up/backfill/unique/FK/check/down/reapply PASS`);
}

// Diagram-first shared DB: migration must preserve pre-existing Clip IDs and QA.
const db = new PGlite();
await db.exec(`CREATE TYPE qa_result AS ENUM ('PENDING','PASS','FAIL');
  CREATE TABLE render_jobs (id uuid PRIMARY KEY, status text, video_url text,
    created_at timestamp NOT NULL, updated_at timestamp NOT NULL);
  INSERT INTO render_jobs VALUES ('00000000-0000-0000-0000-000000000001',
    'COMPLETED','https://storage.example/current.mp4',now(),now());
  CREATE TABLE clips (id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    render_job_id uuid UNIQUE NOT NULL REFERENCES render_jobs(id), video_url text NOT NULL,
    duration_sec integer NOT NULL, qa_status qa_result DEFAULT 'PENDING',
    qa_report jsonb DEFAULT '{}'::jsonb, created_at timestamptz DEFAULT now());
  INSERT INTO clips(id,render_job_id,video_url,duration_sec,qa_status) VALUES
    ('00000000-0000-0000-0000-000000000011','00000000-0000-0000-0000-000000000001',
      'https://storage.example/original.mp4',42,'PASS');`);
const original = (await db.query('SELECT * FROM clips')).rows;
await db.exec(up);
assert.equal((await db.query('SELECT count(*)::int AS n FROM clips')).rows[0].n, 1);
const aligned = (await db.query('SELECT * FROM clips')).rows[0];
assert.equal(aligned.id, original[0].id);
assert.equal(aligned.video_url, original[0].video_url);
assert.equal(aligned.qa_status, 'PASS');
await db.exec(readFileSync('../../../docs/rollback/V10__clip_artifacts.shared.down.sql','utf8'));
assert.deepEqual((await db.query('SELECT * FROM clips')).rows, original);
await db.exec(up);
await db.close();
console.log('existing shared clips: preserve IDs/URL/QA/up/down/reapply PASS');
