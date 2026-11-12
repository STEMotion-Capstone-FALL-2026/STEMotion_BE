# Bàn giao gói sửa BE blocker — phương án A

**05/11/2026, Asia/Saigon — đã thực thi scope được duyệt, bàn giao local/chưa commit. P3/P4 vẫn PARTIAL/chưa tự đóng.**

## Kết quả

Đã sửa đúng boundary repository proxy: fields `jdbc`/`json` thành private final; thêm public non-final accessor `jdbc()`/`json()` trả **cùng target object**. StemService thay đủ38direct reads bằng method invocation. PET/AOP/transaction/mapper strict configuration, SQL/domain/workflow và mọi wire/migration giữ nguyên. Không inject dependencies riêng vào service, không disable/unwrap proxy, không suppression config.

**41BE tests pass (38P3 cũ +3full-context mới)**,0fail/error/skip; **2P4 live tests pass** qua BE clone sau sửa. Regression mới giữ nguyên source/hash từ before3HTTP500NPEfail sang after3pass. Full flow và G06 đã được xác nhận trong gói sửa này; báo cáo/log P4 trước đó vẫn giữ nguyên2FAILED theo lịch sử, không viết lại.

## Vị trí và baseline

- BE clone `C:/Users/LeTran/FPT University/2026_FALL/STEMotion/.audit/stem-phase-2026-11-05/phase-03/STEMotion_BE`; HEADf6c6f0bdcdcc6bbaf1f1246f86678d3249dea8e1, branch feat/stem-p3-isolated-20261105. **0commit mới**; P3 code vẫn uncommitted, không merge/copy sang repo khác.
- Approval: B-Q01A; B-Q02 contract/G06 giữ; B-Q03 không migrations/shared; B-Q04A chỉ core BT01–BT09. Prompt thực thi05/11 là chỉ dẫn, scope/investigation cũ giữ nguyên làm baseline chưa sửa.
- P0/P1/P2/P3/P4 status/numbers giữ theo các handoff cũ; không đổi UC/FR candidate, BM04 dates hoặc nội dung chuyên môn DRAFT/UNVERIFIED.

## Step0 — đủ callsite, gate trước source edit

Đã grep mọi Java/type-reference toàn clone:38field reads/34lines, tất cả tại StemService. StemPostgresTest có injection/method consumer nhưng không đọc fields, không là external field caller cần mở scope. [CALLSITE_INVENTORY](CALLSITE_INVENTORY.md) liệt kê từng dòng/occurrence; [JSON đầy đủ](CALLSITE_INVENTORY.json) có text trước sửa và type references. Hai snapshot byte trước sửa nằm cạnh handoff, hash trong INPUT_BASELINE.json.

Step1: regression mới được viết trước. Full main Spring/PET/JWT/real HTTP/embeddedPG, không slice/standalone/new repository/mock DB. Before log3failures tại workspace98/create46/lock33, đúng500NPE;0setup errors/skips. [BEFORE_GATE](BEFORE_GATE.json) xác nhận chưa edit production trước khi gate đỏ hợp lệ. Source regression không đổi sau gate.

## Patch chỉ3source files

[PATCH_BE_BLOCKER_FIX.diff](PATCH_BE_BLOCKER_FIX.diff) là delta **trên snapshot P3 uncommitted trước gói sửa**, không phải whole P3 vs main HEAD. Chỉ2class được duyệt và regression mới; không build/log/FE/config/migration trong patch.

| File | SHA256 source cuối |
|---|---|
| `src/main/java/com/stemotion/domain/stem/StemRepository.java` | `87a5233c651b0c73c944ead44ac15ef7c84bce7c3004d043ba2c0e24211956f9` |
| `src/main/java/com/stemotion/domain/stem/StemService.java` | `28b9d9f3026f6983acfb33bc4b830a96b12fe94034bee3ac5b633cb71f8dadcc` |
| `src/test/java/com/stemotion/e2e/StemFullContextRegressionTest.java` | `95763fb4234d062043de978fef23df1a9245b463b8f53e897bd45ecfa3c6cfa3` |

[SOURCE_MANIFEST](SOURCE_MANIFEST.json) ghi đường dẫn tuyệt đối. New test ở package E2eServer để tái dùng Config có sẵn. Service diff đã kiểm bằng máy: **chỉ38substitution db.jdbc/db.json→methodcall**, không đổi SQL/rules/transaction logic. Repository constructor vẫn clone mapper/FAIL_ON_UNKNOWN_PROPERTIES như cũ; accessors không final để proxy delegate; private fields ngăn direct access mới lọt qua compiler.

## Expected / actual BT01–BT09 và logs

[TEST_RESULTS](TEST_RESULTS.md) ghi đủ9gate expected/actual/PASS và giới hạn; [TEST_GATE_RESULTS](TEST_GATE_RESULTS.json) xác nhận counts/hashes/wire/DB audit. Điểm chính:

