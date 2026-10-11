# Test report — Tracks 1–4, 2026-10-11

Tests use local fixtures/stubs. No production DML, migrations, provider Gemini calls, push or deployment. FPT was called only for the requested local demo and returned 429. Counts below are final suite results, not a sum of repeated runs.

| Surface | Final scope | Pass | Fail | Skip | Evidence |
|---|---|---:|---:|---:|---|
| BE | All tests except LocalStudioE2eTest; selected local AI E2E enabled, Supabase smoke disabled | 182 | 0 | 5 | `.audit/track4-be-tests.log`: 187 total |
| FE | 12 Vitest files | 74 | 0 | 0 | `.audit/track4-fe-tests.log` |
| Worker | Security, FPT timing/backoff, per-job voice, Google key transport/redaction | 10 | 0 | 0 | `.audit/track2-n08-worker-test.log` |
| AI FastAPI | API auth/response/errors/provider metadata adapter with fake LangChain | 6 | 0 | 0 | `.audit/track4-ai-tests.log` |
| Track 1 baseline | LocalStudioE2eTest, executed before fixes; not rerun after user adjustment | 4 | 0 | 0 | `.audit/track1-e2e.log`; flow limitations in Track 1 report |
| Asset rendering | 9 assets × repeat still render in Chromium | 9 assets | 0 | 0 | FE local artifacts `phase-b-fpt/render-tests.json` |

BE subset evidence: M01 11 passed (4 targeted AI E2E + 3 gateway + 4 script); M14 15 passed (contract/retry/client). These are included in final BE suite, not additional independent test counts. FE targeted AI service 16 passed; visual/motion 10 passed, included in full 74. Worker tests contain real ffmpeg/ffprobe timing normalization; Google/FPT HTTP behavior otherwise stubbed. FastAPI warning: Starlette/httpx deprecated adapter; no real LangChain/Gemini provider execution demonstrated by these six tests.

## Track 1 updated disposition

| Flow | Baseline / post-fix result | Practical limit |
|---|---|---|
| Auth JWT, Guest/Admin/Writer/Reviewer/Producer gates | PASS baseline | API-level controller/JPA, not browser role journeys |
| Writer create/edit/stable scene IDs/save draft | PASS baseline | Local schema fixture, no production writes |
| AI request → accept/ignore | **PASS post-M01, 4 targeted E2E tests** | Provider stub; real JWT/controller/PostgreSQL persistence/ownership/idempotency/failure audit |
| Submit/comment/reject/resubmit/approve/history | PASS baseline | No full Track 1 rerun requested |
| Render submit/poll/callback/Clip binding | PASS baseline using enqueue stub | No live FE→BE→worker→storage MP4 E2E |
| QA/internal library/LMS | PASS baseline metadata/publication/embed assertions | Not decoded artifact QA or browser LMS playback |
| Admin provision/template metadata/metrics | PASS baseline | Metrics are BE jobs/host, not deployed VPS telemetry |

## Builds / runtime / media

- FE TypeScript + Vite production build PASS. Chunk-size warning remains; not a build failure.
- Worker TypeScript build PASS.
- BE package PASS after resolving file lock: an old Codex local read-only smoke Java process held the JAR. PID/port/read-only flags verified before terminating that process. Two earlier package attempts failed file rename; no source change was required. New packaged local BE health/readiness 200 UP, Hibernate validate, Flyway off, datasource loopback read-only; generated 61-operation OpenAPI. Evidence `.audit/track4-be-package.log`, `track4-openapi-boot.json`.
- Music-only preview PASS: 1920x1080, 1800 frames, ~60s, audio stream. **Not narrated acceptance video.** Transition/music timeline tests pass; sampled layout snapshots inspected.
- Live FPT demo FAIL/BLOCKED: HTTP 429 after four attempts/backoff at first narration. No new FPT timings/MP4. No hidden Edge fallback. The user-choice question remains pending.

## Safety and reproducibility

BE command: `STEMOTION_LOCAL_E2E=true`, `STEMOTION_DB_SMOKE=false`, `mvn -Dtest=*,!LocalStudioE2eTest test`. Local AI E2E hard-binds loopback port 15432, cleans its fixture users/requests; other tests are mock/unit. No production registration/login/AI/publish test was run. Existing schema-only local DB is not a copy of production application records. AI audit REQUIRES_NEW persistence is verified by the failure test, not masked by outer test rollback.

FE: `npm test`, `npm run build`. Worker: `npm test`, `npm run build`. AI: `python -m pytest -p no:cacheprovider tests -q` with temporary ignored test dependencies; provider tests mock LangChain. Test helper dependencies are local only, not shipped with app.

No env files or secrets changed, no migration added/applied/up/down in this task. V14 up/down/production rollout evidence remains the earlier approved V14 report. Known P2 M02–M06 and production render/storage/QA/YouTube/OAuth gaps were not fixed here.
