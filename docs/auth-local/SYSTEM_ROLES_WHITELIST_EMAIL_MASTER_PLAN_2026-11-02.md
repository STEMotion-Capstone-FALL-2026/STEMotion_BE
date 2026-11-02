# Master plan phân quyền hệ thống và email whitelist Google

Ngày 02/11/2026. Trạng thái: **AUDIT VÀ PLAN LOCAL, chưa triển khai các thay đổi trong tài liệu này**.

**Cập nhật sau khi người dùng giao triển khai:** source role/mail, migration rehearsal, contract và
kiểm thử đã được thực hiện local. Xem [báo cáo triển khai](SYSTEM_ROLES_EMAIL_IMPLEMENTATION_2026-11-02.md)
để biết trạng thái từng phase, kết quả và phần gửi mail thật/DB chung/hồ sơ Word còn lại.
Nội dung “chưa triển khai” bên dưới là mốc lúc lập plan, không phải trạng thái source hiện tại.
Baseline checkout: BE `5d0d139`, FE `b8ec3c1`, cộng các thay đổi auth v2 local đang có.
Người dùng đã xác nhận đăng nhập Google thật thành công sau khi sửa launcher local.

## 1. Kết luận và quyết định đề xuất

Tách quyền thành hai cấp: **SystemRole ADMIN/USER** và **WorkspaceRole WRITER/REVIEWER/PRODUCER**.
Leader tiếp tục là cờ bổ sung trong membership, không phải role hệ thống hoặc role sản xuất thứ tư.
Một USER có thể là Writer ở workspace A và Reviewer ở B; người chưa có workspace không có quyền sản xuất.

Giữ whitelist chỉ nhập Gmail. Whitelist là quyền sử dụng Google login, không là nơi gán ADMIN.
User Google mới mặc định USER. Sau lần đăng nhập đầu, Admin có thể nâng tài khoản đó thành ADMIN
trong quản lý người dùng. Local account vẫn được chọn ADMIN/USER lúc cấp. Với tài khoản local đã
link Google, Google login giữ nguyên system role và user ID; không reset ADMIN thành USER.

Thêm email thông báo tự động khi cấp/khôi phục quyền Google, gửi ngoài transaction qua outbox.
Lỗi gửi mail không làm mất whitelist hoặc chặn login. Email không cấp quyền workspace và không có
nút yêu cầu quyền gửi Admin. Không gửi mật khẩu, JWT hoặc Google credential trong email.

Người dùng chưa có domain/dịch vụ mail. Triển khai local preview và fake provider trước; đề xuất
Brevo HTTP API cho gửi thật. Chưa mua domain, tạo tài khoản dịch vụ, ghi secret hoặc gửi email thật
trong lượt lập plan này. Yêu cầu hiện tại kết thúc bằng master plan; source triển khai ở các phase sau.

## 2. Audit hiện trạng FE và BE

