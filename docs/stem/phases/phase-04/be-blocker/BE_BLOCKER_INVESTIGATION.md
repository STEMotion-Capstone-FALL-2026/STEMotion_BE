# Điều tra BE blocker P4 live500 — chưa sửa code

**05/11/2026, Asia/Saigon. ĐIỀU TRA ĐÃ THỰC HIỆN; PHƯƠNG ÁN SỬA CHƯA DUYỆT. P3/P4 vẫn PARTIAL.**

## Kết luận có bằng chứng

Phân loại chính **(b): tương tác proxy/wiring repository trong full Spring context**. `StemRepository` target đã được khởi tạo đúng; object được inject vào `StemService` là CGLIB proxy do `PersistenceExceptionTranslationPostProcessor`. Code P3 đọc trực tiếp `db.jdbc`/`db.json` trên proxy nên nhận null; gọi method repository được delegate tới target và vẫn chạy. Đây là defect truy cập field của code P3 lộ qua khoảng trống test full context đã biết, **không phải lỗi FE** hoặc thiếu migration/JSON bean. Không gọi target repository là chưa khởi tạo khi target fields đã nonnull.

Chỉ điều tra và soạn scope: không sửa source/config/test/migration/contract; không chạy FE hoặc rerun P4-Q10; không patch proxy hoặc unwrap target trong service. Reflection trong probe chỉ đọc để chẩn đoán.

## 1. Endpoint, request và lỗi chính xác

Tái hiện trên full main application + cấu hình test E2eServer có sẵn, profile stem-p3-test, localhost127.0.0.1:54742 và DB embedded PostgreSQL mới. Fixture ghi rõ TEST_ONLY; ID dưới đây chỉ tồn tại trong DB dùng một lần, không dữ liệu người dùng. P4 logs giữ nguyên, chỉ dùng làm đầu vào.

### GET settings

`GET /api/v1/workspaces/4057d63b-ace4-4b93-812c-70ae2d2b9456/stem-settings`

Request body:

```json
null
```

Response thật:

```json
{
  "status": 500,
  "code": "INTERNAL_ERROR",
  "message": "An unexpected error occurred",
  "timestamp": "2026-11-05T18:49:20.6814605"
}
```

HTTP **500**; `evidence/runtime-probe.log:224`; stack bắt đầu tại dòng63.

### POST create

`POST /api/v1/scripts/a04b5262-ec98-453c-8ec8-3e02add40c86/versions/d80ea6b3-ec74-44f4-aca9-175dbdb87fc7/stem`

Request body:

```json
{
  "schemaVersion": "stem.v1",
  "expectedScriptRevision": 0,
  "idempotencyKey": "blocker-create-0001"
}
```

Response thật:

```json
{
  "status": 500,
  "code": "INTERNAL_ERROR",
  "message": "An unexpected error occurred",
  "timestamp": "2026-11-05T18:49:20.722463"
}
```

HTTP **500**; `evidence/runtime-probe.log:387`; stack bắt đầu tại dòng227.

Hai dòng exception đầu tiên, nguyên văn:

```text
java.lang.NullPointerException: Cannot invoke "org.springframework.jdbc.core.JdbcTemplate.queryForObject(String, java.lang.Class, Object[])" because "this.db.jdbc" is null
    at com.stemotion.domain.stem.StemService.workspace(StemService.java:98)
java.lang.NullPointerException: Cannot invoke "com.fasterxml.jackson.databind.ObjectMapper.valueToTree(Object)" because "this.db.json" is null
    at com.stemotion.domain.stem.StemService.create(StemService.java:46)
```

GET đi qua `StemController.java:26` → `StemService.settings:97` → `workspace:98`. POST đi qua `StemController.java:17` → `StemService.create:46`, lỗi ngay `db.json.valueToTree(request)` trước persistence. Shape POST giữ đúng P3/P4: schemaVersion=stem.v1, expectedScriptRevision=0 và idempotencyKey; không gửi field server-owned. GET không có body. Stack/bodies đầy đủ tại [RUNTIME_OBSERVATIONS](evidence/RUNTIME_OBSERVATIONS.json) và [STACK_EXCERPTS](evidence/STACK_EXCERPTS.json).

