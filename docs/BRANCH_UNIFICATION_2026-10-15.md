# BE branch unification — 2026-10-15

Approved scope: execute the six Phase 2 steps locally, then stop before Phase 3. No push, deployment, merge into main/production, production DB access, or paid provider call.

## Branches and commits

- Audit/checklist committed separately on docs/checklist-2026-10-14: ba7fc32. Only checklist.md was staged; original untracked .local-backups/ was untouched.
- Fetch confirmed origin/main eb1543c and origin/deploy/render-staging 5b90623 had not changed since the proposal. Narrative source 0525475 remained on the local feature branch (tip 6317685).
- Managed worktree creation returned “Not a git repository” because the chat root contains multiple repositories. A standard Git worktree was created instead at `<workspace>/.audit/worktrees/be-unify`, on integrate/be-unify-2026-10-15 from origin/main.
- 56c1138: actual merge of staging into integration, preserving both parent histories. Main and staging are both ancestors of the integrated code.
- 04a4642: cherry-pick of narrative feature 0525475, adapted to the gateway. The old feature checklist commit 6317685 was not cherry-picked.
- 22de4f5: integration correction for honest admin metrics plus regression tests. This is the tested code SHA; the following documentation commit changes no executable code. feat/ai-microservice is created at the final integration documentation SHA and remains empty of Phase 3 changes.

The original checkout stays on docs/checklist-2026-10-14. The integration worktree switches to feat/ai-microservice at handoff. Local main stays 07bf74b; deploy/render-staging stays 5b90623; no remote branch was updated.

## Conflict decisions and preserved behavior

1. SecurityConfig: keep staging public-signup denial, writer/admin AI authorization, readiness health access and callback shared-key boundary.
2. WorkspaceService: keep active membership filtering, one-time token acceptance and explicit authorization. Remove the main fallback exposing all workspaces when membership is empty; do not auto-activate an invited account.
3. AiSchemas: resolve the differing icon-enum comments while preserving schema compatibility and local strict validation. Narrative schema is added by the subsequent cherry-pick.
4. UserController auto-merged into duplicate POST methods. Preserve the staging controller: admin provisioning calls UserService.create and never returns a newly provisioned user's JWT. Retain list/update/delete authorization and self-or-admin profile read.
5. Restore template write authorization from staging while retaining main template-list/administration functionality and additive V14 metadata validation. Admin overview, metrics and render-job endpoints are admin-only.
6. Narrative tests had an add/add conflict. Combine staging icon/guardrail tests with narrative, validation and pacing tests; use AiGateway.Generation rather than returning raw JsonNode from a GeminiClient mock. Keep requestId/model metadata. Add the four-argument GenerateScriptRequest constructor delegating to the new optional narrativeBrief for existing Java callers.
7. Keep main dynamic pacing and strict icon validation, staging M01 audit/decisions, FastAPI generic boundary, M14 1080p30 normalization, resource-access protections and artifact/publication mappings.

## Honest metrics while preserving the overview endpoints

The overview retains its JSON property names. Actual project/version/scene counts remain numbers. Unimplemented provider call count, token usage, average latency, money cost and TTS identity now return null, rather than values inferred from content counts or constants. The model label reads the existing app.integration.gemini.model property. Optional monthly budget reads AI_MONTHLY_BUDGET_USD; missing means null.

completedToday no longer falls back to all-time completions. Missing success-rate samples and unavailable CPU use the existing -1 unavailable convention. No pricing table or /admin/ai-usage implementation has been added; those remain Phase 3 Task 4.

Compatibility limitation: the current FE tolerates null through optional chaining, but displays its own fallback values (including zero money / assumed latency). FE has not been edited. End-to-end admin UI presentation must treat unavailable values explicitly before a release; this backend is not being deployed now.

## Verification

All commands ran from the isolated worktree without a production .env file. Provider tests use mocks/stubs. Logs live outside Git under `<workspace>/.audit/`.

| Check | Result | Evidence |
|---|---|---|
| Java suite on final code | 212 cases, 199 passed, 13 conditional skips, zero failures/errors | phase2-java-final.log |
| PostgreSQL JWT/controller/JPA integration | 8 passed, zero skips/failures/errors | phase2-postgres-e2e.log |
| FastAPI contract tests | 6 passed | phase2-python-tests.log |
| Local schema migration tests | V10–V14 up/down/reapply, backfill, constraints, guarded rollback passed | src/test/database npm test output |
| JAR package | BUILD SUCCESS, tests not repeated during package | phase2-package.log |
| Migration integrity | No diff from staging for src/main/resources/db/migration | git diff 5b90623 HEAD -- src/main/resources/db/migration |
| Git integrity | Both origin/main and staging are ancestors; no unresolved merge/conflict markers; diff whitespace check clean | git merge-base / git diff --check |

The Java suite's skipped cases include eight opt-in local E2E cases and five shared-Supabase cases. The eight local E2E cases were then run explicitly and all passed. The five shared-Supabase cases remain skipped intentionally. The interrupted initial sandbox run is not counted as the successful suite.

New regression coverage includes admin endpoint guest/ordinary-role denial, admin access, template/user mutation denial, unavailable usage not fabricated from content counts, and historical completions not counted as today's jobs. Existing tests cover strict icon, narrative arc, pacing branches, M01 audit/decisions, M14 render contract, invitations and draft access.

PostgreSQL 18 fixture: a newly initialized task-owned cluster, bind 127.0.0.1:15432 only, trusted local test role. Schema-only pre-V14 DDL from the existing audit plus exact V14 SQL was loaded locally. No production user rows, credentials or database connection were used. Hibernate validate succeeded during both integration test contexts. This does not claim that a fresh V1–V14 Flyway bootstrap against production has been exercised.

Python dependencies were installed from services/ai/requirements.lock into a worktree-local ignored .venv. PGlite dependencies were installed from the committed lockfile; no lockfile change was needed.

### Redis limitation — not a passing test

Docker was initially stopped. Docker Desktop was started hidden for a local Redis fixture but failed to initialize its inference-manager Unix socket. The docker-desktop WSL distro had no Redis, dockerd, ctr or active Docker socket. No factory reset, Docker settings change, WSL reinstall, remote Redis connection, or production worker call was attempted. Redis/BullMQ integration therefore remains NOT RUN, including same-jobId retry behavior. BE workflow tests stub the render client and do not prove BullMQ reliability or actual render/TTS execution.

Redis is a remaining validation item before release. The tested base can be used for the AI-only Phase 3 work, but is not a claim of full pipeline/production readiness.

## Handoff / stop

The integration changes are local only. PostgreSQL fixture is stopped after verification, Docker startup is cleaned up, and the original .local-backups/ remains in place. No migrations were applied to production. No Phase 3 task has been started.

Next action requires the user's instruction to begin Phase 3 from feat/ai-microservice. Its four task commits must remain local; pushing, main/production promotion and deployment still require separate explicit authorization.
