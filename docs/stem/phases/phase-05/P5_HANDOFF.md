# P5 — Bàn giao Nội dung đến artifact (T09)

**06/11/2026, Asia/Saigon — LOCAL / UNCOMMITTED / PARTIAL, CHƯA NGHIỆM THU HOẶC TỰ ĐÓNG P5.**

Đã thực thi Chặng 2 sau người dùng duyệt 3 gate. Bổ sung versioned fingerprints, immutable snapshots, gate cả FeedbackService/PublishService, callback/job binding, frozen-input QA và snapshot-backed library metadata; FE tối thiểu status/origin/QA/legacy acknowledgement. Pipeline video hiện hữu giữ, chưa bảo đảm chuyên môn hoặc MP4 thực tế. Phạm vi phần safe-proof còn giới hạn rõ, không nhận full STEM certification.

## 1. Kết quả kiểm thử cuối

- **268 BE pass**, 0 fail/error/skip, 19 suites: **113 P5 mới = 92 pure + 21 full-context HTTP/PG**; **41 mandatory = 38 P3 + 3 blocker**; 114 existing regression vùng ảnh hưởng. Không full 439 BE hoặc cộng 38 hai lần.
- **227 FE pass = 216 cũ + 11 P5 mới**, 40 files; typecheck/build exit 0, output/cache riêng trong BE audit.
- **2 live P4/G06 nguyên bản pass** qua BE P5 clone/fullSpring/JWT/embeddedPG26. P4 source/test/config/runner không sửa; original wire baseRevision và stale409 được kiểm lại.
- Log/test details ở TEST_BASELINE_P5.md; 52 case + AT21 riêng ở CASE_COVERAGE_P5.md. Không cộng retries/runs trước vào số cuối. Projection specimens không là full renderer acceptance.

## 2. Baseline / commit / bảo toàn

| Repo | Actual |
|---|---|
| B | `C:/Users/LeTran/FPT University/2026_FALL/STEMotion/.audit/stem-phase-2026-11-05/phase-03/STEMotion_BE`; HEAD f6c6f0bdcdcc6bbaf1f1246f86678d3249dea8e1, branch feat/stem-p3-isolated-20261105; **0 commit mới** |
| F | `C:/Users/LeTran/FPT University/2026_FALL/STEMotion/.audit/stem-phase-2026-11-05/phase-04/STEMotion_FE`; HEAD 9fe67cf7801b3e971b3c852c80f68214fd13bba7, branch feat/stem-p4-isolated-20261105; **0 commit mới** |

PRESERVATION_P5.json: kiểm 757 BE / 319 FE baseline, chỉ 10 BE + 8 FE existing production files được duyệt đổi; 7 new BE(source/test/SQL), 1 new FE test. Patch 17 BE / 9 FE là **delta trên P3+fix/P4 uncommitted**, không whole diff so main và không ghi công P4/Antigravity thành mới. Reconstruct before-content từ PRE_P5_SOURCE.patch+HEAD và xác minh byte hashes trước khi tạo PATCH_P5_BE.diff/PATCH_P5_FE.diff. SOURCE_MANIFEST_P5.json có before/after SHA256 và absolute paths.

Original BE 695 và FE 201 known files giữ hash/HEAD so preservation trước; assets/.gitignore/localwork gốc không bị ghi. Old 38+3 BE tests, P4 tests/config/runner, old migrations/history/checksums và P3/P4 docs/log giữ bytes. Không reset/stash/clean/checkout/pull/fetch/merge/push/deploy hoặc sửa worker/Remotion/package/lock/toolchain. G06 tên wire baseRevision cùng counter workspace không đổi.

## 3. Expected / actual P5.1–P5.9

| Mục | Actual / file trọng tâm | Giới hạn |
|---|---|---|
| P5.1 | StemFingerprint: 3 projection/domain/version/NFC/key/number/ordered/set canonical; semantic/contract vs actual target; 92 pure tests | No rawreceipt digest reuse. Safe timing auto-proof chỉ static defaults; arbitrary dynamic cases UNKNOWN |
| P5.2 | Mở rộng **minimal hook P3 +13 dòng đã có** trong FeedbackService; common checks before CLIP/COMPOSITION, exact decision/assessment link, send-back giữ | Không endpoint approve riêng, scientificmissing vẫnadvisoryOFF/ADV; old activeReviewer rule giữ |
| P5.3 | StemArtifactService +2 sidecar tables: SUBMISSION→APPROVAL→RENDER_INPUT→CLIP/PUBLICATION; frozeninput/nonce/versions/metadata; callback/retry terminal/identity | Fake manifest correlation only; noMP4-byte proof; missingmanifestUNKNOWN |
| P5.4 | PublishService independently rechecks current job/clip/input/review, QA; atomicPublication+Record+snapshot+receipt; YouTube503 | Legacyack cannot bypass knownmismatch; no VERIFIED label/history rewrite; historical selection/mixedflag rollout not added |
| P5.5 |13SceneTypes/12BlockKinds +extraBlocks projected; unclassifiedraw retained and no auto-inherit | No block13/table; full renderer/visibility proof NOT_RUN; full F09/F10 PARTIAL |
| P5.6 | Existing QA thresholds unchanged; frozen scenes/jobfps and evaluatedAgainst; FE showsQA separate fromSTEM | No new media thresholds/scientificQA, no realaudio/pixel tests |
| P5.7 | Approved **TEST_ONLY V92000** at src/test/resources/db/stem-p5/, only2newtables; actual26migrationP5contexts; old25 regressioncontexts retained | No oldschemaalter/checksum/repair; noV23production reservation/sharedpreflight |
| P5.8 |268 BE / 227 FE / 2 live, negative/concurrent/retry/legacy/snapshot tests, static checks; counts/logs per suite | Full439, browser/MP4/provider/exact-lock NOT_RUN; cases PARTIAL not converted toPASS |
| P5.9 | B+F clones reused, sanitized ownlocalhost/DB/cache/build/log; cleanup12 owned PG dirs,0 owned process remaining | No shared/server/người dùng app, no teacher contact |