| Khu vực đã đọc | Kết quả | Hệ quả |
|---|---|---|
| `User.java`, `WorkspaceMember.java`, `Membership.java` | Dùng chung `User.UserRole` gồm 4 giá trị | Model trộn hai cấp quyền; cần hai enum độc lập |
| `UserService.create`, `AuthDtos.RegisterRequest` | Local mặc định WRITER; DTO nhận cả REVIEWER/PRODUCER | FE chỉ có hai lựa chọn nhưng API/DB vẫn nhận role sản xuất ở cấp hệ thống |
| `GoogleAuthService.exchange` | User Google mới mặc định WRITER; identity đã bind dùng user cũ | Đổi mặc định USER, giữ nguyên explicit linking và ownership |
| `GoogleWhitelistController`, `GoogleWhitelist` | Thêm/thu hồi/khôi phục, không gán role; chưa có mail | Giữ trách nhiệm whitelist; tách service nghiệp vụ để ghi outbox cùng transaction |
| `CustomUserDetailsService` | Authority lấy từ role user hiện tại trong DB | Chuyển sang ROLE_ADMIN/ROLE_USER; không biến membership thành global authority |
| `WorkspaceAccess` | Hầu hết quyền sản xuất đã dựa vào membership; ADMIN bypass nhiều thao tác | Giữ kiểm tra theo scope; thay enum; không xóa lớp phân quyền hiện có |
| `WorkspaceService`, `WorkspaceDtos`, project repository, narration/script services | Nhận enum dùng chung cho role membership, leaderRole và query Reviewer | Phải sửa cả projection, tham số repository, DTO, validation và fixtures |
| `UserService.update/delete` | Chưa có guard bảo vệ Admin hoạt động cuối cùng; update role chưa tăng authVersion | Bổ sung bảo vệ concurrency và chính sách thu hồi phiên khi đổi quyền |
| `V1`, `V17` | Baseline varchar+CHECK; V17 loại workspace ADMIN trên varchar, có nhánh xử lý native enum | Screenshot DB thực tế là `user_role`; không suy luận schema thực tế từ baseline migration |
| FE `types/stem.ts`, `authService`, `App`, `HeaderNav` | Một UserRole chứa cả role nghiệp vụ, admin và library; profile fallback writer | Tách systemRole/workspaceRole/view, bỏ fallback tự coi là Writer |
| FE `AccountProvisionForm`, `AdminPortal`, `GoogleWhitelistPanel` | Nhãn role hệ thống còn Writer/Creator; whitelist chỉ báo trạng thái quyền | Sửa nhãn/bộ lọc; thêm trạng thái mail và gửi lại, không thêm role vào whitelist |
| BE integration/config/pom | Chưa có mail sender/outbox phục vụ luồng này | Cần provider, cấu hình, mẫu mail, worker và API quản trị trạng thái |

Audit này đọc source và hồ sơ local; **không kết nối hoặc kiểm kê DB chung**. Ảnh DB là bằng chứng
về dữ liệu/kiểu hiển thị, không đủ kết luận toàn bộ constraint, RLS, function và grant đang chạy.

## 3. Đối chiếu hồ sơ đăng ký và SRS/SDD

Nguồn trực tiếp:

- Bản gốc `FA26SE054_STEMOTION_AI_ASSISTED_STEM_VIDEO_PRODUCT_hungld.pdf`, phần 3.2(c):
  quản lý user/role Writer, Reviewer, Producer, Admin; 3.2(d): JWT, Google OAuth và RBAC mọi endpoint.
- `STEMotion_BE/docs/deliverables/bm/SWP490_FA26SE054_R2_Registration.docx`: cùng mô tả chức năng
  user/role management, bảo mật và các giao diện theo vai trò.
- SRS mới `Report/Report/STEMotion_Report3_Software_Requirement_Specification.docx`:
  II.4, III.1–2, FR-01–09, FR-48, BR-01–03, BR-20 và ma trận quyền màn hình.
- SDD mới `Report/Report/STEMotion_Report4_Software_Design_Document.docx`:
  core.security, domain.auth/workspace, Database Design và Authentication and Account Provisioning.

**Nhận định kỹ thuật:** việc lưu ADMIN/USER ở user và ba role sản xuất ở membership không bỏ vai
trò/chức năng đã đăng ký. Đề cương không bắt buộc bốn actor phải là một enum trên bảng users.
SRS đã mô tả mỗi membership có đúng một role sản xuất, không có ADMIN; Leader chỉ là cờ bổ sung.
Vì vậy thiết kế hai cấp phù hợp hơn với mô tả workspace và vẫn phải giữ đầy đủ các chức năng của
Writer/Reviewer/Producer, quyền quản trị role và kiểm tra quyền server.

Đây không phải kết luận thay thế việc nhóm/GVHD xác nhận hồ sơ. Cần ghi rõ ánh xạ actor → quyền:
USER là lớp tài khoản nền; Writer/Reviewer/Producer là actor theo workspace; ADMIN là quản trị hệ thống.
Không đổi use case Writer thành User chung rồi mất quyền chuyên môn trong diagram/matrix.

