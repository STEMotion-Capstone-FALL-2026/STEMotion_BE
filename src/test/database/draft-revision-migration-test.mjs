import {PGlite} from '@electric-sql/pglite';
import {readFileSync} from 'node:fs';
import assert from 'node:assert/strict';

const up = readFileSync('../../main/resources/db/migration/V22__script_draft_revision.sql', 'utf8');
const down = readFileSync('../../../docs/rollback/V22__script_draft_revision.down.sql', 'utf8');

for (const keyType of ['varchar(36)', 'uuid']) {
  const db = new PGlite();
  await db.exec(`CREATE TABLE script_versions (id ${keyType} PRIMARY KEY, version_no int NOT NULL)`);
  const id = keyType === 'uuid' ? '00000000-0000-0000-0000-000000000001' : 'v1';
  await db.exec(`INSERT INTO script_versions VALUES ('${id}', 1)`);
  await db.exec(up);
  await db.exec(up); // re-runnable
  assert.equal((await db.query('SELECT draft_revision FROM script_versions')).rows[0].draft_revision, 0,
    'existing versions start at revision 0');
  await assert.rejects(db.exec('UPDATE script_versions SET draft_revision = -1'), 'negative revision');
  await assert.rejects(db.exec('UPDATE script_versions SET draft_revision = NULL'), 'null revision');
  await db.exec('UPDATE script_versions SET draft_revision = draft_revision + 1');
  await db.exec(down);
  const cols = (await db.query(`SELECT column_name FROM information_schema.columns
    WHERE table_name = 'script_versions'`)).rows.map((r) => r.column_name);
  assert.ok(!cols.includes('draft_revision'));
  await db.exec(up);
  console.log(`${keyType}: V22 up/reapply/checks/down/reapply PASS`);
}
