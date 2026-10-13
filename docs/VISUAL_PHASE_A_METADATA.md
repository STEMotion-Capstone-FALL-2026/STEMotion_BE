# Visual Phase A / metadata and validation — 2026-10-11

Implementation follows the approved extension of `remotion_templates`, FE-owned registry and seconds-to-frames adapter. General pack is `0.1.0`; production AI service/render reliability priorities remain unchanged.

V14 adds nullable `subject`, `asset_pack` and JSONB `slots`; existing rows and API fields are retained. Old update payloads preserve director metadata. Admin metadata rejects unknown subjects, unversioned packs and non-object slots. Manual rollback in `docs/rollback/V14__director_template_metadata.down.sql` refuses populated metadata rather than discarding it.

The canonical registry lives in FE `src/remotion/director/registry.json`. BE `src/main/resources/director-registry.json` is a build-time mirror. `DirectorPlanValidator` checks IDs, allowed slots, duplicate slots, bounded props, narration and seconds. `ScenePropsValidator` applies it to additive `visualPlan` inside existing ILLUSTRATED_EXPLAINER props. AI has no new plan generation endpoint yet. FE render verification compares both registries; keep them byte-equivalent when changing packs. Full catalogue distribution/API and provider JSON Schema export remain Phase D.

Validation: V14 up/down/reapply, invalid JSON shape, rollback guard and legacy row preservation are included in `src/test/database/director-migration-test.mjs`. Java tests cover unknown/executable props, seconds-only input and metadata compatibility. Existing tests remain part of regression.

**Local only:** V14 has not been applied to Supabase; production schema remains v13. No credentials were read/rotated for this phase. No production backup was taken because production was untouched. Before rollout, take and verify a fresh encrypted backup with the existing backup tool, test on the target PostgreSQL version, apply V14 with Flyway, then deploy the new entity mapping. Never deploy this BE mapping before V14.

Visual demos/tests/commands and remaining work: see FE `docs/VISUAL_PHASE_A.md`. Local video snapshots do not prove real TTS, remote worker, callback reliability or publishing end-to-end.