SRS/SDD hiện vẫn ghi email/password, Google ghép existing email, bắt đổi password mọi tài khoản cấp.
Các mô tả này đã lệch auth v2 local và cần sửa cùng lần: username/password; whitelist; Google-only;
explicit linking; password-first-change chỉ cho credential PASSWORD còn tạm; email thông báo mới.
Email là bổ sung tiện ích, chưa phải tính năng được chứng minh có trong đăng ký; ghi là requirement mới.

Một điểm riêng cần giữ minh bạch: SRS ma trận quyền còn ghi **Planned** việc ADMIN không tự quyết
định duyệt nội dung nếu không là Reviewer. Source hiện có ADMIN bypass. Plan này giữ hành vi runtime
đang có và đánh dấu riêng việc siết quyền content approval; không tuyên bố đã hoàn thành requirement
đó chỉ nhờ tách enum. Nếu nhóm triển khai siết quyền, cần phase riêng và xác nhận ma trận nghiệp vụ.
Các project role/project membership trong master plan cũ cũng không được coi đã triển khai ở đây.

## 4. Luồng mục tiêu và phân quyền

| Tình huống | Hành vi mục tiêu |
|---|---|
| Admin cấp local account | Username, tên, password tạm, systemRole USER mặc định hoặc ADMIN; chưa có workspace role |
| Admin thêm whitelist Gmail | Chỉ Gmail; ghi ACTIVE + outbox thông báo; chưa tạo users/identity |
| Google login lần đầu hợp lệ | Check token, challenge, whitelist; tạo Google-only USER; không đổi password lần đầu |
| Gmail ngoài whitelist | 403 ACCOUNT_NOT_IN_SYSTEM; không tạo user và không có request-access |
| Gmail đã bind tài khoản | Dùng user ID/systemRole hiện tại; không áp lại default USER |
| Local user link Google | Reauth password và challenge; giữ role, membership, dữ liệu; không tự merge theo email |
| User vừa đăng nhập chưa có workspace | Màn hình chưa được gán workspace; profile/logout; không mở studio hoặc API sản xuất |
| Admin gán workspace | Chọn user ID + WRITER/REVIEWER/PRODUCER + leader flag; không sửa systemRole |
| Leader sửa role | Chỉ production role trong workspace của mình; không nâng system ADMIN hoặc sửa whitelist |
| Admin nâng/hạ quyền hệ thống | Thao tác user đã tồn tại; audit actor/before/after/reason; tăng authVersion, yêu cầu đăng nhập lại |
| Thu hồi Gmail | Chặn Google login/phiên Google; không tự hạ systemRole, không khóa local credential đã link |
| Khóa user | Chặn mọi phương thức; mail gửi lại/whitelist không vượt qua khóa user |

Không hỗ trợ pending ADMIN trong whitelist ở phiên bản này. Nhu cầu cấp ADMIN trước lần login đầu
có thể xử lý bằng account local ADMIN rồi explicit link Google. Nếu bổ sung invitation có role sau,
đó là nghiệp vụ riêng, cần rõ lúc áp quyền, thay đổi/thu hồi và trường hợp bind existing account.

Bảo vệ ít nhất một ADMIN ACTIVE, bao phủ hạ quyền, khóa/deactivate và xóa tài khoản. Guard phải
serialize mọi thao tác làm giảm tập Admin hoạt động (ví dụ advisory transaction lock cố định dùng
chung), rồi kiểm đếm trong cùng transaction; chỉ khóa từng user không chống được hai Admin cùng
hạ quyền nhau. Không chỉ dựa vào disable nút FE. SystemRole update không chỉnh membership/leader.

## 5. Contract và model đề xuất

### 5.1 Role

- BE `SystemRole {ADMIN, USER}`; `WorkspaceRole {WRITER, REVIEWER, PRODUCER}` độc lập.
- Canonical DB: `users.system_role`; `workspace_members.role` giữ tên nhưng ràng buộc đúng ba giá trị.
  `users.role` cũ chỉ tồn tại ở giai đoạn chuyển đổi, không tiếp tục là nguồn quyền sau cutover.
