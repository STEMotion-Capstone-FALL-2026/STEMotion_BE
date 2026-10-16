import { PGlite } from '@electric-sql/pglite';
import { readFileSync } from 'node:fs';
import assert from 'node:assert/strict';

const migrations = [
  'V1__initial_schema.sql',
  'V2__promote_longtri_to_admin.sql',
  'V3__illustrated_explainer_scene.sql',
  'V4__render_resolution_1440p.sql',
  'V5__user_display_name.sql',
  'V6__align_shared_scene_order.sql',
  'V7__align_feedback_targets.sql',
  'V8__align_subject_and_user_status.sql',
  'V9__workspace_invitation_tokens.sql',
  'V10__clip_artifacts.sql',
  'V11__explicit_review_decisions.sql',
  'V12__ai_request_tracking.sql',
  'V13__publication_transition.sql',
  'V14__director_template_metadata.sql',
  'V15__composed_scene.sql',
];

const db = new PGlite();

console.log('Applying baseline migrations V1 -> V15...');
for (const file of migrations) {
  const sql = readFileSync(`../../main/resources/db/migration/${file}`, 'utf8');
  await db.exec(sql);
}

console.log('Seeding baseline data before V16 and V17...');
await db.exec(`
  INSERT INTO users (id, created_at, updated_at, full_name, display_name, email, role, status) VALUES
  ('user-1', now(), now(), 'Admin User', 'Admin User', 'admin@example.com', 'ADMIN', 'ACTIVE'),
  ('user-2', now(), now(), 'Teacher Two', 'Teacher Two', 'teacher2@example.com', 'WRITER', 'ACTIVE'),
  ('user-3', now(), now(), 'Teacher Three', 'Teacher Three', 'teacher3@example.com', 'WRITER', 'ACTIVE');

  INSERT INTO workspaces (id, created_at, updated_at, name, department, created_by) VALUES
  ('ws-1', now(), now(), 'Physics Department', 'Physics', 'user-1');

  -- Member 1: workspace ADMIN (should become is_leader=true, role=WRITER)
  INSERT INTO workspace_members (id, created_at, updated_at, workspace_id, user_id, invited_email, role, status) VALUES
  ('m-1', now(), now(), 'ws-1', 'user-1', 'admin@example.com', 'ADMIN', 'ACTIVE');

  -- Member 2: INVITED but has user_id (should become status INACTIVE)
  INSERT INTO workspace_members (id, created_at, updated_at, workspace_id, user_id, invited_email, role, status) VALUES
  ('m-2', now(), now(), 'ws-1', 'user-2', 'teacher2@example.com', 'WRITER', 'INVITED');

  -- Member 3: INVITED without user_id (unclaimed invitation, should be DELETED)
  INSERT INTO workspace_members (id, created_at, updated_at, workspace_id, user_id, invited_email, role, status) VALUES
  ('m-3', now(), now(), 'ws-1', NULL, 'unclaimed@example.com', 'WRITER', 'INVITED');
`);

// Flyway runs each migration in its own transaction; do the same here.
const inTransaction = (sql) => db.exec(`BEGIN;
${sql}
COMMIT;`);
const v16Up = readFileSync('../../main/resources/db/migration/V16__member_status_inactive_value.sql', 'utf8');
const v17Up = readFileSync('../../main/resources/db/migration/V17__admin_provisioned_membership.sql', 'utf8');

console.log('Applying V16 and V17 migrations...');
await inTransaction(v16Up);
await inTransaction(v17Up);

// 1. Verify Member 3 was deleted
const remainingMembers = (await db.query('SELECT * FROM workspace_members ORDER BY id')).rows;
assert.equal(remainingMembers.length, 2, 'Unclaimed seat should be deleted');
assert.equal(remainingMembers.some(m => m.id === 'm-3'), false, 'Member m-3 must not exist');

// 2. Verify Member 2 was converted to INACTIVE
const member2 = remainingMembers.find(m => m.id === 'm-2');
assert.equal(member2.status, 'INACTIVE', 'Invited member with user_id should be converted to INACTIVE');

// 3. Verify Member 1 was converted from ADMIN to WRITER + is_leader=true
const member1 = remainingMembers.find(m => m.id === 'm-1');
assert.equal(member1.role, 'WRITER', 'ADMIN role inside workspace converted to WRITER');
assert.equal(member1.is_leader, true, 'ADMIN role inside workspace converted to is_leader=true');

// 4. Verify dropped columns
assert.equal(member1.invited_email, undefined, 'invited_email should be dropped');
assert.equal(member1.invitation_token_hash, undefined, 'invitation_token_hash should be dropped');
assert.equal(member1.invitation_expires_at, undefined, 'invitation_expires_at should be dropped');

// 5. Verify users.must_change_password column
const users = (await db.query('SELECT must_change_password FROM users WHERE id = $1', ['user-1'])).rows;
assert.equal(users[0].must_change_password, false, 'Existing users must default to false');

