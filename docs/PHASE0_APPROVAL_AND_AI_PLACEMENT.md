# Approved implementation baseline — 2026-10-10

The user approved the Phase 0 design draft dated 2026-10-10. Historical Bond/Muse analysis is not implementation evidence. The diagrams and mapping are in the shared workspace `Diagram/Phase0`; the approval below supersedes pending-decision annotations in that draft.

1. Retain workspaces, workspace_members, status_histories, dual script/video states and derived pipeline status.
2. ReviewDecision uses append-only status_histories with explicit event_type, target_type, target_id, decision and reason. No JSON packed into note. Record review events even when aggregate pipeline status does not change. Stabilize scene IDs before review binding.
3. Publications: first unique(project_id, platform) with legacy publish_records adapter; a separate later migration changes uniqueness to (clip_id, platform). INTERNAL_LIBRARY requires a real flow. Preserve LMS embed API compatibility. Current baseline code rejects LMS and YouTube, so an operational LMS flow must be restored/verified rather than assumed to work already.
4. cost_tokens means token count. Persist input/output usage when available. Money remains null unless actual pricing/data supports it. Unknown cost must not become zero.
5. No public registration. Administrators provision accounts. Existing accounts are preserved. Google OAuth must not silently create new accounts outside provisioning policy.

## AI service placement

Keep FastAPI/LangChain/Gemini in `STEMotion_BE/services/ai` for this rollout. This is an independently built/deployed service inside the BE repository, not a Python process hosted inside the Java application.

- Python tests/build run separately from Maven. Changes under services/ai trigger AI CI; gateway/contract changes require both test suites.
- Build the AI image from services/ai/Dockerfile with services/ai as context. Use its own private service URL, key and Gemini environment. Do not put Gemini credentials in the frontend.
- Deploy/version the AI service before switching BE to remote mode; report each runtime image/commit separately.
- Move to a separate STEMotion_AI repo only if repository ownership/release requirements justify it, with a reviewed migration of CI and deployment settings. No new repository is required now.
- Remove embedded GeminiClient only after specialized remote functionality, audit logging, failure/retry behavior and rollout checks pass.

## Phase ordering

P0 credential rotation and account security precede schema rollout. Supabase password rotation and Render DB_PASSWORD update are being performed by the user; code cleanup does not prove rotation has happened. Phase 1 must not be reported started until that operational step is confirmed. Each later phase gets separate tested commits and migration rollback evidence.
