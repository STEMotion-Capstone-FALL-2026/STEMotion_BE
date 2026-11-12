# Prompt thực thi P6 cho Codex — AI / library / exact-lock (T10/T11)

Bạn là Codex, agent backend của STEMotion STEM track. Thẻ phạm vi P6 đã được người dùng duyệt ngày 06/11/2026 với các lựa chọn dưới đây, và Bond đã kiểm lại bảng hash input §9 (39 input, SHA-256, placeholder đã hết). Đây là prompt thực thi duy nhất cho P6. Thực thi đúng phạm vi đã duyệt, rồi bàn giao và DỪNG. Không tự mở P7/P8, không tự đóng P0–P6, không merge/push/deploy.

## 1. Quyết định đã duyệt — khóa nguyên văn, không tự thay

- **P6-Q01 A — Exact-lock làm cổng P6.0:** chạy exact-lock trước validation AI; library/contract độc lập chỉ tiếp theo điều kiện P6.1. Lock chưa đạt thì AI01/AI02 runtime giữ BLOCKED/PARTIAL, không gọi validation AI đạt.
- **P6-Q02 A — AI grounding:** generate + analysis action hiện hữu ở Writer trên project đã có brief, server-resolved context/profile, advisory validation; approaches/storyboard/metaphor/Create dời. Cần chọn exact operation, response refs và accept/save semantics trước code (xem gate §4).
- **P6-Q03 A — Provider fake/offline:** real local Java/Python transport, model fake ở ranh giới provider; không cần key thật, không chứng minh chất lượng provider. **Codex không tạo/cấp API key; người dùng cấp riêng** nếu có lệnh gọi thật sau này. Không tìm key trong env người dùng, không ghi key/prompt nhạy cảm vào evidence.
- **P6-Q04 A — Library:** frozen learning card + server filters/paging/count + read/export/link reuse, legacy label; dashboard/full project clone dời quyết riêng, T11 vẫn PARTIAL phần dashboard.
- **P6-Q05 A — Gap P5:** giữ media proof UNKNOWN; nhận F09/F10 validDTO/reference specimens và F14 negative version regression, không nhận full render/audio/upgrade bridge. Bảng P6.4 của thẻ áp từng gap.
- **P6-Q06 A — Môi trường:** reuse hai clone ở commit backup (BE `4fd7089`, FE `4dfa65e`), giữ output dirty, chụp baseline mới trước edit; không chờ merge.
- **P6-Q07 A — STRICT/activate:** giữ OFF/ADVISORY, DRAFT/UNVERIFIED; chỉ negative evaluator regression, không activate, không tạo VERIFIED fixture.
- **P6-Q08 A — Mức test:** exact-lock + local AI fake transport/accept và library integration, mandatory 268 BE / 227 FE / 2 live + test mới, F09/F10 subset nếu Q05; không provider/MP4/browser thật.
- **P6-Q09 A — Migration/contract:** ưu tiên không migration, additive coordinated DTO/read query trên frozen P5 payload; khảo sát đủ mới code. Nếu khảo sát buộc cần persistence/index thì DỪNG ở gate §4, trình schema/DDL proposal riêng (candidate V93000–V93009) và chờ xác nhận số/location trước khi viết SQL.
- **P6-Q10 A — Chuyên môn:** kỹ thuật dùng DRAFT/UNVERIFIED, ghi câu hỏi và dependency chuyên môn cho P7; không contact giáo viên/chuyên gia, không tạo feedback giả, không nâng status vì test pass.

## 2. Baseline kế thừa — ghi đúng, không nâng mức

