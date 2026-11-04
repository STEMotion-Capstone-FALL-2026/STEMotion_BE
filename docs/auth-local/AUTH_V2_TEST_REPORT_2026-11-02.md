# Kết quả xác minh auth v2 trên local

Ngày 02/11/2026, Asia/Saigon. Người dùng đã cho phép chạy test trong khi chuẩn bị OAuth Client ID.
**Kết quả cuối: BE compile thành công, FE typecheck/build thành công, 73 test BE + 31 test FE + 23 E2E pass.**
Đây là nhóm kiểm thử được chọn theo phạm vi auth và regression liên quan, không phải toàn bộ test trong dự án.

**Cập nhật sau khi người dùng điền Client ID:** đã xác nhận cấu hình FE/BE có giá trị, khớp nhau và đúng
định dạng mà không in giá trị. Đã mở một môi trường manual smoke mới tại localhost:3000 → localhost:18080,
với DB embedded mới và `e2e.google.fake=false`. HTTP check: enabled=true, fake credential bị verifier thật
từ chối 401 GOOGLE_TOKEN_INVALID, local admin login 200, FE 200. Môi trường mới này đang được giữ chạy
để người dùng thêm Gmail và thử popup; chưa có bằng chứng đăng nhập thành công bằng Google thật.
Không thay flags trong .env hoặc kết nối DB dùng chung. Hướng dẫn ở
[real Google local](../AUTH_V2_REAL_GOOGLE_LOCAL.md).

## Phạm vi và môi trường

- BE Java 22 trên máy, compile theo target Java 17 của project; Maven 3.9.9 cache, chạy offline. Maven Wrapper hiện có lỗi manifest nên không dùng wrapper.
- FE Node/npm theo môi trường máy, dependency được cài lại bằng `npm ci --ignore-scripts --no-audit --no-fund` đúng package-lock hiện có; Vitest 5.0.3, Playwright 1.56.1. Không đổi package.json/lockfile.
- JPA query-count dùng H2 riêng. Concurrency và rehearsal dùng PostgreSQL 16.2 embedded mới; E2eServer tạo PostgreSQL embedded riêng và chỉ nghe HTTP localhost:18080.
- Không kết nối hoặc migrate Supabase/DB dùng chung, không đọc/sửa secret Google thật, không push hoặc deploy. AI/render trong E2E là fake test-scope, không gọi dịch vụ trả phí.
- Backend test và DB đã được dừng sau lượt xác minh. Các SQL ở deploy/auth-v2-drafts vẫn nằm ngoài production Flyway.

## Kết quả cuối

| Nhóm | Kết quả | Bằng chứng local |
|---|---|---|
| BE production + test compilation | Pass | Maven test-compile và các lượt test sau cùng exit 0 |
| FE typecheck + production bundle | Pass | `.audit/auth-v2-fe-build.log` |
| BE auth/security/workspace/JPA/concurrency/migration | 73 tests, 0 failures, 0 errors, 16 classes | `STEMotion_BE/target/surefire-reports/TEST-*.xml` theo danh sách bên dưới |
| FE service/component/App regression | 31 tests, 6 files, pass | `.audit/auth-v2-fe-tests.log` |
| Playwright auth và WF-01–04 | 23 tests, pass | `.audit/auth-v2-e2e-tests.log`, lượt cuối 1.1 phút |
| OpenAPI runtime export | Đã xuất từ E2eServer local | `STEMotion_BE/docs/api/auth-v2-local-openapi.json` |
| Google consent/login với OAuth thật | Chưa chạy | Chờ Client ID/origins và Gmail test của người dùng |
| Migration dữ liệu thật và cutover | Chưa chạy | Cần mapping/backup/rehearsal trên bản sao schema thực tế |

16 test classes BE: AuthRateLimiterTest (1), AuthSessionVersionTest (1), JwtServiceTest (5),
ResourceAccessInterceptorTest (9), AccountProvisioningAccessTest (7), AuthControllerTest (2),
AuthMigrationRehearsalTest (1), GoogleAuthPolicyTest (4), RegistrationPolicyTest (4),
UsernameLoginTest (4), UserProvisioningTest (2), WorkspaceServiceTest (12),
GoogleSignatureVerifierTest (1), GoogleSignInTest (5), PostgresConcurrencyTest (7), QueryShapeJpaTest (8).
Số 73 là tổng test khác nhau theo báo cáo cuối, không cộng các lượt chạy lại để tăng số lượng.

