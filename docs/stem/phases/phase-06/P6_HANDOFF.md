# P6 — AI / library / exact-lock (T10/T11): bàn giao

**07/11/2026 — LOCAL / UNCOMMITTED / PARTIAL, CHƯA NGHIỆM THU HOẶC TỰ ĐÓNG P6.**

Đã thực thi Q01–Q10 A theo APPROVED_EXECUTION_PROMPT.md sau scope/Bond gate. AI mới lấy context từ server, gợi ý vẫn UNVERIFIED; thẻ thư viện đọc frozen publication và filter/page/count trong SQL. Giữ pipeline video, mode OFF/ADVISORY và legacy APIs. Không merge/deploy hoặc thay hồ sơ chính thức.

## 1. Kết quả cuối và giới hạn phép đếm

- **360 BE pass = 268 mandatory cũ + 55 P6 mới + 37 existing AI/paging regression bổ sung**, 0 fail/error/skip.
- **258 FE pass = 227 mandatory cũ + 31 P6 mới**, typecheck/build exit0.
- **85 AI pass = 69 cũ + 16 P6 mới** trong exact-lock venv:77/77 đúng pin,0mismatch, pip check/import pass.
- **2 original live P4/G06 pass** và **2 live P6 FE→BE→Python-fake pass**; no mock thay BE/Python transport.
- Delta source cuối: **20 BE / 10 FE files**. Đây là P6 delta trên backup, không số file P3–P5 tổng cộng.

Chi tiết suite/log ở TEST_BASELINE_P6.md và TEST_SUMMARY_P6.json. Tính lượt cuối, không cộng các lượt chạy lại; P6 FE live là các assertions được một ca orchestration BE chạy, báo riêng chứ không cộng thành một denominator chung. Mandatory 268 BE, 227 FE và 2 live P4/G06 giữ source/assertions/config/runner. Full BE439, Playwright, provider thật, MP4/pixel/audio thực và curricular/expert validation **NOT_RUN**.

## 2. Baseline, delta và bảo toàn

- BE: `C:/Users/LeTran/FPT University/2026_FALL/STEMotion/.audit/stem-phase-2026-11-05/phase-03/STEMotion_BE`; HEAD **4fd7089fa3b2c0851487625517f6fdfe2a9cc64c**, nhánh `feat/stem-p3-isolated-20261105`.
- FE: `C:/Users/LeTran/FPT University/2026_FALL/STEMotion/.audit/stem-phase-2026-11-05/phase-04/STEMotion_FE`; HEAD **4dfa65e210ba7bef38fd418943dd19e491743883**, nhánh `feat/stem-p4-isolated-20261105`.
- Input snapshot trước edit: INPUT_BASELINE.json, bao gồm tracked/untracked và output bị loại. Không checkout/pull/fetch/reset/stash/clean/re-clone hoặc lấy main làm baseline mới. Build/cache tồn tại trước P6 giữ nguyên.
- PATCH_P6_BE.diff / PATCH_P6_FE.diff và SOURCE_MANIFEST_P6.json là delta **trên commit backup**, không whole diff so main. Phân biệt P5 delta17BE/9FE, backup141BE/79FE và delta P6; không ghi công việc P3/P4/Antigravity thành mới.
- PRESERVATION_P6.json đối chiếu toàn bộ baseline B/F, old tests/migrations/locks và repo gốc. Không sửa P3 proxy/accessor fix, FeedbackService hook, G06 settings wire, P5 hash/classifier/renderer/worker/Remotion/assets hoặc evidence P3/P4/P5.
- **0 commit/push/merge/deploy, 0 shared DB access, 0 real provider calls, 0 migration mới.** Origin clone vẫn repo local gốc; lần sao lưu riêng trước không là lệnh push tiếp.

## 3. Expected / actual P6.1–P6.8