- DTO auth/profile/user dùng `systemRole`; membership dùng `role: WorkspaceRole`; workspace dùng
  `myRole: WorkspaceRole | null`. Actor/comment metadata không được lấy global role làm vai trò lịch sử.
- Local POST `/api/v1/users` nhận `systemRole`, default USER. Chỉnh quyền qua PATCH user hiện có,
  chỉ ADMIN; version/If-Match hoặc expectedVersion để chống ghi đè thay đổi đồng thời.
- Workspace API từ chối USER/ADMIN trong member role; user API từ chối WRITER/PRODUCER/REVIEWER
  trong systemRole; không tự đoán/mapping request mới của client cũ thành quyền mới.
- Spring authority chỉ ROLE_ADMIN/ROLE_USER; thao tác sản xuất dùng WorkspaceAccess theo resource ID.
  JWT không trở thành nguồn membership; cập nhật session/authVersion và profile từ DB.
- FE tách `SystemRole`, `WorkspaceRole`, `AppView` (admin/library/dashboard/studio); không còn
  `library` trong role. Thiếu/không rõ systemRole phải refetch hoặc báo contract lỗi, không fallback writer.
- Mọi cached profile cũ được bỏ ở cutover; mapping legacy response chỉ được dùng trong adapter có thời hạn,
  không thêm alias nhận role sản xuất ở API quản trị mới. FE và BE chuyển đổi theo đợt phối hợp.

### 5.2 Whitelist và mail

Giữ endpoint cấp quyền hiện có. Di chuyển nghiệp vụ controller → `GoogleWhitelistService`, trong
cùng transaction ghi whitelist + outbox. Khi mail disabled, vẫn cấp quyền; hiển thị rõ chưa bật gửi.

- Bổ sung `grant_revision` trên whitelist để định danh một lần cấp/khôi phục; đổi version do thay đổi
  khác không tạo lại email. Add/restore thực sự mới enqueue; PATCH ACTIVE→ACTIVE không gửi lại.
- Bảng `notification_outbox`: id, whitelist_id, grant_revision, event_type, template_version,
  recipient snapshot, status, attempt_count, next_attempt_at, lease_until, claim_token,
  provider_message_id, error_code đã làm sạch, created_at/updated_at, accepted_at.
- Unique logical event `(whitelist_id, grant_revision, event_type)` chống enqueue trùng; gửi lại do Admin
  là attempt được audit/cooldown, không thêm quyền hoặc tăng grant_revision.
- Tách trạng thái quyền ACTIVE/REVOKED, bind có/chưa và mail. Không dùng “đã gửi mail” thay cho “đã cấp quyền”.
- GET whitelist bổ sung notification summary: DISABLED/PENDING/SENDING/ACCEPTED/RETRY/FAILED/CANCELLED/UNKNOWN,
  lastAttemptAt, nextAttemptAt, canResend. ACCEPTED nghĩa nhà cung cấp nhận yêu cầu, chưa khẳng định vào inbox.
- POST `/api/v1/admin/google-whitelist/{id}/notifications/resend`: ADMIN, whitelist ACTIVE, optimistic
  version/idempotency key; 202 cho enqueue, 409 nếu state không phù hợp, 429 khi cooldown. Không nhận
  email/body tùy ý từ FE. Mặc định cooldown 60 giây và giới hạn resend ngày cấu hình được.
- User đã bind vẫn có thể nhận thông báo restored, nhưng nội dung không hướng dẫn tạo account mới.
  Thao tác thêm trùng trả conflict như hiện tại, không gửi lại ngầm.

## 6. Gửi mail và setup môi trường

### 6.1 Provider và cấu hình

Đề xuất `NotificationSender` với hai adapter: local capture và Brevo HTTP. Không phụ thuộc Gmail API
hoặc quyền gmail.send; không thay scope/token của Google login. Brevo gọi HTTPS
`POST https://api.brevo.com/v3/smtp/email` với API key; SMTP key là credential khác, không dùng ở đây.