// 6. Test constraints: user_id NOT NULL
await assert.rejects(
  db.exec(`INSERT INTO workspace_members (id, created_at, updated_at, workspace_id, user_id, role, status, is_leader)
           VALUES ('m-null', now(), now(), 'ws-1', NULL, 'WRITER', 'ACTIVE', false)`),
  /null value in column "user_id"/i
);

// 7. Test constraints: unique (workspace_id, user_id)
await assert.rejects(
  db.exec(`INSERT INTO workspace_members (id, created_at, updated_at, workspace_id, user_id, role, status, is_leader)
           VALUES ('m-dup', now(), now(), 'ws-1', 'user-1', 'WRITER', 'ACTIVE', false)`),
  /duplicate key value violates unique constraint "uk_workspace_member_user"/i
);

// 8. Test constraints: status only ACTIVE or INACTIVE
await assert.rejects(
  db.exec(`INSERT INTO workspace_members (id, created_at, updated_at, workspace_id, user_id, role, status, is_leader)
           VALUES ('m-inv', now(), now(), 'ws-1', 'user-3', 'WRITER', 'INVITED', false)`),
  /workspace_members_status_check/i
);

console.log('Testing V17 rollback (down)...');
const v17Down = readFileSync('../../../docs/rollback/V17__admin_provisioned_membership.down.sql', 'utf8');
await db.exec(v17Down);

const rolledBack = (await db.query('SELECT * FROM workspace_members WHERE id = $1', ['m-1'])).rows[0];
assert.equal(rolledBack.invited_email, 'admin@example.com', 'invited_email restored from user email');
assert.equal(rolledBack.is_leader, undefined, 'is_leader dropped on rollback');
assert.equal(rolledBack.role, 'ADMIN', 'a leader goes back to the workspace ADMIN role on rollback');

console.log('Re-applying V16 and V17 migrations...');
await inTransaction(v16Up);
await inTransaction(v17Up);

const reApplied = (await db.query('SELECT * FROM workspace_members WHERE id = $1', ['m-1'])).rows[0];
assert.equal(reApplied.is_leader, true, 'the leader survives a rollback and re-apply');

await db.close();

// The diagram-first Supabase baseline stores member status as an enum without
// INACTIVE. PostgreSQL rejects a new enum value used in the transaction that
// added it, which is why V16 adds the value and V17 uses it.
console.log('Applying V16 and V17 on an enum baseline...');
for (const hasInactive of [false, true]) {
  const enumDb = new PGlite();
  await enumDb.exec(`
    CREATE TYPE member_status AS ENUM ('INVITED', 'ACTIVE'${hasInactive ? ", 'INACTIVE'" : ''});
    CREATE TYPE member_role AS ENUM ('WRITER', 'REVIEWER', 'PRODUCER', 'ADMIN');
    CREATE TABLE users (id uuid PRIMARY KEY DEFAULT gen_random_uuid(), email text);
    CREATE TABLE workspaces (id uuid PRIMARY KEY DEFAULT gen_random_uuid());
    CREATE TABLE workspace_members (
      id uuid PRIMARY KEY DEFAULT gen_random_uuid(), workspace_id uuid REFERENCES workspaces(id),
      user_id uuid REFERENCES users(id), invited_email text, role member_role NOT NULL,
      status member_status NOT NULL, invitation_token_hash varchar(64), invitation_expires_at timestamptz,
      CONSTRAINT uk_workspace_member_email UNIQUE (workspace_id, invited_email));
    INSERT INTO users (email) VALUES ('lead@example.com'), ('pending@example.com');
    INSERT INTO workspaces DEFAULT VALUES;
    INSERT INTO workspace_members (workspace_id, user_id, invited_email, role, status)
      SELECT w.id, u.id, u.email, CASE WHEN u.email LIKE 'lead%' THEN 'ADMIN' ELSE 'WRITER' END::member_role,
             CASE WHEN u.email LIKE 'lead%' THEN 'ACTIVE' ELSE 'INVITED' END::member_status
      FROM users u, workspaces w;
    INSERT INTO workspace_members (workspace_id, invited_email, role, status)
      SELECT id, 'unclaimed@example.com', 'WRITER', 'INVITED' FROM workspaces;`);
  await enumDb.exec(`BEGIN;
${v16Up}
COMMIT;`);
  await enumDb.exec(`BEGIN;
${v17Up}
COMMIT;`);
  const rows = (await enumDb.query(
    'SELECT u.email, m.status::text AS status, m.is_leader, m.role::text AS role FROM workspace_members m JOIN users u ON u.id = m.user_id ORDER BY u.email')).rows;
  assert.deepEqual(rows, [
    { email: 'lead@example.com', status: 'ACTIVE', is_leader: true, role: 'WRITER' },
    { email: 'pending@example.com', status: 'INACTIVE', is_leader: false, role: 'WRITER' },
  ], `enum baseline (INACTIVE already present: ${hasInactive})`);
  await enumDb.close();
}

console.log('V1 -> V17 migration, verification, rollback, re-apply and enum baseline ALL PASSED!');