| Mục | Actual và bằng chứng | Giới hạn / phần không nhận |
|---|---|---|
| P6.1 | Venv P6 mới, lock hash trùng P0, đủ77 pinned/0mismatch, pip check/import + suite cũ69 và suite cuối; EXACT_LOCK_INVENTORY/PREFLIGHT | Chỉ local Windows/Python3.12.3; pip24 bootstrap ngoài lock được tách. Không tự đóng P0 hoặc nhận release equivalence/provider quality |
| P6.2 | New grounded project route cho generate/resegment/grade/extract/terms; active Writer/target CAS, server brief/edition/scenes, primary DRAFT instruction, Java/Python context parity, no trusted model claims; accept rechecks binding và chỉ log; in-flight change409, busy429, invalid502/timeout504 | Legacy toolbar/endpoints giữ và không tự gọi grounded; luồng grounded nằm panel Hồ sơ STEM. Không one-click apply/Create, không auto-save scene/brief/assessment. Manual edit + Save Draft cũ giữ |
| P6.3 | StemLibraryQuery dùng frozen publication sidecar; SQL predicate trước count/page/facets, stable sort, parameterized refs; card/task ngoài MP4, context role khác channel, paging headers, labels/legacy read-export-link | Không dashboard/full project clone/backfill. Metadata lịch sử thiếu trường để unknown; educationLevel chỉ từ frozen edition phù hợp, không suy grade legacy. Chưa chứng minh production scale/index plan |
| P6.4 | F09:13 DTO qua BE/FE + semantic mutation; F10:12 kinds/extraBlocks/reference negatives; F14:4version mismatch không inherit, không rewrite history | Full per-leaf/validDTO-render/visibility acceptance vẫn PARTIAL; no broad theme/decrease/audio proof/hash-upgrade bridge |
| P6.5 | CONTRACT_P6 + FIELD_MAPPING_P6; additive DTO/internal stemContext/public grounded route/paging metadata; no SQL migration | Không V93000 placeholder/production numbering. Nếu sau này cần index/table phải quay lại gate Q09, không mặc định được duyệt |
| P6.6 | Mandatory old suites + test mới/affected regression và FE real-BE/Python flow, logs tách fake-vs-real | Không full52ca PASS, không full AT01–24/ba cấp verified. CASE_COVERAGE_P6 ghi từng subcase và PARTIAL |
| P6.7 | Reuse B/F đúng backup, output/build/venv/pip-cache/runners trong `.audit/stem-p6`, localhost/disposable PG/fakes; giữ P4/P5 runner, tạo bản P6 riêng | Không env/venv/server của người dùng hoặc global dependency upgrade. P6 logs giữ local, không tự commit generated outputs |
| P6.8 | Hash/diff/preservation/cleanup/rollback và dependency ghi riêng trong P6 | P0–P6 không tự đóng; acceptance/merge/shared/release cần lệnh riêng |

## 4. Contract actual và ý nghĩa của kết quả

- `POST /api/v1/ai/scripts/{projectId}/grounded`: input version/revisions/operation; không nhận grounding blob/policy/source verification do client. Server authorize và resolve lại trước/sau model call. JSON model output schema/refs/bounds/authority bị kiểm; verdict model không tạo STEM decision.
- Internal DTO Java/Python thêm optional stemContext, Python `extra=forbid` giữ. Public legacy generate/action từ chối client stemContext. Grounded Python invalid output **không tự gọi model lần nữa**; legacy validation-retry giữ để tương thích. Test fake chứng minh single logical call, **không claim real-provider SDK retry/quality đã đo**.
- `PUT /ai/requests/{id}/decision` giữ accept/ignore log; grounded request yêu cầu binding tới đúng persisted request context và current target. Không có approve AI endpoint. Refs objectiveKeys/activityKeys trong response là **allowlist từ hồ sơ**, không chứng minh model đã đáp ứng các mục tiêu đó.
- Accept không ghi kịch bản. Writer biên tập thủ công và dùng Save Draft/Submit hiện hữu, cùng validator/seat/revision cũ. Không đưa workflow mới “accept→apply toàn script” vào P6. Manual invalid refs được covered bởi regression validator P3; AI-generated scene schema có fixture BE/FE riêng.
- Library list vẫn array; page/size trả X-Total-* và X-Page/X-Size. STEM filters AND, primary/secondary/ANY rõ; DEPARTMENT_LIBRARY là channel. Scope/field paths, alias grade và gradeLevel và legacy/null semantics ở FIELD_MAPPING_P6.md.
- Các card mới vẫn UNVERIFIED; legacy không tự suy cấp/lớp/outcome. Thẻ từ frozen snapshot không thay khi project/brief mutable đổi. Không gán người review kỹ thuật thành expert hoặc đưa metadata mutable lên clip cũ để làm đủ field.

