# Audit remediation phase 2 — 10 October 2026

## Delivered changes

- Registration no longer activates workspace invitations by matching email.
  Invitation endpoints issue a random one-use token, persist only its SHA-256 hash,
  expire it after seven days, and require an active account with matching email.
  Acceptance locks the invitation row. Tokens are returned only to the workspace
  administrator creating/reissuing an invitation, never in member listings.
  Existing pending invitations must be reissued. Delivery is manual and private;
  no email integration is claimed.
- IN_REVIEW and APPROVED scripts reject draft updates. User detail endpoints
  permit the account itself or a global administrator.
- Completed/failed render jobs ignore late/repeated callbacks. Completion requires
  a video URL; progress cannot decrease. This is a terminal-state fix, not yet a
  complete attempt/outbox protocol.
- YouTube/LMS publish requests return PUBLISH_TARGET_UNAVAILABLE before writing
  publication records. OAuth upload and a real embed route are still missing.
- AI execution can run in a separate FastAPI/LangChain process with Gemini.
  `services/ai` has its own Dockerfile, dependency lock and CI tests. FE's public
  API contract stays on BE. `AI_SERVICE_MODE=remote` uses the private service key;
  a failed remote call does not fall back to embedded Gemini. The provider model
  returned to FE comes from the service response. Default remains `embedded`
  until the separate service has been deployed and verified.

## FE/worker changes accompanying this phase

- Writer flushes scenes and calls submit-review; reviewer calls separate script
  and video decision endpoints. API failures no longer manufacture approval,
  comments or successful resolution. Workspace selection now uses actual server
  workspaces. Invitation modal issues an actual one-use link, accepted after login.
- Render timer simulation is removed. Producer requests jobs through BE and polls
  BE, then reloads server state. Download opens the existing server artifact; it
  does not bypass BE by calling a worker on localhost or rerender a QA-approved clip.
  Fake YouTube URLs/embed codes are removed from the production modal. Browser
  speech preview is explicitly labeled and does not change scene duration.
- Worker compute/status/TTS/sync endpoints require a constant-time shared-key
  check; empty configuration rejects requests. Render filenames and envelope are
  validated; callback destination must equal API_CALLBACK_URL. Callback HTTP calls
  use the configured key only and a 10-second timeout.

## Database and verification

V9 adds nullable invitation hash/expiry columns and a partial unique index.
It was applied to the existing Supabase database after successful Flyway validation
and an encrypted scoped backup of workspace_members/schema metadata/history.
Post-migration Flyway validation passed. Backup lives under ignored `.local-backups`
with owner-only ACL; it is not a full database backup.

- BE package: 139 tests, 135 passed and 4 opt-in tests skipped; no failures.
- Supabase opt-in verification: 3 passed (read-only schema validation plus script
  round trip and invitation acceptance/replay). Fixture writes were rolled back.
- FE: 45 tests passed and production build passed. Bundle-size warning remains.
- Worker: TypeScript build and 2 security contract tests passed.
- AI: 4 HTTP/schema tests passed. A live, tiny FastAPI/LangChain Gemini request
  returned HTTP 200 with structured JSON and model gemini-3.5-flash-lite.
  These tests do not prove generated full scripts or rendered MP4 correctness.

## Rollout

1. V9 is already applied. Deploy BE from `deploy/render-staging` first.
2. Deploy FE main. New invitation endpoints require this BE release.
3. Deploy worker only after configuring shared key, fixed callback URL, Redis,
   storage and pinned FE composition source. Keep BE worker URL/key empty until then.
4. Deploy `services/ai` as a separate Docker service. Set Gemini key/model there.
   Test it, then set BE AI_SERVICE_MODE=remote, AI_SERVICE_URL and AI_SERVICE_KEY.
   Existing embedded mode remains usable before this switch.

## Still open — do not claim end-to-end architecture compliance

- Separate CompositionVersion and immutable ClipVersion/render payload snapshots.
- Review revision binding/concurrent-update checks; stable scene IDs and autosave
  reconciliation; all reorder/duplicate actions persisted rather than local state.
- After-commit dispatch/outbox, unique render attempts, durable callback retry,
  worker retry/cancellation correctness and recovery after restart.
- Full AI scene-schema validation and removal of scientifically fabricated FE
  default content; AI prompt ownership currently remains in BE.
- QA on actual MP4, feedback scoped to a clip version, artifact storage lifecycle,
  narration voice/FPS/timing alignment, Whisper captions, YouTube OAuth and LMS.
- Public storage access policy, library visibility, full security/NFR and MP4
  contract gates. Worker and AI deployment are not confirmed by source tests.

The production Render/Vercel dashboard environments and running release SHAs were
not directly verified in this phase. Pushed source is not proof of a live release.
