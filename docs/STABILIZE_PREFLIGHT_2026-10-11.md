# Stabilization release preflight — 2026-10-11

Owner approved sequential push BE deploy/render-staging → FE main → worker main. Worker hosting/deployment is postponed. This document records local checks before push; it does not prove production deployment success.

| Check | Result |
|---|---|
| AI accept/ignore selected LocalAiDecisionE2eTest | 4 passed, 0 failures/errors/skips |
| BE full `mvn test`, both local E2E classes enabled | 191 total: 186 passed, 5 skipped, 0 failures/errors |
| FE `npm test` | 74 passed across 12 files |
| FE `npm run build` | Passed; large bundle warning remains |
| Worker `npm test` | 10 passed |
| Worker `npm run build` | TypeScript passed |
| Worker Linux Docker smoke | BullMQ/Redis health UP; real 2s H.264 1080p30 video; local completion callback; 32.545s elapsed; TTS disabled |

Local PostgreSQL fixture hard-bound to 127.0.0.1:15432; Gemini/render gateway stubbed for E2E. Supabase smoke explicitly disabled (five skips). No production DML, schema migration or provider quota calls in stabilization. Logs are local under workspace `.audit/stabilize-push-2026-10-11/`.

M01, M14, N08 are complete in local commits: AI usage/failure audit plus owned decisions; BE/worker/FE 1080p30 and actual per-job voice; Google TTS x-goog-api-key header with sanitized errors. Remote FastAPI rollout and worker reliability/storage/decoded QA still require later infrastructure work.

Remaining working-tree housekeeping is committed separately: cache/secret ignore rules, removal of FE .env from tracking, and archived Cloud Run preparation. The Cloud Run workflow remains manual and additionally requires ENABLE_CLOUD_RUN_STAGING=true; no such variable or cloud resource is configured in this task. BE active deployment target remains Render. V14 was already applied under earlier approval; cloud Flyway remains disabled.

After each push, verify the exact SHA's GitHub CI and deployment before proceeding. BE requires Render dashboard success and health/readiness 200 UP; FE requires Vercel SHA success and production HTTP200. Worker requires CI success only. If a deployment fails, report immediately; BE startup failure requires dashboard rollback to the previous successful deployment before investigation, not a production hotfix.