## 2. db.jdbc/db.json là gì và tại sao null

- `StemRepository.java:11–13`: public final fields `JdbcTemplate jdbc`, `ObjectMapper json`; constructor nhận hai bean, gán jdbc và clone mapper với FAIL_ON_UNKNOWN_PROPERTIES. Đây là field Java, không property YAML, không tên bean db.jdbc/db.json và không biến môi trường.
- `StemService.java:21–23`: constructor nhận StemRepository và lưu vào field db. `@Service/@Transactional` tại17–20; repository `@Repository` tại8, conditional app.stem.enabled tại9. Cả hai context probe đều có feature=true/profile stem-p3-test.
- `runtime-probe.log:59` chứng minh db của service target chính là repository bean proxy, không phải repository object khác bị bỏ constructor. Target của proxy có cả jdbc/json nonnull. `select1=1`,25migrations thành công và `repo.encode` qua method trả JSON đúng. Không có lỗi datasource chưa khởi tạo.

| Quan sát runtime | Full Spring main app | P3 DataJpaTest context (không chạy test method) |
|---|---|---|
| Profile / feature | stem-p3-test / true | stem-p3-test / true |
| Repository bean class | StemRepository$$SpringCGLIB$$0 | StemRepository |
| AOP proxy | true | false |
| Field jdbc/json đọc trên bean inject | null / null | nonnull / nonnull |
| Field jdbc/json trên target thật | nonnull / nonnull | nonnull / nonnull |
| PersistenceExceptionTranslationPostProcessor | Có | Không |
| Repository advisor | PersistenceExceptionTranslationAdvisor | Không |
| select1 / migrations |1 /25 |1 /25 |
| repo.encode qua method | Thành công | Thành công |

Log hai quan sát: `runtime-probe.log:59` và `:1198`; JSON đầy đủ tại evidence/RUNTIME_OBSERVATIONS.json. Constructor khởi tạo target. Spring CGLIB/Objenesis tạo proxy khác object target, không chạy constructor lần hai theo đường bình thường; field access JVM `getfield` không đi qua method interception. `StemService` bytecode đang dùng getfield StemRepository.jdbc/json. Gọi `db.settings/brief/encode/catalog` lại là method invocation được delegate, nên các nhánh chỉ dùng method có thể chạy. Bytecode nguồn thư viện đang dùng được trích tại [SPRING_PROXY_BYTECODE](evidence/SPRING_PROXY_BYTECODE.txt), [STEM_SERVICE_BYTECODE](evidence/STEM_SERVICE_BYTECODE.txt); không dựa vào suy đoán từ tên class.

## 3. Phân loại và loại trừ

| Nhóm theo prompt | Kết luận / bằng chứng |
|---|---|
| (b) wiring/proxy | **Nguyên nhân chính**: advisor/class proxy/field-vs-target đo trực tiếp. Cách diễn đạt chính xác là truy cập field không tương thích proxy; target repository đã khởi tạo đúng. |
| (a) runtime config thiếu/sai | Loại khỏi nguyên nhân chính: cùng profile/feature, datasource hoạt động,25migrations, mapper target nonnull và encode thành công. Không exception missing bean hoặc missing relation. YAML main/cloud không cung cấp field tên db.jdbc/db.json; cloud không được activate trong probe. |
| (c) service đọc bean/config chỉ có trong test | Loại theo đúng định nghĩa này: StemRepository là main-source @Repository; full context có JdbcTemplate/ObjectMapper thật, không thiếu bean test-only. P3 test config có ObjectMapper riêng nhưng full mapper cũng được gán vào target đúng. Việc code P3 đọc public field vẫn là defect code, song không phải dependency chỉ tồn tại trong test config. |

Boot3.2.5 mặc định đăng ký PET postprocessor khi `spring.dao.exceptiontranslation.enabled` absent (`matchIfMissing=true`). `evidence/SPRING_DAO_AUTOCONFIG.txt:129–135` ghi annotation này. Không cần thêm field config vào profile. Không thử tắt exception translation, không disable AOP, không sửa flag/schema/contract để làm 500 biến mất.

