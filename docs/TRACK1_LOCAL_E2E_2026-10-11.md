# Track 1 baseline — 2026-10-11

Executed `LocalStudioE2eTest`: **4 tests passed, 0 failed, 0 skipped** (including the repeat after adding feedback identity assertions).

| Flow | Evidence | Status |
|---|---|---|
| JWT login, Guest and Admin/Writer/Reviewer/Producer gates | Real login tokens through Spring Security; Guest 401 and forbidden roles 403 | PASS |
| Writer project/draft/stable scene IDs | Controller + service + PostgreSQL; IDs and comments preserved after save; deletion of commented scene rejected | PASS |
| AI assist request | Stub Gemini, real gateway/service/controller | PASS |
| AI accept/ignore + usage persistence | No API/gateway persistence exists at this baseline | FAIL: M01, scheduled Track 2 |
| Script submit, inline comment, reject, resubmit, approve | Real DB history assertions: REJECTED and reason; explicit review events | PASS |
| Render enqueue/poll/callback/Clip | Enqueue stub; real callback security, progress DB state, Clip FK | PASS (mock worker, not real rendering) |
| QA/internal library/LMS | Advisory metadata QA passes; real publication and library reads; embed contains fixture video URL | PASS (MP4/audio quality and browser LMS playback not measured) |
| Admin provision/template metadata CRUD/queue metrics | Real endpoints + DB | PASS (BE queue metrics, not VPS/BullMQ throughput) |

## Safety and reproduction

Tests are opt-in with `STEMOTION_LOCAL_E2E=true`. Database URL/user are hard-bound in the test annotation to `127.0.0.1:15432/postgres` / `stemotion_smoke`; Flyway is disabled and Hibernate validates schema. Uses a local schema-only snapshot with V14 already present. Every fixture transaction is rolled back. Gemini and worker enqueue are mocked; no external provider quota is consumed, no Supabase data is read or written.

Run against the local PostgreSQL fixture only: `mvn -Dtest=LocalStudioE2eTest test` with the opt-in variable and a local test JWT secret. The local PostgreSQL listener must use loopback port 15432, not default 5432. Logs retained under workspace `.audit/track1-e2e.log` (ignored/local).

This is API-level E2E through JWT/controller/service/JPA, not browser-click E2E. FE and worker suites are reported separately in Track 4. The whole AI flow cannot be declared PASS until M01 is implemented and re-verified.
