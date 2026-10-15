# Phase 1 — schema, Clip artifacts and explicit review events

Baseline: approved Phase 0 draft dated 2026-10-10. The user approved creating Clip in Phase 1 and changing project deletion to archive. Old Bond/Muse reports are not implementation evidence.

## Implemented

- V10 maps Clip to `clips`: unique render job, original artifact URL, automated QA status/report, nullable measured duration. Completion creates a Clip. Replay retains the artifact and QA; changing its URL is rejected. Review/publication lookup requires the current project, script version and video URL; ambiguous artifacts are rejected.
- V11 adds explicit ReviewDecision fields to `status_histories`: event_type, target_type, target_id, decision, reason. Script rejection binds the reviewed version, not its newly copied successor. Video reviews bind Clip. A review event is inserted even if the aggregate pipeline stays unchanged. Database triggers prevent UPDATE, DELETE and TRUNCATE of history and deletion of reviewed versions/artifacts. Old BE transition inserts remain compatible during rollout; historical transitions are not relabeled as reviews without evidence.
- DELETE `/scripts/{id}` retains its 204 contract and archives the project. Repeated archive is idempotent; a running render must be cancelled first. Archived projects disappear from workspace/owner/review/library lists. Editorial/render/review/publication mutations reject archived projects; retained history and direct historical reads remain available.
- V12 maps `ai_requests` and adds request data, call outcome, input/output token counts and nullable monetary cost/currency. Failures can be recorded before an artifact exists. Existing rows retain UNKNOWN outcome; no provider result or price is inferred. Defaults of zero for unknown token/latency usage are removed. Actual AI call logging is Phase 2.
- V13 adds project binding and UNIQUE(project_id, platform) to canonical `publications`. INTERNAL_LIBRARY/LMS publication requires the current approved Clip and the existing metadata QA gate. Canonical and legacy `publish_records` writes are transactional. Existing response fields/endpoints remain compatible; old records are retained, not automatically converted into proof of a YouTube upload. Repeated publication conflicts with 409. A later migration will remove project uniqueness for multiple Clip versions.
- FE Producer/Admin can publish to INTERNAL_LIBRARY and receive LMS iframe code from BE. The iframe uses the actual HTTPS artifact URL, rather than a nonexistent FE `/embed` route. YouTube stays unavailable pending OAuth credentials. Library results require a canonical artifact-backed publication.

## Source/database discrepancy discovered

Direct schema metadata inspection found `clips`, `ai_requests`, `publications` and `compositions` already present in Supabase, with native enums and UUID keys, despite missing JPA mappings. New migrations preserve these tables and IDs. The fresh legacy V1 baseline uses VARCHAR(36) parent keys, so new FK types are derived from parent columns.

The existing `clips.duration_sec NOT NULL` cannot represent a worker that has not supplied a measured duration. V10 permits NULL until measurement is added in the render phase; it never invents zero. Diagram documentation should mark measured duration as nullable while unknown.

## Files

- `src/main/resources/db/migration/V10__clip_artifacts.sql` through `V13__publication_transition.sql`.
- Matching manual rollback SQL in `docs/rollback/`, with separate fresh-schema and shared-Supabase variants where required.
- Clip, Publication and AiRequest entities/repositories; ClipArtifactService; RenderService completion; PublishService/LibraryService adapter; StatusHistoryService; ScriptService archive/review; FeedbackService/controller.
- Transactional `SupabaseScriptFlowTest`, artifact/publication/review/archive unit tests, and reproducible SQL tests in `src/test/database`.
- FE `publicationService`, `PublishModal`, App refresh and publication tests.

## Validation

- BE full regression: 169 tests, 164 passed, 5 opt-in DB tests skipped; Maven package passed. The opt-in suite now contains the new artifact flow.
- FE: 51 tests passed; production build passed. Existing bundle size warning remains.
- SQL tests: up/down/reapply on UUID, legacy VARCHAR and pre-existing shared tables; preserve existing Clip IDs/URL/QA; unique/FK constraints; target ownership; same-pipeline review event; append-only history; guarded rollback; failure request without artifact; nullable unknown usage/cost; project/platform uniqueness. Run `npm ci --ignore-scripts` then `npm test` in `src/test/database`.
- Flyway 9.22.3 applied V10–V13 to an isolated PostgreSQL 17 Supabase schema. Hibernate validated mappings/JPQL there. Actual PostgreSQL rollback V13→V10 passed twice, including the final compatibility trigger. Verification schemas were removed.
- Three transactional Supabase tests passed in the isolated schema, including Clip → explicit review → canonical/legacy publication → archive and a failed AI tracking row. Fixtures rolled back; the account absence check passed. The HTTPS fixture URL is not a real rendered video and does not prove worker end-to-end rendering.
- The verification clone copies column/check/index definitions and Flyway metadata, not user data. Existing shared FK behavior is additionally tested in PGlite. After production migration, all three transactional Supabase tests passed again against the real FK schema; fixture/account absence checks passed after rollback.

## Backup, rollback and secrets

Before production migration, all 20 public tables plus schema metadata were exported in one repeatable-read snapshot, encrypted with AES-256-GCM, and verified by decrypt round-trip and ciphertext SHA-256. Backup/key/IV/manifest are under ignored `.local-backups/phase1`, restricted to the current Windows account. No backup data or keys are committed. This verifies backup integrity, not a full disaster-recovery restore drill.

User rotated Supabase production password and updated Render. Local `.env` is now usable for DB tests. The agent did not change or disclose that password. Database credentials are read from `.env` internally, never passed on the command line. Logs are redacted. No new app account is persistently created by smoke tests.

`tools/phase1-db/run_db.py` (invoke from the parent STEMotion workspace) provides clone, down-clone, backup, migrate-production. It needs local Maven-cached PostgreSQL/Flyway/Gson/Jackson dependencies and JDK 17+. The migration mode requires a verified backup manifest less than one hour old. Restrict the backup directory ACL before running backup. Do not commit `.audit` logs or `.local-backups`.

Roll back schema-dependent BE code before SQL rollback, newest migration first. Shared rollback NEVER drops original Clip/AI/Publication tables. Rollbacks refuse to discard new review binding or AI call data; Clip rollback refuses to fabricate durations to restore NOT NULL. For such cases retain the additive schema or restore affected records from the verified encrypted backup with an operator, rather than forcing destructive rollback. Flyway history reconciliation is deliberate, not automatic.

## Deferred

- Phase 2: log every real AI attempt, including failures, and complete FastAPI/LangChain/Gemini rollout. Monetary values remain unknown until real pricing exists.
- Phase 3: VPS worker and real video smoke; callback locking/retry/timeout/concurrency reliability; duration measurement; timestamp feedback pinned to Clip (legacy project pins are not guessed); composition editing and render binding; multiple Clip publication uniqueness migration.
- Asset media/scene assets, Whisper processing, YouTube OAuth, Google login and monitoring UI stay in their approved later phases. Existing database tables alone do not make these use cases implemented.
- Full API spec/deployment guide update is Phase 4. No claim of full diagram compliance or real video end-to-end success yet.

## Rollout state

Production public schema reached Flyway v13 at 14:23:42 GMT+7 on 2026-10-10, after a fresh verified encrypted backup. Production transactional smoke passed at 14:25:30. No real accounts, clips, AI calls or publications were retained from testing. Commit/push follows this schema gate because `cloud` has Flyway disabled and Hibernate validation enabled. Git push, Render deployment completion and Vercel production activation are separate facts; runtime activation must be verified after push.