## 4. Phạm vi ảnh hưởng được tái hiện

**Không chỉ hai ca P4, nhưng cũng không phải mọi call qua StemService đều fail.** Đã gọi13request chẩn đoán, không chạy full flow P4 hoặc test FE. Dữ liệu TEST_ONLY, permissions đúng từng actor.

| Case | Method + path (prefix /api/v1) | HTTP thật | Log line |
|---|---|---|---|
| STEM_SETTINGS_GET | `GET /workspaces/{TEST_ONLY_ID}/stem-settings` | 500 | runtime-probe.log:224 |
| STEM_CREATE_POST | `POST /scripts/{TEST_ONLY_ID}/versions/{TEST_ONLY_ID}/stem` | 500 | runtime-probe.log:387 |
| STEM_GET | `GET /scripts/{TEST_ONLY_ID}/versions/{TEST_ONLY_ID}/stem` | 200 | runtime-probe.log:388 |
| STEM_CATALOG | `GET /stem/catalog` | 200 | runtime-probe.log:389 |
| STEM_FRAMEWORKS | `GET /stem/frameworks` | 200 | runtime-probe.log:390 |
| STEM_SAVE_PUT | `PUT /scripts/{TEST_ONLY_ID}/versions/{TEST_ONLY_ID}/stem` | 500 | runtime-probe.log:553 |
| STEM_VALIDATE_POST | `POST /scripts/{TEST_ONLY_ID}/versions/{TEST_ONLY_ID}/stem/validate` | 500 | runtime-probe.log:716 |
| STEM_SETTINGS_PUT | `PUT /workspaces/{TEST_ONLY_ID}/stem-settings` | 500 | runtime-probe.log:879 |
| NON_STEM_ME | `GET /auth/me` | 200 | runtime-probe.log:880 |
| NON_STEM_WORKSPACES | `GET /workspaces` | 200 | runtime-probe.log:881 |
| NON_STEM_SCRIPT_GET | `GET /scripts/{TEST_ONLY_ID}` | 200 | runtime-probe.log:882 |
| NON_STEM_DRAFT_PUT | `PUT /scripts/{TEST_ONLY_ID}` | 200 | runtime-probe.log:883 |
| NON_STEM_SUBMIT_HOOK | `POST /scripts/{TEST_ONLY_ID}/submit-review` | 500 | runtime-probe.log:1075 |

Có **6response500 và7response200** trong13probe HTTP. GETstem khi chưa brief, catalog và frameworks vẫn200 qua repository methods; catalog fixture DRAFT có row thật và data JSON nên không phải chỉ kiểm list rỗng. Trong API không mang đường dẫn /stem, đọc auth/workspaces/script và lưu draft **chưa có brief**200, nhưng submit-review500 tại `StemService.lock:33` qua hook submission. Không kết luận mọi API ngoài STEM an toàn.

Code review còn cho thấy các nhánh lock/receipt/save/settings/validate/assessment/copy/invalidate có direct db.jdbc/db.json; xem StemService:33–44,53–69,98–116,119–158. GETbrief có evidence kiểm scene tại86 và draft mutation đã có brief tại121 có thể đi qua direct access. Các điều kiện existing-brief/evidence và approve/video/copy **chưa tái hiện riêng trong lần này**, chỉ ghi nguy cơ theo source; không tính là probe đã chạy. Runtime hiện tại feature-on trong clone; không suy blocker là lỗi trên production/live vốn chưa merge code P3.

## 5. Vì sao P3 không bắt được

