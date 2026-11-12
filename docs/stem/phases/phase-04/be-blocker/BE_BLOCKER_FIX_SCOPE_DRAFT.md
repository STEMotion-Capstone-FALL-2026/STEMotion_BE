# THẺ SCOPE SỬA BE BLOCKER P4 — DRAFT / CHƯA DUYỆT

05/11/2026, Asia/Saigon. Chỉ là scope nháp sau điều tra, **chưa sửa code/config/test, chưa chạy lại P4**. Trình Bond review → người dùng chọn B-Q01… → prompt thực thi riêng. Không tự đóng P3/P4 hoặc mở P5.

## 1. Baseline và vấn đề cần sửa

- Clone duy nhất: `C:/Users/LeTran/FPT University/2026_FALL/STEMotion/.audit/stem-phase-2026-11-05/phase-03/STEMotion_BE`, HEADf6c6f0bdcdcc6bbaf1f1246f86678d3249dea8e1, branch feat/stem-p3-isolated-20261105. P3 uncommitted giữ nguyên, không kéo/checkout repo gốc.
- P3 PARTIAL:439reported=422pass+17skip, trong đó38P3 (29DB/API/transaction+9unit). Đây là số baseline trước sửa, không kết quả mới.
- P4 PARTIAL:216FE pass,2live failed; fullflow và G06 real assign/mode/409 còn NOT_RUN. Không sửa hoặc ghi đè báo cáo P3/P4.
- Điều tra hiện tại: full main app repository có PersistenceExceptionTranslationAdvisor/CGLIB; service đọc public field trên proxy null trong khi target fields nonnull. Slice P3 không có proxy/postprocessor. Hai endpoint gốc và bốn nhánh khác trả500; một số read/draft APIs vẫn200. [Bằng chứng](BE_BLOCKER_INVESTIGATION.md).

## 2. Mục tiêu / phạm vi dự kiến sau duyệt

B1. Loại bỏ direct field access không tương thích proxy ở boundary StemService–StemRepository; giữ exception translation/AOP/transaction bình thường trong full context. Sửa mọi occurrence liên quan trong hai class theo phương án người dùng chọn, không chỉ hai dòng đang ném500.

B2. Thêm regression **full Spring application context** với real PET postprocessor/repository proxy, real HTTP trên localhost, JWT/fixture TEST_ONLY, embedded PG mới/provider fake. Ca lỗi phải đỏ trước sửa và xanh sau sửa; không manually new repository hoặc dùng standalone/slice thay full context để test qua.

B3. Rerun đủ38testP3 không xóa/đổi assertion để né lỗi; giữ test names/contracts. Rerun P4-Q10 full flow và G06 qua clone sau sửa theo B-Q04. Đây là **phạm vi đề xuất cho lần thực thi sau**, chưa được chạy trong lượt soạn scope này.

B4. Bàn giao patch/hash/log mới riêng cho gói sửa; ghi expected/actual và các FAIL/NOT_RUN còn lại, đối soát bảo toàn FE/BE gốc/P4 handoff. Dừng đúng process/DB mình tạo, không shutdown PostgreSQL theo tên process chung.

## 3. Phương án sửa — chưa khóa

