import {PGlite} from '@electric-sql/pglite';
import {readFileSync} from 'node:fs';
import assert from 'node:assert/strict';

const up = readFileSync('../../main/resources/db/migration/V21__restore_v1_indexes.sql', 'utf8');
const sharedDown = readFileSync('../../../docs/rollback/V21__restore_v1_indexes.shared.down.sql', 'utf8');
const want = ['idx_clip_projects_workspace', 'idx_clip_projects_owner', 'idx_clip_projects_script_status',
  'idx_clip_projects_video_status', 'idx_script_versions_project', 'idx_scenes_version', 'idx_feedback_project',
  'idx_feedback_scene', 'idx_render_jobs_project', 'idx_render_jobs_external', 'idx_publish_project',
  'idx_status_histories_project', 'idx_workspace_members_user'];

// The shared baseline: the tables V21 indexes, with uuid keys and no secondary indexes.
const base = `CREATE TABLE clip_projects (id uuid PRIMARY KEY, workspace_id uuid, owner_id uuid, created_at timestamp,
    script_status varchar(32), video_status varchar(32));
  CREATE TABLE script_versions (id uuid PRIMARY KEY, project_id uuid, version_no int);
  CREATE TABLE scenes (id uuid PRIMARY KEY, version_id uuid, order_no int);
  CREATE TABLE feedback_comments (id uuid PRIMARY KEY, project_id uuid, timestamp_sec double precision, scene_id uuid);
  CREATE TABLE render_jobs (id uuid PRIMARY KEY, project_id uuid, created_at timestamp, external_job_id varchar(255));
  CREATE TABLE publish_records (id uuid PRIMARY KEY, project_id uuid, created_at timestamp);
  CREATE TABLE status_histories (id uuid PRIMARY KEY, project_id uuid, created_at timestamp);
  CREATE TABLE workspace_members (id uuid PRIMARY KEY, workspace_id uuid, user_id uuid, UNIQUE (workspace_id, user_id));`;

const indexes = async (db) => (await db.query(
  `SELECT indexname FROM pg_indexes WHERE schemaname = 'public' AND indexname LIKE 'idx_%' ORDER BY 1`)).rows.map((r) => r.indexname);

const db = new PGlite();
await db.exec(base);
await db.exec(up);
assert.deepEqual(await indexes(db), [...want].sort(), 'every V21 index is created on the shared baseline');
await db.exec(up);
assert.deepEqual(await indexes(db), [...want].sort(), 're-running V21 changes nothing');
await db.exec(sharedDown);
assert.deepEqual(await indexes(db), [], 'the shared rollback removes them');
await db.exec(up);
console.log('V21 shared baseline up/reapply/down/reapply PASS');

// A database built from V1 already has the V1 indexes: V21 only adds the two new ones.
const v1 = new PGlite();
await v1.exec(readFileSync('../../main/resources/db/migration/V1__initial_schema.sql', 'utf8'));
const before = await indexes(v1);
await v1.exec(up);
const added = (await indexes(v1)).filter((name) => !before.includes(name));
assert.deepEqual(added.sort(), ['idx_clip_projects_video_status', 'idx_workspace_members_user']);
console.log('V21 on a V1-built database adds only the two new indexes PASS');
