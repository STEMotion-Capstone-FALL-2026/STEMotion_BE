# SRS/SDD self-review — raw AI output (do not edit)

- Prompt: SWP490 standard self-review prompt (Review 2 guide, section 05), used without modification.
- AI tool and model: Claude Code, Anthropic Claude Opus 5.5 (claude-opus-5-5), with read access to all three repositories.
- Run date: 01/11/2026.
- Inputs: registration form version 2 (SWP490_FA26SE054_R2_Registration_v2.docx), SRS (docs/STEMotion_Report3_SRS.md), SDD (docs/STEMotion_Report4_Software_Design_Document.docx, diagrams in docs/diagrams/*.mmd), source code and Flyway migrations.
- Repository state: STEMotion_BE 7f207bb plus the Review 2 documents of this batch (not yet committed when the run started), STEMotion_FE aa4d722, STEMotion_RENDER 7e5aeea. Tag review-2 not yet created.

| ID | Category | Location | Finding | Severity | Evidence |
|---|---|---|---|---|---|
| SR-01 | Scope | SRS IV.3.3 FR-17, IV.3.6 FR-31, IV.3.10 FR-44, IV.3.11 FR-45 to FR-47 | FRs marked Planned (project proposal, edit lease, AI/TTS configuration, join requests, contribution events, Leader dashboard) are not covered by registration form v2. | Medium | Registration v2 section 3.2 has no proposal, lease, contribution or Leader dashboard item; SRS marks these FRs Planned. |
| SR-02 | Scope | Registration 3.2.c Production Workspace ("swap suggested assets") | No FR covers swapping suggested assets in a scene. | Medium | SRS IV.3.6 FR-26 to FR-31 do not mention assets. |
| SR-03 | Scope | Registration 3.2.c Review & Approval ("compare ... a re-rendered clip") | FR-15 covers script versions only; clip comparison has a use case (UC-21) and a screen but no FR. | Low | SRS FR-15; STEMotion_FE/src/components/modals/ClipCompareModal.tsx. |
| SR-04 | Scope | Registration 3.2.c AI Assistance ("automatic caption generation with timing alignment") | No FR states caption generation explicitly; FR-29 covers duration sync only. | Low | SRS FR-29; STEMotion_RENDER/src/captions.ts. |
| SR-05 | Scope | Registration 3.2.c Admin ("system monitoring ... storage") | FR-43 does not include storage monitoring. | Low | SRS FR-43; no storage metric in domain/admin. |
| SR-06 | Requirements | SRS IV.3 FR-01 to FR-47 | FRs are "shall" statements without separate testable acceptance criteria (precondition, input, expected result). | Medium | SRS IV.3; acceptance criteria appear only in the Design Brief section 6. |
| SR-07 | Requirements | SRS V.2 NFR-01 to NFR-04 | Thresholds are stated but marked "not yet measured"; NFR-04 gives "Load test" without tool, data volume or duration. | Medium | Note under SRS V.2 table. |
| SR-08 | Requirements | SRS V.2 NFR-08, NFR-11, NFR-12 | Measurement methods ("Code review", "Configuration review", "User test with 3 teachers") have no pass threshold. | Low | SRS V.2. |
| SR-09 | Data | SDD 2.1 Logical ERD | The logical ERD omits columns present in the database script (users.display_name, users.google_id, scenes.order_index, feedback_comments.scene_key, render_jobs.stage, error_message, resolution, fps, tts_voice, input_props). | Low | Migrations V1 to V22; entity classes. |
| SR-10 | Data | Database script vs deployed database | Cannot be verified from the repository whether the shared database holds tables outside the 15 created by migrations V1 to V22. | Low | Only src/main/resources/db/migration is in the repository. |
| SR-11 | States | SDD 2.2 ClipProject video status | Code allows RENDERING -> IN_QA (submit for web review while a render is running); the state machine shows only NOT_RENDERED -> IN_QA. | Medium | FeedbackService.submitForVideoReview accepts NOT_RENDERED or RENDERING. |
| SR-12 | States | SDD 2.2 RenderJob | A callback can move a job QUEUED -> COMPLETED directly; the state machine requires PROCESSING first. | Low | RenderService.applyCallback sets the status from the callback without checking PROCESSING. |
| SR-13 | States | SDD 2.2 | Lifecycle fields without a state machine: User.status (ACTIVE/INACTIVE), Clip.qa_status (PENDING/PASS/FAIL), AiRequest.call_status and decision, ClipProject.archived. | Low | User.java, Clip.java, AiRequest.java, ClipProject.java. |
| SR-14 | Flows | SDD 4.1 to 4.4 activity diagrams WF-01 to WF-04 | End-to-end tests exist (LocalStudioE2eTest) but run only when STEMOTION_LOCAL_E2E=true and not in CI; WF-01 (team setup, forced password change, Leader role change) has no end-to-end test. | High | LocalStudioE2eTest @EnabledIfEnvironmentVariable; .github/workflows/ci.yml. |
| SR-15 | Flows | STEMotion_FE | No browser-level end-to-end tests for the main workflows; FE tests are Vitest unit/component tests. | Medium | No Playwright or Cypress configuration in STEMotion_FE. |
| SR-16 | Architecture | SDD 1.1 | The core API can call Gemini directly (embedded mode, GeminiClient) besides the AI service; the architecture diagram shows LLM access only through the AI service. | Medium | AiGateway (embedded vs remote), GeminiClient.java, AI_SERVICE_MODE. |
| SR-17 | Architecture | SDD 1.1 | Optional Redis (BullMQ) and the inactive Cloud Run staging workflow exist in the repositories but are not in the architecture diagram. | Low | STEMotion_RENDER/src/queue.ts (USE_REDIS); STEMotion_BE/.github/workflows/deploy-staging.yml. |
| SR-18 | Architecture | Code vs SDD 1.3 | Google Sign-In code (GoogleTokenVerifier, /auth/google, FE GoogleSignInButton) remains although the SDD declares no third-party sign-in. | Low | AuthController /google; STEMotion_FE/src/components/GoogleSignInButton.tsx. |
| SR-19 | Architecture | STEMotion_RENDER/src/tts.ts | The worker contains a free Google Translate TTS path (google-tts-api) that is not declared as a third-party service. | Low | tts.ts synthesizeWithFreeGoogle. |
| SR-20 | Design vs code | SDD 1.2.3 package descriptions | Worker endpoints are /render, /render-status/:jobId, DELETE /render/:jobId, /videos/:filename, /tts-preview, /sync-durations, not "/status" and "/cancel" as written. | Low | STEMotion_RENDER/src/server.ts. |
| SR-21 | Design vs code | SRS NFR-05, registration NFR Reliability | "Failed AI jobs auto-retry up to three times": LangChain providers use max_retries=2 and the core API does not retry AI calls; whether this equals "three times" depends on counting attempts or retries. | Medium | services/ai/app/providers.py; AiGateway.java. |
| SR-22 | Design vs code | SDD 1.3 Deployment environments | Staging and production are the same hosted environment; the guide requires demos on a declared staging environment. | Medium | SDD 1.3 table; no second deployment configuration. |
| SR-23 | Design vs code | SDD 3.5 sequence, SRS FR-29 / BR-11 | The sync check compares the draft revision number only; a version change between measuring and writing (submit, then request changes creates a new version at revision 0) is not detected. | Medium | NarrationToolsService.syncDurations; ScriptService.applyMeasuredDurations. |

**Five most important findings**

1. SR-14 — main workflows have no end-to-end test running in CI, and WF-01 has none at all.
2. SR-11 — the video state machine misses the RENDERING -> IN_QA transition used by the code.
3. SR-23 — duration sync can apply results measured on an earlier script version.
4. SR-22 — no separate staging environment for the Defense demo.
5. SR-01 — Planned FRs are outside the registration form; their status must be decided at Review 2.