6 file FE: authService.test.ts, adminProvisioning.test.ts, workspaceService.test.ts,
CreateWorkspaceForm.test.tsx, ManageMembersModal.test.tsx, App.ux.test.tsx.

## Các hành vi đã có bằng chứng chạy

- Username chuẩn hóa, không dùng Gmail làm username, không fallback lookup contact email; invalid credentials không làm ghi đè phiên hiện có.
- Password session bị yêu cầu đổi mật khẩu tạm; token được thay trước khi bootstrap. Google session không bị ép đổi password local.
- Gmail ngoài whitelist trả 403 ACCOUNT_NOT_IN_SYSTEM, không phát phiên và không tạo identity/user.
- Google-only đăng nhập qua callback thật của component FE với GIS được stub từ Playwright; profile sửa tên hoạt động và không có password prompt.
- Local account link Gmail qua profile sau reauth; Google login sau đó vào cùng userId.
- Gmail thuộc user khác không bị merge; user thường không quản lý được whitelist.
- Revoke làm Google JWT bị từ chối ở request kế tiếp; PASSWORD JWT của account đã link tiếp tục dùng được.
- Hai first Google login đồng thời cùng Gmail/sub vào cùng user; challenge đã consume không dùng lại được.
- Chữ ký RSA và audience được kiểm tra bằng verifier production với key fixture riêng, không dùng fake verifier để kết luận chữ ký đúng/sai.
- Workflow onboarding, script/review, production/render và publish/library vẫn đi qua các case E2E sẵn có; render/AI ở đây là test double, không phải bằng chứng render video thật.

## Rehearsal SQL migration

AuthMigrationRehearsalTest dựng DB từ V1–V22, thêm user ADMIN fixture rồi thực thi trực tiếp các SQL draft:

1. Mở rộng users và tạo whitelist/identity/challenge.
2. Mapping rỗng bị từ chối, rollback không để lại username được ghi một phần.
3. Mapping hợp lệ backfill username, giữ nguyên userId và password_hash của các user fixture.
4. CHECK constraint được thêm sau backfill và từ chối local credential không có username.
5. Import legacy Google đã có approval mapping giữ cùng userId, không tạo user mới hoặc đổi owner.

Đây là schema dựng từ migration repo, không phải bản sao DB production. Chưa chứng minh mọi trigger,
grant/RLS hoặc dữ liệu lệch lịch sử trên Supabase thực tế đều tương thích. Mapping username thật và
legacy Google approval vẫn phải được nhóm rà trước cutover. Các lỗi SQL do test cố ý tạo (mapping
thiếu, vi phạm CHECK) xuất hiện trong log là negative cases đạt, không phải migration production lỗi.

## Lỗi phát hiện và đã sửa trong quá trình test

1. FE build bị chặn bởi AssetPreview có sẵn đang thiếu prop `frame` của ComposedScene. Bổ sung duy nhất
   `frame="center"`, giữ các thay đổi asset khác của người dùng.
2. FE bootstrap gọi workspace API dù phiên đang bắt đổi password. Thêm return sau khi mở modal; chỉ
   tải workspace sau khi đã lưu JWT thay thế. E2E onboarding sau sửa pass, không còn lỗi bootstrap này.
3. Mapping inverse OneToOne từ User sang GoogleIdentity gây truy vấn phụ khi tải author/history/comment,
   làm ba test query-count thất bại. Chuyển googleIdentity trên User thành projection transient nạp rõ
   qua query join ở đường auth; admin/member/candidate được hydrate theo batch cố định. Không tăng
   giới hạn query của test để che lỗi. Tất cả test query-count cũ pass; test mới xác nhận user + Google
   identity + whitelist nạp trong một câu lệnh.