Chọn HTTP vì hệ thống đang hướng đến BE trên Render: Render Free chặn outbound 25/465/587.
Chưa kiểm tra plan hosting thực tế của tài khoản người dùng; đây là lý do thiết kế tránh phụ thuộc SMTP.

Biến env dự kiến (BE only, tên sẽ chốt cùng IntegrationProperties):

```dotenv
NOTIFICATION_EMAIL_ENABLED=false
NOTIFICATION_EMAIL_PROVIDER=local
BREVO_API_KEY=
NOTIFICATION_EMAIL_FROM_ADDRESS=
NOTIFICATION_EMAIL_FROM_NAME=STEMotion
NOTIFICATION_EMAIL_REPLY_TO=
APP_PUBLIC_URL=http://localhost:3000
NOTIFICATION_EMAIL_CONNECT_TIMEOUT_MS=5000
NOTIFICATION_EMAIL_READ_TIMEOUT_MS=10000
NOTIFICATION_EMAIL_MAX_ATTEMPTS=5
NOTIFICATION_EMAIL_POLL_INTERVAL_MS=10000
NOTIFICATION_EMAIL_RESEND_COOLDOWN_SECONDS=60
```

Local preview không cần domain/key và không phát email ra Internet. Output chỉ ở thư mục ignored,
không log toàn bộ mail/token; preview dành dev/admin, không public endpoint hoặc API nhận recipient tùy ý.
E2eServer ép provider local/fake và vô hiệu real transport kể cả .env có key thật. Mail enabled với
provider thật nhưng thiếu from/key/URL HTTPS ở deployment phải báo cấu hình rõ; không silently fake-send.

Người dùng setup ngoài ứng dụng ở phase gửi thật: tạo tài khoản dịch vụ, bật transactional sending,
tạo API key, xác minh sender; nhập key vào BE local/deployment secret. Kiểm tra điều kiện/quota/phê
duyệt tài khoản theo provider lúc setup, không giả định đăng ký xong sẽ gửi được ngay.

Người dùng chưa có domain: chưa bắt mua để làm local. Muốn gửi chuyên nghiệp bằng thương hiệu,
cần domain sở hữu/quản lý được, sender ví dụ no-reply@domain đó và xác thực DNS theo provider
(Brevo code, DKIM, DMARC; chỉ thêm SPF khi hướng dẫn của cấu hình thực tế yêu cầu). Không dùng
domain @gmail.com như domain do STEMotion sở hữu. Nếu thử sender không có domain riêng, phải kiểm tra
provider có cho phép sender đó không; không cam kết inbox/deliverability hoặc tự dùng Gmail cá nhân.
Domain mail và domain app có thể khác; URL app dùng hostname HTTPS cố định đã deploy.

### 6.2 Reliability

Worker claim batch bằng row lock SKIP LOCKED + lease/claim token, commit claim trước network call;
không giữ DB transaction trong lúc gọi provider. Restart phục hồi lease hết hạn, nhiều instance không
cùng claim một job. Retry backoff có jitter, tối đa 5 attempts, tôn trọng Retry-After cho 429.
4xx lỗi cấu hình/recipient là terminal; 429/5xx lỗi tạm được retry. Timeout sau submit chưa biết provider
đã nhận hay chưa phải ghi UNKNOWN, không tuyên bố exactly-once; nếu provider có idempotency được
xác nhận thì dùng event key ổn định. Local dedupe không loại được hoàn toàn mail trùng qua network.

Trước dispatch kiểm tra whitelist vẫn ACTIVE và grant_revision còn đúng; revoke hủy pending/retry cũ,
restore tạo grant mới; không backfill gửi tất cả whitelist cũ khi bật feature. Đối với revoke xảy ra
sau khi request mail đã đi, không thể bảo đảm thu hồi email; nội dung ghi quyền có thể đã thay đổi,
quyền truy cập luôn kiểm tra lại lúc login. Worker phải không retry vô hạn job đã revoke.

