# P5 — Bàn giao Chặng1, chờ gate migration

05/11/2026, Asia/Saigon. **CHẶNG1 ĐÃ SOẠN CONTRACT/PROPOSAL; P5 IMPLEMENTATION NOT_RUN; P0–P5 CHƯA TỰ ĐÓNG. DỪNG TRƯỚC CHẶNG2.**

## Kết quả / expected-actual

| Bước được duyệt ở prompt §4 Chặng1 | Actual | Evidence / giới hạn |
|---|---|---|
| Chụp baseline B+F trước code | HEAD/branch/status including untracked, hashes, tracked+untracked source patch đã ghi trước docs mới | B/F INPUT_BASELINE.json, PRE_P5_SOURCE.patch. B757 files = prior756 + regression mới fix đã duyệt, overlay2production fix hash; F319 files giữ. Không có source owner mới chưa rõ. |
| Contract và field matrix | Ba projection versioned, canonical bytes, 13 scene/12 block+extraBlocks, unknown fail closed, errors/limits/idempotency, endpoint/FE consumption proposals | CONTRACT_DELTA_P5.md, FIELD_MATRIX_P5.md; API/DTO mới chưa tồn tại. P3 wire/G06/source/assertions giữ. |
| Canonical vectors | 29 specimen vectors, valid canonical literals+expected SHA256, invalid input400 expected | CANONICAL_VECTORS.json. Chỉ SHA calculation cho fixture tài liệu, **không projector implementation/test suite**, không 29test PASS. |
| Persistence survey / proposal | Existing JSON không thay submission/approval/compatibility immutable lineage; đề nghị2sidecar tables | MIGRATION_PROPOSAL.md. Không DDL/SQL file, không DB/preflight shared. |
| Number/location gate | **Đề nghị V92000–V92009 TEST_ONLY; dùng V92000 tại src/test/resources/db/stem-p5/** | Chưa reserve/cấp số, chưa tạo file .sql. Fresh expected26 chỉ sau apply thật, hiện NOT_RUN. |
| Callback proposal | Optional BE manifest + server binding/fake test; missing legacy manifest UNKNOWN/explicit unverified route; no worker patch | CALLBACK_MANIFEST_PROPOSAL.md. Hash echo/callback key/digest assertion không MP4 byte-proof. |
| Mapping / FE method-endpoint | Kế hoạch52case riêng + AT21, actual P5 NOT_RUN; FE read/transition endpoint map | CASE_COVERAGE_STAGE1.md; F/docs/stem/phases/phase-05/FE_CONSUMER_PLAN.md. F10 count đã user xác nhận13scene/12block; no invented block13. |

## Các quyết định đã khóa, baseline không bị nâng mức

APPROVED_EXECUTION_PROMPT.md giữ nguyên văn prompt đã duyệt. GAP-01: FeedbackService có minimal hook P3 +13 dòng; mở rộng hook, không ghi service untouched. GAP-05:13SceneTypes/12BlockKinds. Q01 B/Q02 A/Q03 A/Q04 A/Q05 C/Q06 A conditional/Q07 A/Q08 A/Q09 A/Q10 A/Q11 A/Q12 A giữ nguyên. Chưa implementation thì không claim gate/snapshot/AT21 đã hỗ trợ.

41BEpass(38P3+3fix),216FEpass,2liveP4/G06pass **kế thừa**, không chạy lại trong Chặng1. Historical P4 pre-fix2FAIL không sửa. P0 exact-lock NOT_RUN/41trên77 lệch thiếu,69contractenvmismatch không thay exact-lock. Ba brief4/8/11 DRAFT/UNVERIFIED; modeOFF/ADVISORY, noSTRICT/activate/verified; C01/C02/BR-01/cloudFlywayOFF/V23V24V25+ candidate giữ. BM04 RF09/10 17/11; RF11/12/13 24/11; RF08YouTube29/11/2026 không gia hạn. Report/main/live ba mốc không gộp; liveBEf6c6f0b kế thừa P0, không đọc dashboard lại.

## Gate cần người dùng xác nhận trước bước phụ thuộc

1. **Migration:** đồng ý2sidecar tables + TEST_ONLY rangeV92000–V92009, file V92000__stem_p5_artifact_snapshots_test_only.sql trong `src/test/resources/db/stem-p5/`, giữ nguyên mọi migration/P3 schema cũ? Đây là confirmation bắt buộc do prompt §4 Chặng1.2, Q06 chưa cấp số.
2. **Callback:** đồng ýphương án A ở CALLBACK_MANIFEST_PROPOSAL (optional BE manifest, fake correlation only, legacy missing-manifestUNKNOWN/explicit unverified, supplied mismatchhard409), không sửa worker? Prompt §4 Chặng1.3 yêu cầu trình cùng gate. Nếu bắt true digest/worker-required manifest cần scope riêng.
3. **Contract additive/FE scope clarification:** proposed read status surface tách P3 DTO + publish expectation/key/explicit legacy ack. Actual publisher **publicationService.ts/PublishModal.tsx** ngoài danh sách §4.8: đề nghị thêm đúng2file cho request/display/ack tối thiểu và tests P5 mới, không sửa P4 test source/config/runner. Chưa duyệt không sửa hoặc dời publisher sang file khác để lách allowlist; UI flow phụ thuộc giữ blocker. Đây là phát hiện read-only, chưa implementation ngoài scope.

## Test / logs / ngoài kế hoạch

**Tests/build/app/DB/migration/render/provider: NOT_RUN toàn bộ Chặng1** theo gate. Không tạo test log giả hoặc ghi PASS. Hash calculations/document structure checks là kiểm tra artifact tài liệu, không software suite. Không server/process/DB P5 được tạo nên cleanup runtime N/A; không kill app người dùng. Git chỉ read-only inventory/diff, commits0, pushes0, merges0, deployments0. Không checkout/fetch/pull/stash/reset/clean hoặc package/lock/dependency/toolchain changes.

Ngoài kế hoạch thực thi: không feature/code thay đổi. Phát hiện publisheractual ngoài allowlist là scope gap được trình, chưa tự mở. Supporting docs/hash snapshots/patch baseline thuộc Chặng1, không ghi vào repo gốc hoặc sửa P3/P4 evidence. FE docs có baseline và consumer plan riêng, không copy code P3 sang FE.

## Rollback / dependency / dừng

Chưa có code/SQL/runtime cần rollback. Giữ docs/baseline để review. Nếu về sau revert P5, chỉ selective delta theo captured baseline; không reset/stash/clean P3/fix/P4 uncommitted, không down/drop/repair/shared/history. OFF không bypass wrong artifact/media hardFAIL và không xóa snapshot/history. Chặng2 chỉ sau gate được xác nhận; sau đó chạy mandatory38+3/216/2live trên source cuối (không cộng38hai lần), thêm cases kỹ thuật B01/B02 tách và fake callback; no MP4/browser/provider/exact-lock.

P6 AI/full library/exact-lock, P7 hồ sơ/FR38/Word/BM/diagram/T12a, P8 shared/release/production reservation còn dependency riêng. Không tự mở chúng, không tự nghiệm thu chuyên môn hoặc đóng P0–P5. **DỪNG chờ trả lời gate.**