## 4. Contract / file map / debt

CONTRACT_ACTUAL_P5.md là actual; Chặng 1 files giữ design history. New GET stem-artifact-status trả current technical projection; QA thêm evaluatedAgainst; publish thêm optionalack/expectation/key; callback thêm optionalartifactManifest và constructors cũ giữ; library DTO thêm label/snapshotref. P3StemDTO/settings wire không sửa. New filter bounds400/details, existing403/404/409/429 RetryAfter/YouTube503 giữ, không422.

FE 8 existing files: types/stem.ts; stemClient.ts; reviewService.ts; libraryService.ts; StemReadOnlySummary.tsx; ProducerStudio.tsx; **publicationService.ts/PublishModal.tsx theo gate bổ sung**. New test duy nhất StemArtifact.p5.test.tsx. Không Create/redesign/full librarycards/newFEproductionfile. FE actual endpoint map/notes ở F/docs/stem/phases/phase-05/P5_HANDOFF.md.

## 5. Lỗi, PARTIAL, ngoài kế hoạch

Các lỗi đã sửa giữ log: sandbox loopbackPermissionDenied; firstnewtestHTTPstatus kỳ vọng200 sai với202/201baseline; new status endpoint detached-owner lazy500; accidentalPythonencoding làm sai nhãn retry (khôi phục UTF-8original, oldtest giữ); FE sandbox240stimeout trước summary. Lượt cuối pass; không che bằng mock/skip/assertion cũ hoặc cấu hình suppression. Không auto-review rejection còn chặn.

Remaining: arbitrary geometry/cue/motion/asset/audio proof, broad safe-theme/safe-decrease inheritance, independent MP4checksum/realrenderer, positive audio digest resolver, full13template/12block validDTO-render acceptance, future hash-upgrade/production retirement/reconciliation. Những input chưa proof giữUNKNOWN/no auto-inheritance; không tự mở worker/P6/P8 để lấp. Nativeexpert/GDPT/curriculum correctness vẫn chưa xác minh.

Không feature ngoài scope. P5newprotocol helper/filter/tests/orchestration/doc/hash nằm approvedclone; explicit 2 FE files thêm theo người dùng duyệt. Contractactual thu hẹp current-only status selectors, supported proof subset và explicit actual bounds; không giả optionalproposal đã implemented. Không thay nguồn/Word/BM/diagram để hợp thức hóa code.

## 6. Cleanup / rollback / dependency

CLEANUP_LOG_INVENTORY.json: **12 postmaster starts / 12 shutdowns**, mọi DB directory từ P5 runs có logclose. CLEANUP_PROCESS_CHECK.json đọc current Java/Node/Postgres theo owned audit/data dirs: 0 remaining, 0 killed. Live serverexit 0/OWNED_BACKEND_CLOSED. Không đụng process/app người dùng hoặc xóa baseline/evidence.

Rollback chưa thực hiện: chỉselective deltaP5 theo before/afterhash/patch, giữ nguyên P3/fix/P4uncommitted. Không reset/stash/clean/down/drop/repair shared/history. OFF chỉ STEM/readiness/labelprojection; không bypass wrongartifact/mediaFAIL hoặc rewrite capturedhistory. DefaultP5 featureoff ngoài test, không productionconfigrollout.

P0/P1/P2/P3/P4/P5 vẫn **PARTIAL/chưa tự đóng**. P0AIexact-lock69contractenvmismatch41/77không được gọi xong. Threebrief4/8/11/source/outcome/profile/rubric DRAFT/UNVERIFIED; G-SOURCE/CURRENCY/TECH/EXPERT/MAPPING còn mở, noLED/Math11decision/fakefeedback. C01/C02/BR-01/cloudFlywayOFF/V23V24V25+candidate giữ. Report4BE7f207bb, mainf6c6f0b, livef6c6f0b(P0quan sát) không gộp; không kiểm livelại. BM04 RF09/10 **17/11/2026**, RF11/12/13 **24/11/2026**, RF08 **29/11/2026** không gia hạn. UC/FRcandidate không cấp chính thức.

Đề nghị Bond/người dùng review patch 17 BE / 9 FE, contractactual, tests và supported-proof limits; nghiệm thu/merge/phase sau cần lệnh riêng. P6AI/library/exact-lock, P7hồsơ/FR38/Word/BM/diagram/T12a, P8shared/release không được tự mở. **Bàn giao local rồi DỪNG.**