Trên Render Free, service ngủ thì scheduler không chạy. Job vẫn bền trong Postgres và tiếp tục khi
service thức; UI báo pending, không hứa gửi ngay. Nếu cần SLA gửi ngay, dùng worker luôn chạy hoặc
scheduler/queue phù hợp deployment. Không dùng HTTP request chờ gửi mail để che giới hạn này.

Không coi accepted là delivered. Delivery/bounce webhook có xác thực và chống replay là phase nâng
cao; v1 có thể chỉ hiển thị nhà cung cấp đã tiếp nhận, lỗi hoặc đang chờ. Log theo event ID, không key,
password, credential hoặc full email; policy retention job/recipient đề xuất 30 ngày sau terminal,
metadata audit quyền giữ theo policy dự án. Outbox không được public/Supabase anon đọc hoặc ghi.

### 6.3 Nội dung email và UX

Subject: “STEMotion — Bạn đã được cấp quyền đăng nhập bằng Google”. Body tiếng Việt, HTML escape,
template do BE quản lý; chỉ gửi từng recipient, không CC/BCC danh sách whitelist.
Thông báo Gmail đã được phép; nút “Mở STEMotion” trỏ APP_PUBLIC_URL cố định; hướng dẫn chọn Google.
Nêu rõ chưa tự có workspace. Nếu đã được cấp username/password, đăng nhập tài khoản đó và liên kết
Google trong hồ sơ trước khi dùng nút Google để tránh tạo tài khoản riêng. Không hứa auto-merge.
Mail là thông báo, không có OTP/accept-invitation token, không thay nonce/challenge flow.

FE cấp whitelist thành công: “Đã cấp quyền Google. Email thông báo đang chờ gửi.” Nếu mail disabled:
“Đã cấp quyền Google. Chưa bật email thông báo.” Nếu mail lỗi, quyền vẫn ACTIVE, có gửi lại với cooldown.
Restore gửi thông báo khôi phục, revoke không tự gửi thư ở v1; email thu hồi có thể bổ sung sau.

## 7. Migration DB và cutover

Không sửa Flyway V1/V17 hoặc nhét thay đổi vào V9000 fixture như một migration production.
Auth v2 drafts chưa áp DB chung: phối hợp thứ tự với role/mail drafts, chọn version mới chưa dùng
sau khi sync repo. Kiểm thử cả schema từ migrations và native-enum baseline tương tự ảnh DB.

1. Preflight SQL read-only chuẩn bị local: columns/types/defaults/checks; ai dùng `user_role`; RLS,
   trigger/function/view/index/grant; thống kê role/status, Admin ACTIVE, workspace role bất thường,
   orphan/null membership; dependencies các auth-v2 scripts còn dùng users.role. Chỉ chạy DB thật ở rollout.
2. Backup có phục hồi kiểm chứng; baseline mapping userId→legacyRole/systemRole, checksum FK/ownership;
   không export dữ liệu/credential vào repo. Tạo rehearsal trên DB cô lập, không ép DB chung vào test.
3. Expand `users.system_role` với kiểu varchar+CHECK độc lập. Backfill ADMIN→ADMIN;
   WRITER/REVIEWER/PRODUCER→USER. Giá trị lạ/null dừng migration và báo finding, không tự nâng quyền.
4. Giữ workspace role/is_leader/status đúng dữ liệu hiện có. Không suy role workspace từ users.role
   và không tự gán workspace cho user chưa có. Membership ADMIN bất thường phải xử lý mapping có
   rà soát; không tự gán WRITER hoặc leader trong migration mới.
5. Nếu workspace role đang dùng native `user_role` chung, chuyển riêng column sang varchar+CHECK ba
   giá trị, xử lý default/cast/dependencies; không ALTER TYPE user_role để thay hàng loạt cả hai bảng.
   Giữ legacy type đến khi không còn dependency; không DROP CASCADE để né lỗi.
6. Thêm grant_revision/outbox, indexes, permissions. RLS/grant bảo đảm chỉ BE/service được truy cập.
   Thêm NOT NULL/default USER sau khi kiểm tra backfill; đối chiếu số user/membership, IDs và ownership.