| Phương án | File dự kiến chạm | Cách sửa dự kiến / rủi ro | Contract / migration / test cũ |
|---|---|---|---|
| **A — Proxy-safe repository method/accessor (đề xuất ưu tiên)** | StemRepository.java, StemService.java; thêm test mới com/stemotion/e2e/StemFullContextRegressionTest.java | Thêm method không final trả đúng JdbcTemplate/ObjectMapper của target (hoặc operation boundary nhỏ tương đương), thay toàn bộ direct db.jdbc/db.json trong service bằng method invocation. Cân nhắc private fields để compiler chặn direct access; chỉ làm sau kiểm caller, không làm gãy38tests. Không di chuyển/rewrite toàn bộSQL/domain. Rủi ro: sót callsite, accessor final không được intercept, lỡ đổi mapper strict config hoặc transaction connection. | Wire/DTO/schema không đổi; datasource/mapper cùng object như target hiện tại; @Transactional và PET giữ.38tests cũ giữ nội dung/names, phải pass lại; thêm full-context regression. |
| **B — Inject service dependencies riêng** | StemService.java constructor/fields; test full context mới. Nếu constructor có consumer ngoài DI cần liệt kê trước, không ngầm sửa test cũ | Service nhận JdbcTemplate/ObjectMapper qua constructor riêng và dùng field của chính target service; không đọc field trên repository proxy. Giữ repository methods như cũ. Rủi ro: service và repo có mapper khác config/copy, tăng coupling; public repository fields vẫn là bẫy cho caller tương lai, phải ghi debt. | Wire/migrations không đổi, không thêm endpoint. Chứng minh cùng datasource/transaction và mapper semantics;38P3 giữ/pass. |
| **C — Tắt/loại PET bằng config/profile (đối chiếu, không khuyến nghị nghiệm thu)** | Có thể là application-stem-p3-test.properties hoặc main config/exclusion; **chưa được phép chạm** | Tắt spring.dao.exceptiontranslation.enabled hoặc bỏ proxy có thể tránh triệu chứng, nhưng thay hành vi exception translation/bean graph; tắt riêng test còn che defect runtime mặc định. Chưa thử và không phải config thiếu. Rủi ro vượt phạm vi, ảnh hưởng repositories khác, xanh do test environment khác production. | Không cần đổi wire/schema, nhưng config shared/main là gate riêng B-Q03. Nếu chọn cách chỉ giấu trong profile test thì không đạt tiêu chí sửa full context. Không tự bỏ @Repository/AOP để pass. |

**Ưu tiên trình A** vì giữ bean graph/advisor/transaction và mapper đã được khởi tạo đúng, sửa đúng cách service truy cập repository; diff có thể giới hạn hai class + một regression mới. Đây là đề xuất, không phải người dùng đã chọn A. B vẫn là phương án hợp lệ để review. Không phương án nào đã được thử như một bản sửa.

## 4. Những thứ tuyệt đối không đổi

- Contract delta P3, DTO/route/error/idempotency24h/limits/authorization/CAS. Settings PUT giữ tên wire **baseRevision của counter workspace**; không gửi/đổi sang modeRevision. FE alias modeRevision là cùng counter và đã duyệt ở P4; View.modeRevision, Assessment.expectedModeRevision và response WorkspaceMode.revision vẫn theo contract hiện có.
- V1–V22, V9000/V9001 và **V91000 TEST_ONLY**: giữ nội dung/checksum/location/history, không placeholderV23/V24, outOfOrder/repair, không promote test migration hoặc rerun shared auth cutover. C01/C02/cloud Flyway OFF vẫn mở như baseline.
-38testP3 và assertions mục đích ban đầu; không drop/skip/relabel để suite xanh. Báo cáo P3 cũ giữ nguyên, thêm kết quả sửa riêng.
- Không endpoint mới, không wire field rename, không nâng dependency/toolchain để né lỗi; không STRICT/activate/VERIFIED/ACTIVE giả.
- Không sửa FE/P4 test source/config/runner, assets Remotion, Worker, Python AI. Việc **rerun** test P4 đã có chỉ nằm trong gói thực thi sau B-Q04; không biến nó thành quyền sửa FE.
- Không liên hệ người khác/chuyên môn giả; không sửa report Word/BM/diagram/T12a/master/P0/P1/P2/P3/P4 handoff để hợp thức hóa.

Nếu cần đổi một trong các mục bất biến để tiếp tục, dừng phần phụ thuộc và trình scope/contract riêng; không ngầm mở rộng bằng cấu hình test.

## 5. File/service dự kiến và đầu ra sau sửa

| Thành phần | Mức / mục đích |
|---|---|
| src/main/java/com/stemotion/domain/stem/StemRepository.java | A: accessor/visibility proxy-safe, giữ persistence behavior; B: đọc/giữ nguyên trừ khi được duyệt thêm |
| src/main/java/com/stemotion/domain/stem/StemService.java | A/B: sửa field boundary trên toàn callsites; không đổi workflow/rules/SQL nghĩa |
| src/test/java/com/stemotion/e2e/StemFullContextRegressionTest.java (mới, **chưa tạo**) | Cùng package E2eServer để tái dùng Config có sẵn; full app/PET/embeddedPG/fakes, không standalone/slice để chứng minh fix |
| Existing StemPostgresTest/StemValidatorTest | Read/rerun, không sửa/delete tests để che regression |
| Docs/evidence gói sửa mới | Handoff mới, patch/hash, before/after HTTP/regression logs, cleanup/preservation; không ghi đè P3/P4 baseline |
| application*.yml/properties/migrations/FE | Không chạm trong A/B. Phát sinh nhu cầu → gate B-Q02/B-Q03 hoặc scope riêng |