- P0–P5 đều PARTIAL, chưa phase nào đóng, chưa merge vào main.
- BE clone: `.audit/stem-phase-2026-11-05/phase-03/STEMotion_BE`, HEAD backup `4fd7089` (parent `f6c6f0b`), nhánh `feat/stem-p3-isolated-20261105`. FE clone: `.audit/stem-phase-2026-11-05/phase-04/STEMotion_FE`, HEAD backup `4dfa65e` (parent `9fe67cf`), nhánh `feat/stem-p4-isolated-20261105`.
- P5 bàn giao 06/11: **268 BE pass** (113 P5 mới + 41 bắt buộc + 114 regression), **227 FE pass** (216 cũ + 11 P5 mới), **2 live P4/G06 pass**; migration TEST_ONLY V91000 (P3) và V92000 (P5); patch P5 17 BE / 9 FE.
- Exact-lock P0: NOT RUN, 41/77 dependency lệch/thiếu; AI 69 contract pass trong env lệch lock không thay exact-lock.
- Contract tiêu thụ: contract actual P5; wire G06 chỉ `baseRevision` của counter workspace, UI alias `modeRevision` cho cùng counter. Inventory: **13 SceneTypes / 12 BlockKinds**. `FeedbackService` đã có minimal hook P3, P5 mở rộng hook đó (GAP-01). Mode OFF/ADVISORY, chưa STRICT, chưa activate. Ba brief 4/8/11 DRAFT/UNVERIFIED. BM04: RF-09/10 17/11/2026, RF-11/12/13 24/11/2026, RF-08 YouTube 29/11/2026.
- GitGuardian: 11 incident sau backup; 1 đã xác nhận false positive (SHA-256 `AuthService.java`); 10 còn lại người dùng đã tự kiểm tra ngày 06/11. Không tự kết luận cả 11 false positive, không resolve/dismiss, không mở lại nhiệm vụ security trong P6, không sửa evidence lịch sử để dập alert.

## 3. Môi trường — chụp baseline trước khi chạm code

- Preflight: HEAD/branch/status/hash/diff kể cả untracked của cả hai clone; tách output build/cache đã có (BE `.build-p3/`, FE `docs/stem/phases/phase-04/takeover-codex/build/`) khỏi delta P6. Không clean/stash/reset, không checkout/pull/fetch/re-clone, không kéo main.
- AI venv/cache/output/build/log mới riêng cho P6, không tái dùng hoặc sửa `video-quality-venv`, Python global hoặc venv người dùng. Node engines/lock/Java 17/Maven wrapper/Python theo source, không global install/upgrade package để né lỗi.
- Embedded PostgreSQL disposable, E2eServer/fake mail/render/TTS, localhost port trống, env allow-list; không `.env` thật, không DB/server người dùng/shared/staging, không mail/Brevo/YouTube/AI/TTS thật. Provider thật chỉ nếu có lệnh riêng mở rõ (không có trong lần này). Không sửa worker/Remotion/assets. Chỉ dừng PID/data-dir do runner P6 tạo.

## 4. Trình tự thực thi — có gate contract và gate migration

### Chặng 1 — Exact-lock P6.0 (Q01 A)

1. So hash lock tại `services/ai/requirements.lock` trong clone BE với lock P0; nếu khác phải inventory lại, không giữ 41/77 như số đo hôm nay.
2. Tạo venv mới riêng cho P6; cài đúng phiên bản trong lock, không upgrade/downgrade lock để né resolver. Dependency thiếu wheel/không hỗ trợ platform/network thì ghi PARTIAL + blocker, trình hướng riêng; không tự đổi OS/runtime hoặc bổ sung package ngoài lock.
3. Tiêu chí gate: xác nhận interpreter/version/OS; đủ 77 mục pinned đúng version, **0 missing/mismatch**; phân biệt package bootstrap/ngoài lock; dependency consistency check không lỗi; log nguồn cài/cache và hash lock. Runtime smoke/import và toàn bộ suite AI hiện hữu chạy trong venv đó, fake provider/network guard, kết quả thực không fail/error; mọi skip giải thích. 69 là số baseline cũ, không phải số test bắt buộc phải khớp bằng cách bỏ test mới.
4. Gate tách **dependency equivalence** và **AI suites**: lock đạt nhưng suite lỗi chưa được gọi validation AI đạt; suite đạt trên env lệch lock cũng chưa đạt exact-lock. Lỗi ngoài phạm vi thì trình phương án sửa riêng, không che bằng mock module để import pass.
5. Nếu gate chưa đạt: chặn claim Java↔Python runtime parity, AI01 remote end-to-end, AI02 acceptance qua remote và provider validation. Được soạn contract/read source và làm library/Java fake độc lập (nếu Q01 cho phép); ghi kết quả đúng env, không đóng AI01/AI02 hoặc P0.

