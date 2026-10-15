# Phase 1 — DB alignment and workspace authorization

The shared database has the original Supabase columns alongside columns added
by Hibernate. The API retains its existing JSON contract; this change bridges
the required columns instead of removing legacy data or enabling ddl-auto=update.

## Changes

- Public signup rejects ADMIN before any persistence. WRITER/REVIEWER/PRODUCER
  remain selectable for the current collaborative capstone workflow.
- All MVC resource routes for workspaces, projects, scenes, comments and render
  jobs require active workspace membership. Global ADMIN remains privileged.
- Draft writes require the owner or a workspace/global administrator.
- Workspace management uses the workspace ADMIN seat. A new workspace creator
  can invite members without becoming a global administrator.
- Workspace lists exclude inactive/invited seats; review queues are scoped to
  accessible workspaces. Existing role annotations on editorial/render actions
  still apply. Workspace role does not silently change the user's global role.
- Scene writes synchronize order_index with order_no.
- Feedback writes record legacy target metadata and reject scenes from another
  project or comments without either a scene or timestamp.
- ComputerScience remains the API label and maps to CS in PostgreSQL; both
  legacy varchar spellings are readable.

## Migrations and release order

Cloud profile deliberately disables Flyway. Apply reviewed SQL explicitly,
then release the matching backend; do not switch to Hibernate update.

1. V5__user_display_name.sql if not recorded/applied already.
2. V6__align_shared_scene_order.sql: backfill order_index from canonical order_no.
3. V7__align_feedback_targets.sql: retain legacy target CHECK branches and add
   the current project's timestamped video target. Invalid existing feedback
   aborts constraint validation instead of being deleted or silently repaired.
4. V8__align_subject_and_user_status.sql: permit CS with the original varchar
   schema and add BANNED to Supabase's native user_status enum when present.

Back up the database before production application. V6 changes legacy ordering
to the API order; V7 replaces a CHECK constraint; V8 adds an enum label. Enum
label removal is not an ordinary reversible ALTER, so use a forward migration
if reverting that part is necessary. Existing full_name/display_name data and
legacy feedback targets are preserved.

SQL validation ran entirely locally using PGlite/PostgreSQL WASM: V1–V8 on an
empty schema and V5–V8 against a native enum/UUID fixture matching the relevant
Supabase metadata. Checked scene backfill, CS/BANNED writes, inline and project
timestamp feedback, and rejection of target-less feedback. This does not
substitute for testing against a complete restored Supabase schema. An opt-in
Spring context test also passed Hibernate schema validation against Supabase
with Hikari connections set read-only, Flyway disabled and no API/DB writes.
This checks the real JDBC metadata/mappings, but not write constraints after
migration.

On 10 October 2026, V5–V8 were explicitly applied to the shared Supabase
database after user confirmation. Flyway validated the existing checksums
before application, applied four migrations and validated again successfully;
history records V5–V8 as SUCCESS. An AES-GCM encrypted scoped backup of affected
columns, constraints, enum definitions and migration history was saved locally
with owner-only file access before application. This is not a full database
backup; it is excluded from Git along with its encryption key.

Post-migration opt-in tests passed against Supabase: registration/login for
all three public roles, read-only Hibernate schema validation, and creation
of a ComputerScience script with a scene plus inline and timestamp feedback.
All fixture writes were rolled back, and the tests verified that their users
did not remain. Five test executions passed with no failures or errors.

Local verification artifacts are in target/check-migrations-local.cjs and
target/audit-fix-tests.log; the local PGlite dependency lives outside the product
dependency tree in ../.audit/migration-check.

## Remaining phases

Next: FE review transitions and error handling, AI template contracts; then
render UI/TTS through BE, worker callback/retry/cancel and storage, and the
temporary worker deployment. These are not claimed complete by phase 1.