7. Cutover phối hợp FE/BE trong maintenance ngắn, dừng writer cũ; source mới chỉ đọc system_role.
   Bump authVersion/clear profile cache để phiên cũ đăng nhập lại; email dispatch vẫn OFF. Không cho
   old BE còn ghi users.role sau khi đã copy dữ liệu sang system_role.
8. Smoke local/staging, mở login, thử role/workspace trước; sau đó mới bật gửi mail với sender đã xác minh.
   Bật flag không tự gửi cho toàn bộ dữ liệu quá khứ. Legacy users.role drop ở cleanup migration riêng.

Rollback trước mở ghi: trả ứng dụng cũ + snapshot/mapping. Sau khi có đổi quyền mới, không copy
ngược USER về legacyRole cũ rồi vô tình khôi phục quyền; cần stop writes, reconcile audit mapping,
backup và runbook rõ. Rollback không thể thu hồi email đã gửi. Tạm dừng dispatcher không hủy whitelist.

## 8. Phase triển khai và nghiệm thu

| Phase | Công việc và đầu ra | Điều kiện hoàn thành |
|---|---|---|
| P0 Chốt contract | Role matrix, ADR whitelist không gán ADMIN, contract API, mapping actor hồ sơ, inventory phụ thuộc DB | Không còn chỗ mơ hồ giữa systemRole, workspaceRole và view; các điểm chưa biết được ghi rõ |
| P1 Data rehearsal | SQL expand/backfill/validate/rollback cho varchar và enum; outbox schema; fixtures | IDs/FK/hash/Google links và membership không đổi; unexpected roles fail rõ; rehearsal pass |
| P2 BE roles | Hai enum, DTO/projection/repository, UserService/GoogleAuthService, auth/session, WorkspaceAccess; Admin cuối cùng + audit/version | Role hệ thống chỉ ADMIN/USER; mọi API scope đúng workspace; session cũ không giữ quyền sau đổi |
| P3 FE roles và admin | Types/services/cache, App/HeaderNav, account form, admin list/filter/edit, membership UI, no-workspace state | Account chỉ chọn USER/ADMIN; role sản xuất chỉ trong workspace; không còn fallback Writer |
| P4 Mail local | Whitelist service transaction + outbox + worker lease/retry; fake/capture provider; template, admin summary/resend UI | Thêm/restore enqueue đúng; revoke chặn stale job; mail fail không mất quyền; E2E không gửi thật |
| P5 Provider setup | Brevo HTTP adapter/config docs, key/sender validation, domain runbook, production URL; sender/domain do chủ tài khoản setup | Local integration fake pass; gửi thử thật chỉ tới Gmail test được chỉ định sau khi cấu hình đủ |
| P6 Integration và hồ sơ | BE/FE/E2E/regression matrix; chỉnh SRS/SDD/BM change record, API/OpenAPI, ERD và runbook | Báo cáo phân biệt local fake/Google thật/mail thật; trạng thái hồ sơ đúng bằng chứng |
| P7 Rollout | Preflight/backup thật, cutover có giám sát, roles trước mail sau; cleanup riêng | Role/no-workspace/link/revoke/admin và notification smoke pass; rollback reviewable |

Thứ tự bắt buộc: P0→P1→P2→P3; P4 dựa contract P0/P1 và có thể làm sau P3; P5 không chặn local P4.
P6 phải bao phủ P2–P5, P7 chỉ sau rehearsal và có môi trường rollout được chỉ định. Phase hiện tại chỉ
lập plan; không tự chạy migration DB chung hoặc gửi thư thật cho các Gmail đã nhập trước đó.

## 9. Ma trận kiểm thử bắt buộc