### Chặng 2 — Contract-stage gate (trước code phụ thuộc)

Khóa bằng văn bản trước khi viết code AI/library phụ thuộc:

1. AI target schema/version, required/optional/null/legacy route, exact actors/ownership/limits, response suggestion refs/provenance; giữ constructor compatibility hoặc ghi migration contract rõ; đồng bộ `extra="forbid"` Python và specimens Java/Python/FE. Đặt finite limits có số từ source hoặc trình rõ số mới.
2. Request/response stale/CAS khi context đổi; accept decision log khác persist draft; không trao model quyền đổi brief/source/status. Gọi AI ngoài transaction; recheck lúc apply/save.
3. Library field matrix: frozen path → public DTO → filter/count → FE card/export, missing/legacy semantics, authorization, stable sorting, alias old params, headers và limits. Current draft dashboard (nếu chọn) có origin riêng, không alias publication snapshot.
4. Không tin client/AI source VERIFIED/PASS, không thêm policy activate hoặc public snapshot/nonce mutation endpoint. Error envelope BE 400/details, 403/404/409/429 theo actual; provider mappings 502/503/504 giữ, retry chỉ khi được đặc tả. Không hardcode mã lỗi mới trong FE trước khóa contract.
5. Nếu khảo sát buộc cần migration/index/table/projection: DỪNG và trình schema/DDL proposal riêng (candidate V93000–V93009, location mới `src/test/resources/db/stem-p6/`), chờ Bond review và người dùng xác nhận số/location trước khi viết SQL. Không sửa V1–V22/V9000/V9001/V91000/V92000/checksums/history, không dùng V23 production.

### Chặng 3 — Implement theo Q02 A / Q04 A / Q05 A

