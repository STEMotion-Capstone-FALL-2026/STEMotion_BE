# M-B ledger — TEST ONLY, recorded before SQL

05/11/2026. Fresh isolated embeddedPG database, no existing URL. Contract choice Q01 M-B: test range **V91000–V91009** solely `src/test/resources/db/stem-p3/`. Not production reservation; V23/V24/V25+ unchanged proposals. Prerequisites: unchanged V1–V22 + baseline auth-v2-e2e V9000/V9001 once. New test profile adds V91000 only; no duplicate auth bootstrap, no changes old files or P0 DB. No outOfOrder/repair/IF NOT EXISTS masking drift. SQL not written at time ledger initialized. Result/hash/schema evidence to be appended after actual runs.

Tables planned: stem_topics, stem_topic_versions, stem_source_editions, stem_outcomes, stem_policies, stem_briefs (typed JSON aggregate), stem_evidence, stem_settings, stem_assessments (FK existing status_histories), stem_validation_runs, stem_receipts, stem_audit. New FK types match fresh varchar36 existing users/workspaces/script_versions/scenes/status_histories; test-only migration must assert actual types and fail mismatch. Backend role privileges/RLS inspected in synthetic role tests, not claim shared IAM. Existing scene removal preserves original scene ID on evidence and nullable scene FK SET NULL; brief/version/history FKs restrict, never blind delete provenance.

## Physical mapping finalized before SQL

P3 logical catalog types use versioned discriminator rows in stem_catalog with parent/source FKs and service kind checks; TOPIC/TOPIC_VERSION/SOURCE_EDITION/OUTCOME/PROFILE/POLICY/RUBRIC are distinct logical kinds. Brief context/tasks/objectives/contributions use typed DTO JSONB with CAS; evidence separate. Nine tables: stem_catalog, stem_briefs, stem_evidence, stem_settings, stem_assessments, stem_validation_runs, stem_validation_limits, stem_receipts, stem_audit. Earlier planned names superseded before SQL. JDBC repositories do not add unconditional JPA schema expectations to legacy tests; feature enabled only by isolated test profile. No production config modified.

## Actual — 05/11/2026

- `p3-handoff-regression-native.log`: PostgreSQL16.2 mới,25 migrations thành công: V1–V22 +9000/9001 đúng một lần +91000; Hibernate validate baseline thành công. Không có repair/outOfOrder hoặc restore dump.
- `MIGRATION_INVENTORY.json`: hash25 file,22 production resources và2 auth fixtures giữ nguyên so với input,1 migration mới chỉ trong test location. Production candidate23/24/25+ chưa reservation.
- `SCHEMA_TEST_MANIFEST.json`: expected/executed testDDL và observed assertions, **không phải shared schema dump**. Nine-table physical mapping, varchar36 prerequisite assertions, real FK/trigger checks, RLS9table flags, explicit client grants vẫn không cho đọc/ghi qua policy. Chưa chứng minh backend IAM/grants trên shared.
- Test `testMigrationRejectsWrongPrerequisiteType`: temp schema mismatch bị từ chối trước DDL; public test schema không bị đổi. `copyMapWrongRollsBackNewVersion` và `copyFailureRollsBackWorkflowAndKeepsOriginalInReview` chứng minh rollback transaction, không phải rehearsal shared.
- `CLEANUP_VERIFICATION.json`: kiểm tra25 data directories test đã ghi trong log, không còn PostgreSQL thuộc các directory này. Không dừng app/user server, không xóa evidence hoặc DB P0.
- Cloud Flyway OFF, C01/C02 và đường shared bridge vẫn mở. Không SQL/DDL production, không rerun cutover, không promote fixtures, không sửa history/checksum cũ.