| Nhóm | Ca kiểm tra quyết định |
|---|---|
| Auth | Local USER/ADMIN; Google mới USER; existing linked ADMIN giữ quyền; whitelist/revoke; Google không bắt đổi password |
| Scope | Cùng user Writer ở A/Reviewer ở B; không có membership bị 403; đổi workspace không giữ quyền cũ; library view không là role |
| Input | System DTO từ chối role sản xuất, workspace DTO từ chối ADMIN/USER; profile không sửa role; Leader không nâng systemRole |
| Admin | Promote/demote, status/delete, self-demotion khi còn Admin khác; hai request đồng thời không làm mất Admin ACTIVE cuối |
| Session | Đổi role/logout cache; authVersion cũ bị từ chối; membership đổi có hiệu lực request sau; không dựa JWT role cũ |
| Data | Varchar và native enum; shared enum dependencies/RLS; giữ ID/hash/identity/owner/leader/membership; mapping lạ rollback |
| Mail transaction | Rollback whitelist không có job; add duplicate không mail; ACTIVE→ACTIVE không mail; restore enqueue một grant |
| Worker | Concurrent claims, crash trước/sau network, stale lease token; timeout UNKNOWN; retry 429/5xx, terminal 4xx, max attempts |
| Race | Revoke lúc pending, revoke→restore, resend đồng thời, job grant cũ không gửi lại; mail đã dispatch có giới hạn thu hồi rõ |
| FE | Badge quyền/bind/mail độc lập; resend cooldown; mail disabled/failure giữ quyền; pending polling có giới hạn và cleanup |
| Security | Recipient/template do BE quyết định; HTML injection; provider key không ra FE/log; public client không đọc outbox |
| Regression | Whitelist default GET/paging/status và username Chrome pattern; explicit linking race; query count; script/review/render/publish scope |
| External | Google thật đã được người dùng xác nhận; mail thật là ca riêng: provider accepted + recipient kiểm tra inbox/spam; không fake kết quả |

Chỉ chạy suite phù hợp thay đổi từng phase; integration cuối cần toàn bộ nhóm auth/role/workspace,
negative scope và nghiệp vụ sản xuất có ảnh hưởng. Không chỉ chứng minh bằng build hoặc đổi nhãn.

## 10. Cập nhật hồ sơ và tài liệu kỹ thuật

- SRS: actor inheritance, FR-01–09, FR-48, BR-01–03/20, màn hình phân quyền; thêm requirement email
  add/restore/resend/failure/delivery semantics và nội dung no-workspace. Dùng ID mới sau kiểm tra ID hiện có.
- SDD: class diagram hai enum, schema users/membership/whitelist/outbox, sequence login/link/role change,
  dispatch/retry/revoke; deployment giới hạn service sleep và secret chỉ ở BE.
- BM03/record of changes: ghi thay đổi phân tầng quyền và email thông báo; không sửa bản đăng ký gốc
  để làm như thiết kế chi tiết mới đã được phê duyệt từ đầu. BM02/BM04 cập nhật theo test evidence.
- OpenAPI/auth-v2 contract, examples/env templates, seed/fixtures/E2E, SQL auth-v2 drafts tham chiếu role
  phải được đồng bộ. Chỉ sửa bản báo cáo được nhóm coi authoritative, đồng bộ bản deliverables rõ version.
- Lưu test report và migration runbook mới; không ghi mail đã delivered hay native DB đã tương thích khi
  chỉ có fake-provider/local-baseline evidence.

## 11. Nguồn tham chiếu kỹ thuật

- [Render Free limitations](https://render.com/docs/free): SMTP outbound và service ngủ khi idle.
- [Brevo transactional email API](https://developers.brevo.com/docs/send-a-transactional-email): HTTPS
  endpoint, API key, sender, recipient và message ID; không coi API acceptance là inbox delivery.
- [Brevo domain authentication](https://help.brevo.com/hc/en-us/articles/12163873383186-Authenticate-your-domain-with-Brevo-Brevo-code-DKIM-DMARC):
  cần kiểm soát domain, cấu hình DNS theo provider; không authenticate domain Gmail do người khác sở hữu.

Các lựa chọn outbox, enum, cutover, guard Admin và retry ở trên là thiết kế đề xuất cho STEMotion,
không phải khẳng định provider hoặc hồ sơ gốc đã quy định những chi tiết đó.


This is a repository copy of the workspace audit/report. Local paths and runtime counts are
verification evidence, not production deployment configuration.