1. **P6.2 AI grounding:** generate + analysis action hiện hữu ở Writer trên project/draft đã có brief. Server resolve context: authorize project/workspace/Writer role → load đúng draft/brief pin/catalog editions → resolve grade/subjects/outcomes/context/objectiveKey/activityKey/safety/coverage từ server. Phát hiện source/outcome ngoài scope/không tồn tại, DRAFT/UNVERIFIED hiển thị rõ. Client không được gửi trusted source status, policy/rule body hoặc grounding blob. Không nhận `isValidStemTopic`/PASS/VERIFIED do AI làm authority. Prompt parity: cùng context schema/version và specimen cho embedded Java + remote Python; missing/null/unknown/limits thống nhất; nội dung user/source là dữ liệu không phải lệnh — không cho prompt injection đổi policy/activate hoặc gọi URL từ model. Profile tiểu học DRAFT theo grade đã resolve (ngôn ngữ vừa sức, nhiệm vụ quan sát/đo đơn giản, vật liệu/rủi ro/giám sát khai báo); không dùng công thức/quy tắc an toàn do Codex tự coi là chuẩn Bộ. Output giữ scene result tương thích + envelope gợi ý có refs và cảnh báo/uncertainty, requestId/context binding/version; không tạo nguồn/outcome mới có trạng thái verified, không trả review decision có hiệu lực. Validation: deterministic structure/ref/scope/CAS dùng validator BE hiện hữu cho manual lẫn AI trước persistence/submit; AI terms/grade/extract là gợi ý/advisory tách khỏi quyết định STEM và media QA. Không gọi AI trên autosave, không bắt AI PASS để manual save. Accept/save: giữ `PUT /ai/requests/{id}/decision` là accept/ignore suggestion; chỉ explicit lưu draft qua write path hiện hữu mới đổi dữ liệu, cùng quyền/revision/schema/evidence checks; không auto-apply sau response. Target đổi khi provider đang chạy: response stale hoặc apply 409 giữ input, không dùng context cũ để save lên bản mới. Thời gian gọi provider nằm ngoài transaction/lock DB. Failure: key thiếu, timeout, 429, malformed schema hoặc Java/Python mismatch phải có lỗi rõ, không mất draft; manual write/review vẫn dùng được; retry thủ công có kiểm request/audit, không autosave retry/provider fan-out.
2. **P6.3 Library:** frozen learning card + combined filters + paging DB-side + read/export/link reuse. Card: cấp/lớp, môn chính/phối hợp, outcome, primary/secondary context có role, channel, mục tiêu, việc học sinh làm sau xem, hoạt động trong MP4/ngoài MP4 (`coverage_scope`), điều kiện/an toàn/giới hạn, source/edition và provenance review; hiển thị "chưa được thẩm định" khi UNVERIFIED. Metadata lấy snapshot PUBLICATION gắn đúng publication/clip, cùng phiên bản card; API list/detail/export/filter/count nhất quán. Không đọc current brief/catalog/project để lấp trường lịch sử thiếu. Filters: educationLevel, grade, subjectId/outcomeId theo edition, usageContext primary/secondary semantics, channel riêng; giữ subject/gradeLevel cũ và alias được đặc tả. Mọi giá trị multi-filter áp cùng tập publication trước paging/count, không FE lọc page rồi tính tổng. Không đưa DEPARTMENT_LIBRARY vào primary enum. Query: đọc JSON frozen payload trong sidecar/PUBLICATION bằng server query có tham số, JOIN/EXISTS refs không nhân đôi row; cùng predicate cho data/count/facets, stable sort + unique tie-breaker. Không tải toàn bộ thư viện vào Java rồi subList hoặc N+1 đọc snapshot từng card. Paging giữ array body + headers hiện hữu và bounded PageParams. `LEGACY_UNMAPPED`/missing snapshot vẫn browse/detail/export theo guard cũ, nhãn rõ, không suy grade từ "Đại chúng/Cơ bản/Nâng cao/EdTech", không tự gán outcome/context; missing curriculum fields là unknown, không match filter có giá trị cụ thể. Mismatch artifact đã biết vẫn không được acknowledgement bypass ở publish. Tái dùng ưu tiên mở/xuất/link học liệu có provenance; copy một phần vào draft có sẵn cần explicit action/ownership/CAS/new IDs/evidence, không copy approval/VERIFIED. Full project clone/Create/backfill lịch sử ngoài scope.
3. **P6.4 Gap P5 (Q05 A):** nhận F09/F10 ở mức validDTO/reference specimens (mỗi SceneType có DTO hợp lệ qua BE/FE validators, semantic mutation đổi hash hoặc UNKNOWN đúng contract; tách từng kind/extraBlocks, refs/cue/unknown và invalid cases) và F14 negative version regression (mismatch-version → review/UNKNOWN, history giữ; không update captured hash hoặc auto-migrate approval). Không đóng full F09 validDTO-render/pixel acceptance, không gọi 12 mutation fixtures là full rendered visibility acceptance. Mọi gap render/audio khác giữ PARTIAL/UNKNOWN. Không đổi thuật toán/classifier/version P5 để đáp ứng AI/library; phát hiện bug thuộc P5 thì dừng phần phụ thuộc, trình defect/scope sửa riêng, không gộp vào P6, không chỉnh test cũ cho xanh.

## 5. Những thứ tuyệt đối không đổi

- Contract delta P5 và G06 wire `baseRevision`; không rename field wire, không thêm endpoint approve AI mới, không 422, không hardcode mã lỗi mới ở FE trước contract-stage gate.
- 268 test BE + 227 test FE + 2 live P4/G06: giữ nguyên source/assertions, phải pass lại; không drop/skip/relabel để suite xanh; không cộng 41 thêm vào 268, không cộng 216 thêm vào 227.
- Không sửa test source/config/runner P4 và P5, không chạm assets Remotion/worker, không đổi package/lock/toolchain/renderer architecture, không sửa V91000/V92000.
- Không bật STRICT/activate, không nâng brief/rubric/catalog/source/outcome lên VERIFIED/ACTIVE, không cấp verified publication thiếu người xác nhận thật, không liên hệ giáo viên/tạo phản hồi giả, không chốt chuyên môn/LED/Toán 11, không sửa FR-38/Word/BM/diagram/T12a, không mở Create flow/project clone/catalog CRUD/LMS.