Supporting orchestration có thể dùng runner tạm chỉ cho test, mọi URL phải owned localhost/DB dùng một lần. Source/runner mới ngoài bảng có tác động nghiệp vụ phải trình lại, không tự thêm.

## 6. Kế hoạch test và tiêu chí nghiệm thu bắt buộc

| Mã | Ca / điều kiện | PASS sau sửa | FAIL / dừng |
|---|---|---|---|
| BT01 | Boot full main context, feature-on, PET advisor thật, PG test25migrations | Context boot; JDBC/JSON target usable qua boundary đã chọn; repository advisor không bị tắt để né lỗi; SELECT1/schema khỏe | Startup fail, profile thiếu feature/schema, proxy bị strip/unwrap prod để pass |
| BT02 | GET settings đúng workspace/actor, cùng request P4 | **200**, mode OFF/default revision0 trên fresh settings; response shape revision/policyIds/defaultPolicyId giữ; regression trước sửa **500NPE** như evidence |500 hoặc chỉ test plain/slice repo; mock response/DB thay runtime |
| BT03 | POST create cùng DTO schemaVersion/expectedScriptRevision0/idempotencyKey | **200** (route StemController trả record, không đổi sang201), brief revision0/primary:null/DRAFT/UNVERIFIED; retry cùng key không duplicate; regression trước sửa **500NPE** |500 hoặc đổi request/contract/permission/migration để tránh codepath |
| BT04 | Read/catalog/frameworks, legacy draft và submit hook | Read200 như trước; legacy draft200; submit hợp lệ qua hook không NPE, state đúng guard workflow; negative400/403/404/409 được giữ | Sửa500 nhưng làm gãy read/legacy route; transaction/permission bypass |
| BT05 |38P3tests gốc | **38/38pass**,29DB/API/transaction+9unit,0newskip; source/expected nghĩa giữ; report riêng không ghi lại số baseline | Bất cứ fail/error/skip mới; sửa test/fixture để loại proxy bệnh mà không regression full context |
| BT06 | P4-Q10 qua FE existing source → BE clone, fresh test data | Thiếu completeness vẫn lưu draft/findings OFF/ADVISORY; malformed/reference thật400; sửa giữ input → save200 → submit → Reviewer active approve note+assessment atomic; Admin không seat403; không duplicate receipt/decision | Mock thay BE, request chưa đi tới branch,500 bất kỳ, auto VERIFIED hoặc workflow bị hard gate trái OFF/ADVISORY |
| BT07 | G06 assign/mode/real stale409 | GET current revision; FE adapter chỉ gửi wire baseRevision; assigned DRAFT policy/OFF↔ADVISORY thành công và revision tăng; request stale409 không ghi partial; retry key/payload đúng; default thuộc assignment | Field modeRevision bị gửi vào settings, counter lấy từ brief/script, stale vẫn200, input/reset hoặc unknown branch chưa chạy |
| BT08 | Transaction/identity/seat regression hiện có | Copy/evidence/rollback/reviewer ACTIVE checks/idempotency24h/limits theo delta, không đổi semantics. Dùng đúng tests P3 đã có, không suy hash proof | Đổi SQL schema, bỏ guard để pass, copy approval/activate STRICT |
| BT09 | Bảo toàn/cleanup | BE/FE gốc và clone FE/P4 baseline/hash/status giữ; chỉ files scope mới dirty; owned contexts/PG dừng đúng data dir, không orphan; outputs mới tách baseline | Ghi đè P3/P4 logs/report, dùng server/DB/provider thật, kill app người dùng |

`BT08` không tạo giới hạn idempotency mới: **idempotency24h** và mọi bound giữ đúng CONTRACT_DELTA.md. Phần video branches hiện có chạy theo regressionP3; full render/MP4/hash/AT21 không mở trong gói này. Nếu muốn thêm live approve-video CLIP/COMPOSITION riêng phải chốt rõ ở B-Q04, không gọi flow script là đã cover hai nhánh.