4. Test môi trường Windows: Java shim trên PATH bị treo; chạy trực tiếp JAVA_HOME/bin/java.exe.
   Sandbox chặn socket localhost `getsockopt`; sau khi dùng quyền chạy local test được duyệt, PostgreSQL
   embedded và Chromium chạy bình thường. Không đổi sang DB chung để né lỗi môi trường.

## Regression sau khi thử giao diện whitelist

Ảnh thử thủ công phát hiện hai lỗi chưa được các ca trước bao phủ: GET danh sách whitelist mặc định
trả 500 và HTML pattern username không hợp lệ trên Chrome dùng cờ `v`.

- BE: với PostgreSQL `stringtype=unspecified`, điều kiện `:status is null` không xác định được kiểu
  parameter khi bỏ bộ lọc. Thay bằng cờ boolean `allStatuses` và luôn truyền status có giá trị.
- FE: escape dấu gạch ngang trong character class của pattern ở login và form cấp tài khoản.
- Bổ sung test PostgreSQL thật cho danh sách mặc định, kết quả rỗng, phân trang/count, lọc ACTIVE
  và REVOKED: **1 test pass** (`PostgresConcurrencyTest#whitelistPagingSupportsDefaultAndFilteredStatus`).
- Bổ sung Playwright Chromium mở tab whitelist mặc định (HTTP 200), thêm/tìm Gmail, thu hồi/khôi phục
  và kiểm tra không có lỗi regex hoặc HTTP 500: **1 test pass** (`e2e/whitelist-ui.spec.ts`). Gmail fixture
  được thu hồi sau test. FE `npm run build` (TypeScript + Vite) pass, còn cảnh báo bundle lớn.
- Backend thử Google thật đã được khởi động lại sau sửa, phục hồi dữ liệu DB tạm từ snapshot local.
  Không áp migration vào DB chung. Kiểm thử này dùng password admin fixture, chưa xác nhận popup/token
  Google thật của người dùng. Các tổng số test ở trên là kết quả đợt trước, không phải toàn bộ suite
  đã chạy lại sau bản sửa này.

## Giới hạn và bước tiếp theo

Lần thử Google thật tiếp theo phát hiện launcher `E2eServer` truyền Client ID hai lần (mặc định
rỗng và override từ .env). Spring ghép các giá trị command-line trùng tên bằng dấu phẩy, làm
audience không khớp và trả `GOOGLE_TOKEN_INVALID` trước khi đọc whitelist (`q=0` trong log).
Đã sửa launcher loại mặc định bị override, thêm `E2eServerArgumentsTest`: 1 test pass, xác nhận
Client ID nhận đúng một giá trị và datasource vẫn là embedded fixture. Backend local được khởi
động lại, snapshot mới giữ cả Gmail người dùng vừa thêm. Cần thử lại Google thật sau bản sửa này;
chưa coi đăng nhập Google thật đã pass.

- Cần Google Web Client ID, branding/audience, Authorized JavaScript origins và Gmail test để thử
  popup/consent/token thật. Client secret/API key/service-account không thuộc flow ID-token này.
- In-memory limiter được test riêng; E2eServer tăng ngưỡng cho các fixture chạy chung một IP. Chưa có
  load test hoặc kiểm thử trusted-proxy/client-IP trên host triển khai.
- FE build còn cảnh báo bundle lớn, không gây build failure. Test query-count không thay thế đo tải.
- Chưa chạy toàn bộ test project, không smoke-test DB được cấu hình bởi .env, không cập nhật Word/BM
  thành Implemented chỉ dựa vào local test. OpenAPI snapshot là local runtime, không phải production.
- Trước rollout: mapping/backup/schema thực tế, Google thật trên staging, báo cáo/hình SRS/SDD và runbook
  cutover phải được hoàn thiện. AUTH_V2_ENABLED và GOOGLE_LOGIN_ENABLED trong template vẫn mặc định false.

Contract và tiến độ: [Auth v2 contract](../api/AUTH_V2_CONTRACT.md),
[tiến độ source](AUTH_V2_TEST_REPORT_2026-11-02.md),
[migration drafts](../../deploy/auth-v2-drafts/README.md).


This is a repository copy of the workspace audit/report. Local paths and runtime counts are
verification evidence, not production deployment configuration.