## 6. Test — theo Q08 A, tách từng ca

- Exact-lock: gate P6.0 như §4 Chặng 1; import/runtime và pytest suite AI hiện hữu + mới theo lock, fake provider/no real network. 69 pass env cũ không thay exact-lock.
- AI01 Java/Python: same server-resolved context specimens, primary/secondary/channel/grade/profile parity, foreign/out-of-scope refs, forbidden claims, null/unknown/limits; real localhost BE↔Python transport. Mock BE/Python thay runtime chỉ là unit, không full AI01.
- AI02/accept: manual vs AI invalid refs qua cùng draft validator; explicit save có CAS/seat, no auto save/approval, in-flight target edit 409; malformed/timeout/429/manual fallback. Accept log không phải save; fake model không chứng minh output khoa học đúng.
- AI advisory: G04 findings chỉ advisory, không hard AI PASS gate; no AI call autosave, no retry storm; no server-owned/VERIFIED/ACTIVE mutation. Không bật STRICT để test "happy path" giả.
- Library L01: PostgreSQL integration kết hợp level/grade/subject/outcome/context-role/channel + paging/count/facets; mutable current metadata đổi không đổi published card; stable sort, empty/last page, no duplicate JOIN rows. Chỉ FE filter hoặc page trước filter không đủ PASS.
- Library L02: snapshot thiếu/legacy/null browse/detail/export tương thích, nhãn đúng; known artifact mismatch không được publish bypass. Không backfill/cấp current STEM eligibility để làm legacy test qua.
- FE: Vitest/RTL unit/component + integration real BE clone, card/task outside MP4/labels, combined filter/page, stale request response giữ UI, lỗi giữ input, AI unavailable manual vẫn làm. Không browser Playwright/real model/MP4.
- Gap specimens: F09/F10 DTO/reference coverage và F14 version mismatch theo Q05 A; ghi từng scene/block/supported subcase. Full F09/F10, F06/pixel/audio/AT21 không chuyển PASS bằng DTO tests.
- Rerun bắt buộc: **268 BE + 227 FE + 2 live P4/G06 trên source cuối P6**, cùng case cũ không sửa assertion/config/runner để né lỗi; thêm AI/library regression vùng ảnh hưởng (deduplicate theo suite/test ID, không cộng trùng). Không chạy full BE 439. Full AT01–24/ba demo, browser acceptance ba cấp, provider thật, MP4 và exact-lock blocked không được coi là pass ngầm.

## 7. Đầu ra và dừng

Đầu ra trong hai clone, chưa commit:
- Contract actual/delta P6 + field mapping; exact-lock report/package inventory (lock hash, venv, 77 mục, suite results); grounded context specimens và Java/Python parity; library API/card/filter/page map; source/diff manifests; mapping từng case/subcase (52 ca + AT21 tách riêng); test baseline/log per suite; source/input/output hash manifest; handoff expected/actual, lỗi/giới hạn, ngoài kế hoạch, cleanup, dependencies; fake-vs-real limits rõ ràng.
- Không sửa báo cáo P3/P4/P5/Word chính thức để thay lịch sử; kết quả P6 ghi riêng.

Rollback: chỉ revert chọn lọc delta P6 theo snapshot trước thực thi; giữ nguyên code P3/fix/P4/P5 và commit backup `4fd7089`/`4dfa65e`; không reset/stash/clean, không force-push, không revert commit backup. Không down/drop/repair schema/history/shared.

**Bàn giao xong thì DỪNG. Không commit/push/deploy/merge, không tự đóng P0–P6, không mở P7.**
