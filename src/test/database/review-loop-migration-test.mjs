import {PGlite} from '@electric-sql/pglite';
import {readFileSync} from 'node:fs';
import assert from 'node:assert/strict';

const up = readFileSync('../../main/resources/db/migration/V20__review_loop_and_theme.sql', 'utf8');
const down = readFileSync('../../../docs/rollback/V20__review_loop_and_theme.down.sql', 'utf8');
const sharedDown = readFileSync('../../../docs/rollback/V20__review_loop_and_theme.shared.down.sql', 'utf8');
const U = '00000000-0000-0000-0000-000000000003';
const C = '00000000-0000-0000-0000-000000000010';
const R = '00000000-0000-0000-0000-000000000011';

// shared: the Supabase baseline (uuid keys, resolved_by/resolved_at already there);
// flyway: the Flyway baseline (varchar(36) keys, no resolved_* columns).
const base = (keyType, shared) => `CREATE TABLE users (id ${keyType} PRIMARY KEY);
  CREATE TABLE compositions (id uuid PRIMARY KEY DEFAULT gen_random_uuid(), revision integer, scenes jsonb);
  CREATE TABLE feedback_comments (id ${keyType} PRIMARY KEY, clip_id ${keyType}, content text NOT NULL
    ${shared ? `, resolved_by ${keyType}, resolved_at timestamp with time zone` : ''});
  CREATE TABLE status_histories (id ${keyType} PRIMARY KEY, event_type varchar(32) NOT NULL,
    target_type varchar(16) NOT NULL, target_id varchar(36) NOT NULL, decision varchar(16));
  CREATE TABLE remotion_templates (id ${keyType} PRIMARY KEY, name varchar(255) NOT NULL);
  INSERT INTO users VALUES ('${U}'); INSERT INTO compositions (revision, scenes) VALUES (1, '[]');
  INSERT INTO remotion_templates VALUES ('${U}', 'TITLE_HERO');`;

for (const [keyType, shared] of [['varchar(36)', false], ['uuid', true]]) {
  const db = new PGlite();
  await db.exec(base(keyType, shared));
  await db.exec(up);
  await db.exec(up); // re-runnable

  assert.equal((await db.query('SELECT visual_theme FROM compositions')).rows[0].visual_theme, 'default');
  await assert.rejects(db.exec(`UPDATE compositions SET visual_theme = 'neon'`), 'unknown theme');
  await db.exec(`UPDATE compositions SET visual_theme = 'cyber'`);

  await db.exec(`INSERT INTO status_histories VALUES ('${C}','REVIEW_DECISION','COMPOSITION','x','APPROVED')`);
  await assert.rejects(db.exec(`INSERT INTO status_histories VALUES ('${R}','REVIEW_DECISION','PROJECT','x','APPROVED')`));

  await db.exec(`INSERT INTO feedback_comments (id, content) VALUES ('${C}', 'note')`);
  await db.exec(`INSERT INTO feedback_comments (id, content, parent_id, resolved_by, resolved_at)
    VALUES ('${R}', 'reply', '${C}', '${U}', now())`);
  await db.exec(`DELETE FROM feedback_comments WHERE id = '${C}'`);
  assert.equal((await db.query('SELECT count(*)::int AS n FROM feedback_comments')).rows[0].n, 0, 'replies go with their note');

  const t = (await db.query('SELECT recommended_subjects, default_visual_theme FROM remotion_templates')).rows[0];
  assert.deepEqual(t.recommended_subjects, []);
  assert.equal(t.default_visual_theme, null);
  await assert.rejects(db.exec(`UPDATE remotion_templates SET recommended_subjects = '{}'`));
  await assert.rejects(db.exec(`UPDATE remotion_templates SET default_visual_theme = 'neon'`));

  // Rollback refuses while web-player decisions exist, then drops what V20 added.
  const rollback = shared ? sharedDown : down;
  await assert.rejects(db.exec(rollback), /COMPOSITION/);
  await db.exec(`DELETE FROM status_histories`);
  await db.exec(rollback);
  const cols = (await db.query(`SELECT column_name FROM information_schema.columns
    WHERE table_name = 'feedback_comments'`)).rows.map((r) => r.column_name);
  assert.ok(!cols.includes('parent_id'));
  assert.equal(cols.includes('resolved_by'), shared, 'the shared baseline keeps its own resolved_* columns');
  await db.exec(up); // and can be applied again
  console.log(`${keyType}: V20 up/checks/cascade/guarded down/reapply PASS`);
}