| Gate | Actual cuối |
|---|---|
| BT01 |Full main Spring/PET advisor/proxy thật, SELECT1,25migrations,0config suppression |
| BT02 |GETsettings500before→200after, OFF/default revision0, exact shape |
| BT03 |POSTcreate500before→200after, nullable brief0/UNVERIFIED, retry same key1brief |
| BT04 |Catalog/read/legacy draft200 và submit hook200;400/403/404/409 guards giữ |
| BT05 |38/38P3 pass, source/names không sửa,0skip |
| BT06 |Original P4 full script flow pass:400→sửa/save→submit→Reviewer atomic assessment; Admin no-seat403; DB1decision/1assessment/1receipt |
| BT07 |G06 pass: settings wire chỉbaseRevision, stale409; revision3/3mode audits, không partial stale write |
| BT08 |Copy/evidence/rollback/active seat/idempotency24h/limits P3 regression pass |
| BT09 |Hash/status preservation và4DB graceful shutdown, no owned process remaining |

BE: before.log/json (3fail mong muốn), after.log/json (41pass). Build/Surefire ở `.audit/be-blocker-fix/before|after`, không ghi `.build-p3`/P3 logs. P4: p4-q10.log/results.json (2pass), p4-backend.log, P4_RUN_RESULT.json. Không cộng before/lượt lặp vào41/2; không chạy full439BE hoặc216FEregression ngoài B-Q04A. Không mở live video CLIP/COMPOSITION, chỉ các video regression fake đã có trong38P3.

## G06 và full flow — evidence riêng

[p4-http-audit.json](p4-http-audit.json) là observer **fetch thật**, không mock; settings PUT statuses200/409/200/200. Body keys cóbaseRevision, không modeRevision; base revisions0/0/1/2. FE adapter vẫn tên nội bộmodeRevision, wire baseRevision là **cùng counter workspace**, không phải brief/script. Không đổi DTO/route/error/idempotency contract.

[P4_DB_AUDIT](P4_DB_AUDIT.json):25migrations,1assessment/1approved decision/1approval receipt, settings revision3/1ADVISORYworkspace/3mode audits;3audit cóbaseRevision,0modeRevision,0verified assessment. Fixture DRAFT/TEST_ONLY, phê duyệt workflow không trở thành chuyên môn đã xác minh.

## Bảo toàn và cleanup

[PRESERVATION_CHECK](PRESERVATION_CHECK.json): kiểm756existingfiles BEclone, chỉ2approved production files thay hash; regression mới và docs/orchestration mới thuộc scope. FE P4 clone319files, BE gốc695, FE gốc201 giữ nguyênhash/HEAD/branch/status tương ứng.38P3 test source/names, contract delta, configs, V1–22/V9000/V9001/V91000, P3/P4 handoff/log baseline không đổi. Không checkout/pull/fast-forward/reset/stash/clean, commit/push/deploy/merge, không dependency/global install.

[CLEANUP_CHECK](CLEANUP_CHECK.json):4ownedDB data dirs (before1, after2, P4live1),4postmaster shutdown markers; không còn process theo data dir hoặc owned JVM IDs. Full contexts/EmbeddedPG đóng graceful; live launcher Temp dừng qua stdin STOP/ctx.close. Không kill PostgreSQL theo tên chung, không dừng app người dùng, không dùng shared/production/server đang chạy, không provider thật.

BE-owned [ORCHESTRATION_REVIEW](ORCHESTRATION_REVIEW.md) mô tả config/cache/log mới và HTTP observer, đã review trước live execution. Chỉ đọc source/deps/fixture SQL cũ FE; không chạy runner FE ghi đè baseline. Cache/report/build mới nằm trong BE, native loader/envDir=false. Test credentials chỉ TEST_ONLY; không log access token/password hoặc .env thật.

## Ngoài kế hoạch / FAIL / NOT_RUN

Không feature ngoài phương án A hoặc3source files. Supporting BE runner/config/observer/log/hash/patch là orchestration/evidence cần thiết, không sửa FE config/source hay mở endpoint mới. Preflight path-normalization assertion đã xử lý trước code, không phát hiện external caller. Không có FAIL/NOT_RUN còn lại trong BT01–BT09; before3fail được giữ như chứng cứ fail-before.

Không chạy các phần ngoài B-Q04A: full BE439/FE216regression, live video mới, AI exact-lock/P6, MP4/browser E2E, projector/hash/timing/AT21/snapshot/render/publish/P5, shared/C01/C02/production reservation/release, chuyên môn/official sources, Word/BM/diagram/T12a. Chưa có quyền dùng các pass này để tự đóng P3/P4.

## Rollback và bước đề nghị

Rollback chỉ chọn lọc delta3files theo patch và byte snapshots trước sửa, sau khi so hash không có work mới; giữ nguyên P3uncommitted baseline và docs/log cũ. Không git reset/stash/clean toàn repo, không drop/down/rollback migrations DBshared. Rollback chưa thực hiện; code hiện giữ để review.

Đề nghị Bond/người dùng review patch3files, fail-before/pass-after cùng source và2livepass; quyết định nghiệm thu/merge bằng lệnh riêng. P5 chỉ bắt đầu khi có thẻ/prompt duyệt riêng.

**DỪNG sau bàn giao. Không commit/push/deploy/merge, không tự đóng P3/P4, không mở P5.**
