# P5 — Migration ledger TEST_ONLY, actual 06/11/2026

User đã duyệt gate số/location: **V92000–V92009 TEST_ONLY**, dùng đúng **V92000** ở `src/test/resources/db/stem-p5/V92000__stem_p5_artifact_snapshots_test_only.sql`, hai bảng sidecar. MIGRATION_PROPOSAL.md giữ lịch sử trước SQL; không production reservation.

| Route | Expected / actual |
|---|---|
| P5 owned fresh DB | V1–22 + auth fixtures V9000/V9001 một lần + unchanged P3 V91000 + P5 V92000 = **26**, actual asserted ở StemArtifactP5Test và live P4 DB audit. Hibernate baseline validate pass. |
| P3/fix regression fresh contexts | Original test source/config vẫn25migrations, không bị sửa để đòi P5 schema; 38P3 +3fix pass lại. Không lấy26 làm số mọi context. |
| Shared/production | NOT_RUN, no connection/release/SQL/reservation; C01/C02/BR-01/cloudFlywayOFF và V23/V24/V25+ candidate giữ nguyên. |

Chỉ thêm stem_p5_snapshots và stem_p5_compatibility; không cột/tables core hoặc P3 bị alter. Parent type assertions trước DDL: legacy varchar36 vs UUID clips/publications/compositions; drift test tạo namespace tổng hợp sai users.id UUID, migration fail trước bảng mới, public migration history vẫn26. Không IF NOT EXISTS che drift, placeholder/repair/outOfOrder hoặc rerun auth cutover.

Immutability triggers chỉ hai bảng mới reject UPDATE/DELETE; stage/ref/unique/check/FK/index boundaries; RLS enabled/revoke client roles. Test role synthetic có explicit SELECT grant vẫn thấy0rows vì không policy; không dùng owner bypass làm chứng minh RLS. FK/history restrict, không cascade mất provenance. Payload/frozen input/proof bounded, hash versions/lineage nonmutable.

Nguồn/hash: SOURCE_MANIFEST_P5.json; PRESERVATION_P5.json xác nhận V1–22/V9000/V9001/V91000 và old test fixtures/checksums không đổi. Test evidence: be-handoff/maven.log + XML, P5 real HTTP suite21tests; p4-live-final/DB_AUDIT.json. Đây là schema assertions trên disposable DB, **không shared dump hoặc rehearsal**.

Rollback: dừng owned runtime/profile, giữ schema/history/evidence; no down/drop/repair shared. Existing DBs của test đã đóng; OFF không xóa snapshot/history hoặc bỏ artifact integrity/media hard FAIL. Promotion thành production migration cần phạm vi/ledger/preflight riêng, không copy V92000 sang production.