## 5. Lỗi, giới hạn, ngoài kế hoạch

Lượt đầu pip sandbox không resolve DNS; giữ log, chạy lại cài đúng lock bằng cơ chế native được duyệt. Pytest sandbox timeout300s không summary; native cùng venv pass. FE fixture timeline P6 ban đầu có1stage, không đủ validator FE; sửa fixture mới thành2stage. Test P6 live bị type nullable brief; thêm null guard ở **test mới**, không đổi assertion/test P4/P5. Các log đầu giữ, không gộp retry thành test cuối hoặc sửa lock để né lỗi.

Source/test bổ sung có mục đích trong scope: grounded route/controller/byte filter/resolver/service; SQL read projection/card; header helper FE; new DTO/reference specimens, real transport/negative/UI tests. Contract được ghi trước phần code phụ thuộc. Không phát sinh migration gate hoặc feature ngoài T10/T11 được tự mở; dashboard vẫn dời. Chưa có bằng chứng để gọi AI/scientific curriculum validation đầy đủ dù test plumbing xanh.

Những gap P5 **giữ nguyên**: actual MP4/pixel/audio/independent bytes attestation; arbitrary geometry/cue/motion/asset/audio; safe-theme/safe-decrease; positive audio digest resolver; full13template/12block render acceptance; hash-upgrade/production retirement/mixed-epoch reconciliation. UNKNOWN/no auto-inherit vẫn áp dụng, không sửa algorithm P5 để làm test qua. Profile4/8/11/source/outcome/rubric DRAFT/UNVERIFIED; no LED/Math11/expert feedback giả.

GitGuardian: giữ đúng prompt P6 — 11 incident, 1 hash AuthService đã xác nhận false positive;10 người dùng tự kiểm tra06/11, Bond chưa có chi tiết. P6 không mở lại security task hoặc dismiss/rewrite evidence.

## 6. Cleanup, rollback và dependency

CLEANUP_VERIFICATION.json xác nhận **19 PostgreSQL starts/19 shutdowns, 0 owned process remaining, 0 process bị kill bởi bước kiểm cleanup**; kiểm theo đúng owned P6 audit/data dirs. Không dọn cache/build có trước, không kill ứng dụng người dùng. Mọi PG test đóng theo context; Python fake và Node live do test tạo được dừng tại finally/AfterAll. Evidence/log giữ local để review.

Rollback chưa thực hiện: selective delta P6 theo before/after hash tại backup4fd7089/4dfa65e, kiểm work mới trước restore; giữ nguyên P3/fix/P4/P5 và history/snapshots. Không reset/stash/clean/revert commit backup/force-push, không drop/down/repair shared. Không có migration P6 để rollback. OFF không bypass known artifact mismatch/mediaFAIL hoặc xóa decision/history.

P0–P6 vẫn PARTIAL/chưa tự đóng. Exact-lock local có evidence mới, không tự sửa báo cáo P0 thành nghiệm thu. C01/C02/BR-01/cloudFlywayOFF/V23/V24/V25+ vẫn đề xuất theo ledger cũ, không kiểm shared lại. Report/main/live khác nhau; backup không phải deployed commit. BM04 RF09/10 17/11, RF11/12/13 24/11, RF08 29/11/2026 giữ nguyên.

Đề nghị Bond/người dùng review contract actual/diff/test mapping/giới hạn. P7 hồ sơ/chuyên môn/acceptance và P8 shared/release chưa mở. **Bàn giao local rồi DỪNG; không commit/push/merge/deploy hoặc tự đóng phase.**
