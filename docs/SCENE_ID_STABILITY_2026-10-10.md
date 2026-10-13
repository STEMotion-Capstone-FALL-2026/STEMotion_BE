# Scene identity prerequisite — 2026-10-10

Approved mapping: Scene/scenes and Add Inline Comments/feedback_comments. This prerequisite precedes ReviewDecision binding.

## Changes

- SceneRequest.id is an explicit DTO field rather than a template prop. Updates retain existing managed rows/IDs and remove only omitted rows. Duplicate IDs and UUIDs outside the current script version are rejected.
- Legacy props.id aliases can identify existing rows; response props no longer shadow canonical id/type/title/narration/duration/order fields. An ID-less legacy client may edit in place with an unchanged scene count; adding/removing scenes requires identities.
- New scenes use temporary non-UUID client IDs and receive generated server UUIDs. New script versions intentionally receive new scene IDs; this does not mutate historical versions.
- Scene reconciliation acquires row write locks. Deletion checks all feedback, including resolved feedback, and refuses to delete any referenced scene, as explicitly chosen by the user. This is not soft deletion or snapshot migration.
- FE save queue translates temporary IDs in subsequent queued saves, and App reconciles identities without overwriting newer local text. Add/delete errors propagate; no local-success fallback. Move/reorder/duplicate/duration actions now persist; add focuses the persisted scene ID.

## Verification

- Full BE regression before the final locking adjustment: 157 tests, 153 passed, four opt-in DB tests skipped. Final scene mapper/identity/immutability tests and package passed after locking was added.
- Real Supabase opt-in ScriptFlow tests: two passed; both transactional fixture runs rolled back. The scene test edits a scene with feedback, flushes/reloads it, verifies the same scene ID and two comments remain, and rejects deletion. The second test checks invitation acceptance. The separate read-only schema opt-in was not enabled in that invocation; application startup still initialized and validated entity/repository mappings.
- FE: 48 tests passed; production build passed. Queue tests include first-save ID reconciliation and rejected feedback deletion notification.
- Production probe after credential rotation: health 200, deliberately invalid login returns normal 400 Invalid email or password (DB read path), and public register returns 401 after redeploy. These behaviors do not prove an exact runtime commit SHA.
- No migration, schema change or real account/content mutation. Migration up/down: not applicable. No secret rotated/read into tool output; local ignored .env was used only for opt-in DB test authentication. Runtime passwords were rotated by the user.

## Remaining

Cross-browser revision concurrency is not fully solved by a per-browser save queue. ReviewDecision needs version/artifact binding and a project-level concurrency check. Existing feedback lost by earlier destructive saves cannot be recovered without historical backups. Clip entity is explicitly approved for Phase 1, not deferred to the render phase. AI service remains in services/ai. Schema rollout must precede deployment of any JPA entities depending on new tables/columns.