- `StemPostgresTest.java:42–45` dùng DataJpaTest slice, ActiveProfiles stem-p3-test và Import bốn class. `:47–53` cấp embedded datasource/ObjectMapper trong TestConfiguration. Boot slice chọn auto-config giới hạn; danh sách imports thực từ jar Boot3.2.5 tại [DATA_JPA_IMPORTS](evidence/DATA_JPA_IMPORTS.txt) không có PersistenceExceptionTranslationAutoConfiguration.
- Probe boot chính context test class qua TestContextManager, **không invoke29JUnit test methods**: repository plain và PET postprocessor absent tại log1198. Đây là khác biệt đo được so với full app tại59 dù cùng profile và25migrations.
- `:68` tạo ScriptService bằng new trong fixture rồi set stem; `:69` dùng MockMvcBuilders.standaloneSetup(new StemController(stem,validator)). Không full app component scanning/auto-config/security JWT/live servlet pipeline.
- `nullDraftReplayCasAndUnknownFieldApi` tại75–80 gọi create/save rồi standalone MockMvc; `settingsPinNoBackfillAndStrictRefused` tại82 kiểm settings trên slice plain repo. Chúng không đi qua repository proxy của full app. `workflowApproveLinksExactlyOneExistingDecisionAndRetry` tại85 dùng service transaction trong fixture; không live HTTP.
- `StemValidatorTest.java:8–9` new ObjectMapper/StemValidator thuần,9unit tests không có Spring proxy. P3 đã ghi giới hạn này trong P3_HANDOFF §2/P3.8 và §4: standalone MockMvc/service transaction, chưa live HTTP. **Giữ nguyên38testP3 và439reported=422pass+17skip của baseline; không viết lại thành test full app hoặc cộng probe hiện tại vào số đó.**

## 6. Bảo toàn, cách chạy và giới hạn

Clone BE HEADf6c6f0bdcdcc6bbaf1f1246f86678d3249dea8e1, branch feat/stem-p3-isolated-20261105, P3 source vẫn uncommitted. Không checkout/pull/fast-forward hoặc copy code. Đọc đủ các input theo thứ tự prompt; lần trích patch đầu gặp lỗi encoding stdout/locator tương đối, đã đọc lại đúng file, không có input thực sự thiếu. Hash/đường dẫn tuyệt đối của source/config/migrations/P3/P4 input tại [SOURCE_HASHES](evidence/SOURCE_HASHES.json).

Probe Java chẩn đoán được biên dịch/chạy bằng Java17 source launcher trong Temp, không viết code/config/test vào repo. Dùng bytecode .build-p3 của P3 + E2eServer.Config, override localhost port0, test profile, Flyway test locations, dotenv off, notification email off, fake integrations; môi trường allow-list và credentials chỉ TEST_ONLY. Reflection chỉ đọc object/field/launchArguments để quan sát, không mutate bean/target. Không config workaround hoặc positive fix được áp.

Lượt probe đầu compile lỗi tên hàm fixture findByUsernameIgnoreCase; không boot app/DB. Sửa **probe Temp** sang hàm findByUsername đúng source, giữ log probe-r0-compile.log. Lượt chẩn đoán cuối exit0,56.46s; exit0 nghĩa probe hoàn tất, **không phải BE được sửa hoặc13endpoint pass**. Kết quả500 được giữ nguyên. Không chạy38tests hoặc FE216tests lần này.

Full context và DataJpaTest context dùng hai DB test khác nhau, mỗi DB25migrations, không restore/shared DB. Đã close cả hai contexts và EmbeddedPostgres; log có2shutdown markers, native read-only verification không còn process thuộc hai data dir/PID40384. [CLEANUP_CHECK](evidence/CLEANUP_CHECK.json). Không dừng process người dùng, không để runtime override tồn tại trong repo.

Đối soát cuối tại [PRESERVATION_CHECK](evidence/PRESERVATION_CHECK.json): chỉ hai tài liệu mới và supporting evidence dưới docs/stem/phases/phase-04/be-blocker là thay đổi được phép; source/config/test/migration cũ, reports P3/P4, clone FE và repo gốc giữ hash/HEAD/status tương ứng. Không commit/push/deploy/merge.

## 7. Chuyển sang thẻ sửa nháp, chưa thực thi

Xem [BE_BLOCKER_FIX_SCOPE_DRAFT](BE_BLOCKER_FIX_SCOPE_DRAFT.md): A accessor/method boundary repository (đề xuất ưu tiên), B inject dependencies riêng vào service, C config/PET suppression chỉ là phương án đối chiếu không khuyến nghị. Chưa phương án nào được khóa; cần B-Q01–B-Q04 và prompt thực thi riêng. P4 full save/submit/assessment và G06 assign/mode/stale409 vẫn NOT_RUN sau blocker; không mở P5.