**Fail-before/pass-after** phải là regression test source giữ nguyên, chạy source P3 trước sửa ghi500 ở BT02/BT03 rồi chạy lại sau sửa ghi200. Evidence điều tra hiện tại là tiền đề, không thay thế việc test regression mới đỏ trước sửa. Mọi lần chạy/lỗi/log tách riêng; chỉ count lượt bàn giao cuối mỗi suite, không cộng probe hoặc old P3/P4 count.

## 7. Dependency, gates và cách rerun an toàn

- Cần người dùng chốt B-Q01–B-Q04 và prompt thực thi. A/B không đòi contract/migration/shared config thay đổi theo bằng chứng hiện tại; nếu phân tích thực thi khác đi thì dừng phần đó.
- Chỉ clone BE P3, không merge main/copy P3 đi repo khác. Log/sourcehash trước khi sửa; working tree uncommitted ban đầu được giữ, không tự stash/reset/clean.
- Rerun P4 dùng **existing test source read-only**, BE orchestration cô lập và output/cache/log mới trong gói sửa hoặc Temp đã duyệt; không chạy runner làm ghi đè thư mục handoff P4 cũ. Cách runner/cache cụ thể phải review trước execution, không sửa FE config để chạy cho tiện.
- Chỉ fresh embeddedPG/test profile V1–22+9000/9001+91000; không dump/shared URL, provider fake/off. Không rerun AI exact-lock, không MP4/browser E2E.
- Sau kết quả, đối soát scoped source + configs/migration/reports cả trước/sau; dừng đúng JVM/context/DB do lần chạy tạo, giữ app người dùng. Không auto đóng P3/P4.

## 8. Rollback dự kiến

Chỉ revert chọn lọc patch sửa blocker trong clone BE theo snapshot trước sửa; giữ nguyên code P3 uncommitted và docs/log baseline. Không dùng git reset/stash/clean toàn repo. Không down/drop DB shared hoặc rollback V91000/V1–22/V9000/V9001. Dừng test contexts/EmbeddedPG trước, kiểm PID/data dir sau. Nếu có runtime properties chẩn đoán tạm thì chỉ truyền cho process và bỏ cùng process, không để profile suppression tồn tại trong repo. Giữ log fail-before và fail-after nếu có, không xóa evidence để gọi PASS.

## 9. Câu hỏi người dùng cần quyết — chưa có câu nào tự chốt

| ID | Quyết định cần duyệt |
|---|---|
| **B-Q01** | Chọn **A accessor/method boundary (đề xuất ưu tiên)** hay **B inject dependencies riêng service**? C config/PET suppression chỉ đối chiếu, không đề xuất nghiệm thu bằng tắt riêng profile test. Chưa lựa chọn nào được áp. |
| **B-Q02** | Xác nhận giữ nguyên contract delta/G06 wire baseRevision; nếu execution thấy cần contract change, có dừng và mở scope/decision riêng trước không? Thẻ này **không cấp quyền đổi contract**. |
| **B-Q03** | Xác nhận không đổi migrations/config shared; nếu phát sinh nhu cầu, dừng và trình scope riêng hay hoãn nhánh đó? Không cấp quyền sửa shared hoặc promote testDDL trong thẻ này. |
| **B-Q04** | Mức rerun: **A — bắt buộc BT01–BT09 (gồm38P3+new full context+P4-Q10+G06)**; hoặc **B — cùng core đó cộng full BE regression và FE216 regression read-only**? Có yêu cầu live video CLIP/COMPOSITION bổ sung không? Không phương án nào được bỏ core full flow/G06; cache/log outputs phải tách baseline và không sửa FE. |

## 10. Ngoài phạm vi và điểm dừng

P5 projector/semantic/presentation/render hashes, timing/AT21, snapshots/artifact/render/publish gates; P6 AI/library/exact-lock; sửa FE/Worker/Python; curriculum/chuyên môn/teacher feedback; BM/diagram/T12a/native export; shared bridge/preflight/release; commit/push/deploy/merge. Không bật STRICT hoặc activate/verify data.

**DỪNG sau thẻ nháp. Bond/người dùng chưa duyệt sửa; chưa thực thi phương án nào; không chạy lại P4-Q10 hoặc mở P5.**
