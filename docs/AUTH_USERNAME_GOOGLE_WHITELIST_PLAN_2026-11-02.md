# Kế hoạch refactor đăng nhập: username/password, Google theo whitelist Gmail, hồ sơ người dùng

Ngày: 02/11/2026 (Asia/Saigon). Bản 2: đã review và chỉnh sửa trên bản plan đầu.
Trạng thái: **PLAN — chưa triển khai, chưa build/test, chưa migration**.

## 0. Kết quả review bản plan đầu

**Kết luận:** hướng thiết kế đúng và an toàn: `users.id` giữ làm gốc sở hữu dữ liệu, không tự ghép tài khoản theo email, whitelist là quyền dùng Google chứ không phải lời mời, có preflight và rollback cho migration. Có thể triển khai sau khi bổ sung các điểm dưới đây. Các mục đánh dấu **[mới]** hoặc **[sửa]** trong tài liệu là phần thay đổi so với bản đầu.

| # | Nhận xét | Đã xử lý ở |
|---|---|---|
| R1 | Baseline cũ (BE `97b0651`, FE `aa4d722`). Hiện tại BE `5d0d139`, FE `b8ec3c1`. Thay đổi liên quan: sửa render (REQUIRES_NEW); bộ Playwright E2E; `E2eServer`/`E2eFakes`; FE gọi lại `bootstrapFromApi()` sau khi đổi mật khẩu lần đầu. | §3 |
| R2 | Thiếu cập nhật **test E2E Playwright** (đang provision và login bằng email) và **14 file test BE** đang dùng email. Không sửa thì CI E2E (link báo cáo gửi thầy) sẽ đỏ ngay. | §11.1 |
| R3 | Google login **không tự động test được trên CI** nếu dùng Google thật. Cần thêm verifier giả trong `E2eServer` (giống `FakeGemini`) để test whitelist, liên kết và revoke tự động. | §11.2 |
| R4 | Thiếu thứ tự cutover cho **tài khoản Admin**: nếu Admin chưa có username trước khi bật auth v2 thì không ai vào được trang quản trị để sửa mapping. | §9.2 bước 0 |
| R5 | Login với username không tồn tại phải **chạy BCrypt giả** để thời gian phản hồi như nhau, tránh dò tài khoản qua thời gian. | §2, §6.1 |
| R6 | BE **chưa có rate limiter** (không có bucket4j hay tương đương). Plan cần chọn cách làm: đề xuất bộ đếm trong bộ nhớ theo IP + username, đủ cho một instance Render.com. | §2 |
| R7 | Đánh số theo **Report 3 chính thức** (bản docx của Lê, UC-01…UC-34, FR-01…FR-55): FR mới bắt đầu từ **FR-56**. Thêm use case "Quản lý whitelist Google" và "Cài đặt hồ sơ" thì **phải cập nhật use case diagram** (file drawio của nhóm), nếu không SRS lại lệch diagram. | §12 |
| R8 | Tác động lên form Review 2: BM01 F-01 (tiêu chí nghiệm thu đang ghi email), DS-01a/DS-01b, G-06 (nút Google) và BM04 RF. Phiếu đăng ký **không cần BM03**, vì phiếu gốc ghi "JWT sessions and Google OAuth 2.0" và không quy định định danh là email. | §12 |
| R9 | Đường dẫn tài liệu đã đổi: SRS/SDD nằm ở `docs/deliverables/reports/`. | §13 |
| R10 | Mỗi request đã tra DB qua `userDetailsService.loadUserByUsername(userId)`; các kiểm tra authVersion/status/whitelist nên gộp vào **cùng truy vấn đó** (join identity + whitelist) để giữ số query không đổi (NFR-03). | §5 |
| R11 | Principal hiện tại là `subject = userId`, và các `@PreAuthorize` dùng `authentication.name` như userId. **Giữ nguyên**; chỉ bỏ claim `email`. | §5 |
| R12 | Thời điểm: refactor lớn (schema, JWT, FE, tài liệu). Đề xuất triển khai **sau Review 2**, chia hai mốc (MVP và hoàn thiện) và bật bằng feature flag, để demo Defense 1.1 không phụ thuộc vào migration dữ liệu thật. | §10 |
| R13 | Challenge LOGIN có cả `nonce` lẫn `challengeSecret`: giữ được, nhưng ghi rõ lý do (secret buộc token phải được đổi tại đúng trình duyệt đã xin challenge). Nếu cần rút gọn ở MVP: chỉ nonce dùng một lần, TTL 5 phút. | §4.4 |
| R14 | Thêm các câu hỏi cần nhóm chốt trước khi code (§14), thay vì để ẩn trong plan. | §14 |

## 1. Mục tiêu và ranh giới

Thay đăng nhập email/password bằng **username/password**; đăng nhập Google qua **whitelist Gmail**; thêm **hồ sơ** để đổi tên, xem phương thức đăng nhập và liên kết Google với tài khoản nội bộ. Quyền và dữ liệu tiếp tục gắn với `users.id` hiện có. Sửa đồng bộ FE, BE, contract API, migration, test và tài liệu (SRS/SDD/BM).

Yêu cầu của người dùng là căn cứ ưu tiên:
- tài khoản là username thường, không có dạng email;
- whitelist chỉ nhập Gmail;
- Gmail ngoài whitelist nhận thông báo "Tài khoản không nằm trong hệ thống";
- không có chức năng xin quyền Admin;
- đăng nhập Google không bị bắt đổi mật khẩu lần đầu.

Ranh giới:
- Không push, deploy, hay chạy migration trên DB dùng chung khi chưa được duyệt. Supabase production chỉ được đọc.
- Không sửa migration đã chạy (V1–V22).
- Không mở rộng sang YouTube OAuth.

## 2. Quyết định thiết kế

| Chủ đề | Quy tắc |
|---|---|
| Tài khoản local | Admin cấp username + mật khẩu tạm + họ tên + role. Username không phải email, không chứa `@`. |
| Username | Trim, chữ thường ASCII, 3–32 ký tự, regex `^[a-z0-9][a-z0-9._-]{2,31}$`; lưu dạng chuẩn; không đổi được trong profile v1. |
| Gmail | Chỉ domain `gmail.com` chính xác; trim, lowercase; không bỏ dấu chấm hay `+tag`; không quy đổi `googlemail.com` hay Workspace. |
| Whitelist | Bảng riêng, Admin chỉ nhập Gmail. Thêm vào whitelist không tạo user. |
| Tạo user Google | Lần đăng nhập Google đầu tiên (token hợp lệ, whitelist ACTIVE, chưa có identity): tạo user Google-only, role hệ thống **WRITER**, không tự cấp workspace hay ADMIN. |
| User Google-only | `username` và `password_hash` null; tên lấy từ claim `name` hợp lệ, không có thì dùng "Người dùng STEMotion". |
| Local có Google | Một user ID: một username và tối đa một Google identity. Liên kết làm trong profile, không làm ở trang login. |
| Ngoài whitelist | 403 `ACCOUNT_NOT_IN_SYSTEM` "Tài khoản không nằm trong hệ thống."; không tạo gì, không có nút xin quyền. |
| Không tự ghép | Không ghép theo email cũ. Google subject đã thuộc user khác thì trả 409, giữ nguyên cả hai user. |
| Thu hồi whitelist | Chặn đăng nhập Google và phiên Google từ request kế tiếp; không khóa đăng nhập username/password. |
| Tài khoản khóa | INACTIVE/BANNED chặn mọi kiểu đăng nhập và mọi phiên; whitelist không vượt qua trạng thái user. |
| Mật khẩu lần đầu | Chỉ bắt đổi với phiên PASSWORD khi còn mật khẩu tạm. Phiên GOOGLE không bị bắt; đăng nhập Google không xóa cờ mật khẩu tạm. |
| **[mới]** Chống dò tài khoản | Username không tồn tại: vẫn so BCrypt với một hash giả, trả chung `INVALID_CREDENTIALS`. |
| **[mới]** Rate limit | Bộ đếm trong bộ nhớ (cửa sổ trượt) theo IP + username cho `/auth/login`, `/auth/google`, `/auth/google/challenge`, reauth ở profile; vượt ngưỡng trả 429 `TOO_MANY_ATTEMPTS`. Đề xuất 10 lần / 5 phút; nhóm chốt ngưỡng (§14). Một instance nên chưa cần Redis. |
| **[mới]** Admin dự phòng | Trước cutover phải có ít nhất **một Admin có username + mật khẩu local** đã kiểm tra đăng nhập được (§9.2 bước 0). Admin không phụ thuộc Google. |
| Profile v1 | Đổi tên; xem username, Gmail đã liên kết, phương thức đăng nhập; liên kết Google. Không đổi username, role, status. |

Regex username, thời điểm tạo user Google và role mặc định là lựa chọn của plan này, chưa phải đặc tả trong SRS; sẽ ghi vào SRS khi chốt (§12).

## 3. Hiện trạng (đối chiếu lại, baseline BE `5d0d139`, FE `b8ec3c1`) **[sửa]**

| Khu vực | Hiện trạng | Việc cần làm |
|---|---|---|
| `AuthDtos.LoginRequest` | `email` có `@Email` | Đổi sang `username`, dùng chung validator với provisioning |
| `AuthService` | Local: `findByEmail` (dòng 37); Google: tìm theo email rồi ghi `googleId` | Tách lookup theo username, Google login và liên kết chủ động |
| `UserService.create` | `existsByEmailIgnoreCase` (dòng 27), DTO `RegisterRequest` có email | DTO cấp local mới, username bắt buộc |
| `JwtService` | `subject = userId`, claim `email`, `role` | **Giữ `subject = userId`**; bỏ claim email; thêm `authMethod`, `authVersion`, `googleIdentityId` |
| `JwtAuthenticationFilter` | Mỗi request gọi `loadUserByUsername(userId)` (đã tra DB) | Kiểm tra status, authVersion, identity, whitelist trong **cùng truy vấn** |
| `ResourceAccessInterceptor` | Chặn theo `user.mustChangePassword` cho mọi phiên | Chặn theo `requiresPasswordChange` của phiên |
| `GoogleTokenVerifier` | Gọi `oauth2.googleapis.com/tokeninfo` | Thay bằng thư viện xác minh chữ ký có cache public key (`google-api-client` `GoogleIdTokenVerifier`), có timeout, phân loại lỗi |
| `WorkspaceDtos` | Member/Candidate mang email | Thêm `username` nullable; thao tác theo userId |
| FE `LoginScreen` | `type=email`, chưa gắn nút Google | Ô username + nút Google + một luồng hoàn tất đăng nhập |
| FE `GoogleSignInButton` | Tự nạp SDK trong component, initialize toàn cục | Một SDK/controller dùng chung cho LOGIN và LINK |
| FE `App.tsx` | Sau khi đổi mật khẩu lần đầu gọi `bootstrapFromApi()` (bản sửa ngày 01/11) | Giữ hành vi này; thay `mustChangePassword` bằng `session.requiresPasswordChange`; thay token mới trả về từ change-password **trước khi** bootstrap |
| FE admin | `AdminPortal` search `u.email.toLowerCase()`, so thành viên theo email; `CreateWorkspaceForm` hiện email; `workspaceService` ép email null thành chuỗi rỗng | Null-safe, so theo userId, hiện username/Gmail |
| FE `HeaderNav` | Hiện email dưới tên | Hiện username hoặc Gmail, thêm mục Cài đặt hồ sơ |
| **[mới]** Test BE | 14 file dùng email: `EmailCaseLoginTest`, `GoogleSignInTest`, `AccountProvisioningAccessTest`, `JwtServiceTest`, `ProjectAndNoteGuardsTest`, `WorkspaceServiceTest`, `SupabaseRegistrationTest`, `SupabaseScriptFlowTest`, `PostgresConcurrencyTest`, `QueryShapeJpaTest`, `LocalStudioE2eTest`, `LocalAiDecisionE2eTest`, `LocalAiUsageE2eTest`, `E2eServer` | Cập nhật theo contract mới (§11.1) |
| **[mới]** Test E2E FE | `e2e/support/api.ts` provision/login bằng email (16 chỗ), WF-01 kiểm tra luồng email | Cập nhật và thêm spec auth (§11.2) |

Đối chiếu lại code ngay trước khi bắt đầu, vì teammate có thể đã thêm migration hoặc sửa auth.

## 4. Mô hình dữ liệu

### 4.1 `users` vẫn là gốc sở hữu

- Giữ nguyên `id`, role, status, `full_name`/`display_name`, `password_hash` và mọi FK.
- Thêm `username` nullable, unique theo dạng chuẩn; bắt buộc khi có `password_hash` (CHECK constraint).
- Thêm `auth_version` integer NOT NULL DEFAULT 0, dùng để vô hiệu hóa token khi đổi/reset mật khẩu hoặc liên kết Google.
- `email`: giữ dữ liệu, cho phép null (sau khi kiểm kê constraint); chỉ còn là email liên hệ cũ, không dùng để đăng nhập, không ghi Gmail liên kết vào đây.
- `google_id`: giữ trong giai đoạn chuyển đổi, ngừng ghi sau cutover, xóa ở migration sau khi nghiệm thu.

### 4.2 `google_login_whitelist`

`id`, `email` (Gmail chuẩn hóa, NOT NULL UNIQUE), `status` (ACTIVE/REVOKED), `created_by`, `created_at`, `updated_by`, `updated_at`, `revoked_at`, `version` (optimistic lock).

Không xóa vật lý. Không sửa email tại chỗ. Thêm lại một email đã revoke thì khôi phục chính bản ghi đó.

### 4.3 `google_identities`

`id`, `user_id` (FK UNIQUE), `google_subject` (NOT NULL UNIQUE), `email` (UNIQUE), `whitelist_id` (FK UNIQUE), `linked_at`, `last_login_at`, `created_at`, `updated_at`.

Không lưu token. Cùng `sub` mà email đổi thì từ chối `GOOGLE_IDENTITY_CHANGED`, không tự gắn lại.

### 4.4 `google_auth_challenges` **[sửa: thêm lý do và phương án rút gọn]**

`id`, `nonce_hash`, `secret_hash`, `purpose` (LOGIN/LINK), `user_id` (bắt buộc với LINK), `auth_version` (với LINK), `expires_at` (TTL 5 phút), `consumed_at`.

- **Lý do có secret:** `nonce` nằm trong ID token, chống dùng lại token; `challengeSecret` giữ trong bộ nhớ FE, buộc token phải được đổi tại đúng trình duyệt đã xin challenge.
- **Phương án MVP:** nếu cần rút gọn, chỉ dùng nonce một lần với TTL 5 phút (bỏ secret). Ghi lại quyết định ở §14.
- Consume nguyên tử (`UPDATE … SET consumed_at = now() WHERE id = ? AND consumed_at IS NULL AND expires_at > now()`). Dọn bản ghi hết hạn bằng `@Scheduled` trong BE.

## 5. Phiên và mật khẩu

Token: `sub = userId` **(giữ như hiện tại)**, `role`, `authMethod` (PASSWORD|GOOGLE), `authVersion`, `iat`/`exp`; phiên Google thêm `googleIdentityId`. Bỏ claim `email`.

`requiresPasswordChange = authMethod == PASSWORD && passwordHash != null && mustChangePassword`. Đây là điều kiện quyền ở BE, không chỉ là cờ hiển thị ở FE.

| User / phiên | Bắt đổi mật khẩu |
|---|---|
| Local mới, PASSWORD | Có; chỉ cho `/auth/me`, `/auth/change-password` và đăng xuất |
| Local đã đổi, PASSWORD | Không |
| Google-only, GOOGLE | Không; không hiện form mật khẩu |
| Local đã liên kết, GOOGLE | Không, kể cả khi mật khẩu local vừa bị Admin reset |
| Local đã liên kết, PASSWORD sau khi bị reset | Có; đăng nhập Google không xóa cờ này |

Mỗi request **[sửa]**:
- Một truy vấn lấy user + identity + whitelist (join), cache trong phạm vi request.
- Kiểm tra ACTIVE và `authVersion` khớp.
- Với phiên GOOGLE: identity vẫn thuộc user và whitelist ACTIVE.
- Không cache quyền whitelist lâu để revoke có hiệu lực ngay.

Đổi/reset mật khẩu và liên kết Google làm tăng `authVersion`, rồi trả token thay thế cho phiên hiện tại. Token cũ không có `authMethod`/`authVersion` trả 401 `SESSION_EXPIRED`. Whitelist bị revoke khi đang có phiên Google trả 401 `SESSION_REVOKED`. Nhập sai mật khẩu lúc reauth ở profile trả **400** để FE không đăng xuất nhầm.

Các endpoint công khai (login, challenge, enabled) gửi không kèm JWT. apiClient phân biệt 401 của endpoint đăng nhập với 401 của tài nguyên cần đăng nhập.

## 6. Luồng nghiệp vụ

### 6.1 Admin cấp tài khoản local và người dùng đăng nhập
Admin nhập họ tên, username, mật khẩu tạm, role. BE chuẩn hóa username, kiểm tra unique, mã hóa BCrypt, đặt `mustChangePassword = true`. Không yêu cầu Gmail, không tự thêm whitelist, không trả JWT của user mới.

Đăng nhập: tìm theo username chuẩn. **[mới]** Username không tồn tại thì vẫn so BCrypt với hash giả. Sai thông tin trả `INVALID_CREDENTIALS`. Mật khẩu không trim, không lowercase. Có `requiresPasswordChange` thì FE mở modal đổi mật khẩu; API guard chặn mọi đường vòng.

### 6.2 Admin quản lý whitelist
Form chỉ có Gmail và nút Thêm; bản ghi mới là ACTIVE, chưa có user. Danh sách có tìm kiếm, phân trang và trạng thái (Chưa sử dụng / Đã liên kết / Đã thu hồi). Admin có thể revoke và restore. Whitelist không phải lời mời vào workspace.

### 6.3 Đăng nhập Google
1. FE kiểm tra cấu hình, xin challenge LOGIN, cấu hình GIS với nonce.
2. FE gửi `credential`, `challengeId`, `challengeSecret`.
3. BE xác minh chữ ký, `aud`, `iss`, `exp`, `email_verified`, `sub`, `nonce`, domain Gmail và challenge.
4. Whitelist không ACTIVE thì trả 403 `ACCOUNT_NOT_IN_SYSTEM`.
5. Trong transaction: khóa whitelist, sau đó identity, sau đó user (thứ tự cố định), rồi kiểm tra lại.
6. Đã có identity thì đăng nhập đúng user đó. Chưa có thì tạo user Google-only (WRITER) và gắn identity.
7. Consume challenge, phát JWT GOOGLE, ghi log sự kiện (không ghi credential).
8. FE lưu token và profile. Chưa có workspace thì hiện trạng thái trống, không tự tạo workspace.

### 6.4 Đổi tên trong profile
PATCH chỉ nhận `fullName`: trim, 2–100 ký tự, chấp nhận tiếng Việt. Không ghi đè tên bằng claim Google ở các lần đăng nhập sau. Chặn mass assignment.

### 6.5 Liên kết Google cho tài khoản local
- Chỉ mở khi có mật khẩu local, đã đổi mật khẩu tạm, chưa có identity.
- Reauth bằng mật khẩu hiện tại, rồi cấp challenge LINK gắn với userId và authVersion.
- Gmail phải ACTIVE trong whitelist. Gắn vào **userId của phiên đang đăng nhập**, không tạo user mới.
- Gửi lại callback cùng user và cùng sub: trả kết quả đã liên kết.
- Gmail hoặc sub đã thuộc user khác: trả 409 `GOOGLE_IDENTITY_IN_USE`, không ghi gì.

**Xung đột đã biết:** Gmail đã từng đăng nhập (tạo user Google-only A) thì không chuyển sang user local B được. Nhóm nên **hướng dẫn người có tài khoản local liên kết Google trong profile trước khi đăng nhập Google lần đầu**; ghi câu này vào user manual.

### 6.6 Ngoài phạm vi v1
Tự đăng ký, xin whitelist, đổi username, đổi Gmail, tự đặt mật khẩu cho tài khoản Google-only, unlink/relink, hợp nhất hai user.

## 7. Contract API (prefix `/api/v1`, error envelope hiện có)

| Method và route | Quyền | Request → Response |
|---|---|---|
| POST `/auth/login` | Công khai | `{username,password}` → AuthResponse |
| GET `/auth/google/enabled` | Công khai | `{enabled}` |
| POST `/auth/google/challenge` | Công khai | → `{challengeId,nonce,challengeSecret,expiresInSeconds}` |
| POST `/auth/google` | Công khai | `{credential,challengeId,challengeSecret}` → AuthResponse |
| GET `/auth/me` | Đã đăng nhập (cả khi phải đổi mật khẩu) | `{user,session}` |
| POST `/auth/change-password` | Có mật khẩu local | `{currentPassword,newPassword}` → AuthResponse (token mới) |
| GET `/profile` | Đã đăng nhập | `{user,session,googleLoginAllowed,canLinkGoogle}` |
| PATCH `/profile` | Chính user | `{fullName}` → ProfileResponse |
| POST `/profile/google/link-challenge` | Local, đã đổi mật khẩu | `{currentPassword}` → challenge LINK |
| POST `/profile/google/link` | Cùng phiên | `{credential,challengeId,challengeSecret}` → AuthResponse |
| POST `/users` | ADMIN | `{fullName,username,password,role}` → AdminUserResponse (không có token) |
| GET/PATCH `/users/{id}`, GET `/users` | RBAC hiện có | Thêm `username`, `hasLocalPassword`, `googleLinked` |
| GET `/admin/google-whitelist` | ADMIN | `?page&size&query&status` → danh sách + header phân trang hiện có |
| POST `/admin/google-whitelist` | ADMIN | `{email}` → bản ghi; trùng ACTIVE trả 409 |
| PATCH `/admin/google-whitelist/{id}` | ADMIN | `{status,version}` → bản ghi; version cũ trả 409 |

`user` gồm `id`, `fullName`, `username?`, `role`, `status`, `hasLocalPassword`, `googleLinked`, `googleEmail?`. `session` gồm `authMethod`, `requiresPasswordChange`. Không trả Google `sub`, password hash hay raw claims.

Mã lỗi:
- 400: `VALIDATION_ERROR`, `REAUTHENTICATION_FAILED`, `LOCAL_PASSWORD_UNAVAILABLE`
- 401: `INVALID_CREDENTIALS`, `GOOGLE_TOKEN_INVALID`, `SESSION_EXPIRED`, `SESSION_REVOKED`
- 403: `ACCOUNT_NOT_IN_SYSTEM`, `ACCOUNT_DISABLED`, `PASSWORD_CHANGE_REQUIRED`
- 409: `USERNAME_TAKEN`, `GOOGLE_IDENTITY_IN_USE`, `GOOGLE_ALREADY_LINKED`, `GOOGLE_IDENTITY_CHANGED`, `CONCURRENT_UPDATE`
- 429: `TOO_MANY_ATTEMPTS`
- 503: `GOOGLE_LOGIN_DISABLED`, `GOOGLE_VERIFICATION_UNAVAILABLE`

Khi mạng tới Google lỗi thì trả 503, không được trả nhầm thành 403 ngoài whitelist.

**[mới]** Cập nhật `docs/api/` (OpenAPI) và `STEMotion_FE/BACKEND_API_SPEC.md` cùng batch với contract.

## 8. FE

### 8.1 Đăng nhập và phiên
- Ô "Tài khoản" `type=text` `autocomplete=username`; mật khẩu `autocomplete=current-password`.
- Nút GIS "Đăng nhập bằng Google" đặt ngoài form để thuộc tính `required` không chặn popup.
- Thông báo ngoài whitelist hiện đúng câu yêu cầu, không có nút xin quyền.
- Một handler hoàn tất đăng nhập kiểm tra đủ token, user, session; không lấy user cũ trong cache.
- SDK loader dùng một Promise duy nhất; controller chọn purpose LOGIN hoặc LINK và dọn callback; tắt auto select; chặn double submit và callback về muộn sau khi đã logout.
- Cache auth có số phiên bản schema; cache cũ thì bỏ hoặc gọi lại `/auth/me`.
- **[mới]** Thứ tự sau khi đổi mật khẩu lần đầu: lưu token mới trả về, rồi mới `bootstrapFromApi()`. Bootstrap dùng token cũ (authVersion cũ) sẽ bị 401.

### 8.2 Cài đặt hồ sơ
Mở từ header: tên (sửa được), username (chỉ xem), Gmail đã liên kết (chỉ xem), trạng thái quyền Google. Tài khoản local chưa liên kết: reauth, rồi popup, rồi xác nhận. Lỗi xung đột giữ nguyên profile và phiên.

### 8.3 Admin và workspace
- Form cấp tài khoản đổi "Email" thành "Tài khoản" và hiện quy tắc username.
- Tab "Whitelist Google" riêng.
- DTO thêm `username` nullable. Hiển thị theo username, nếu không có thì Gmail, nếu không có thì tên. Tìm kiếm null-safe, so thành viên theo userId.

## 9. Migration và dữ liệu cũ

### 9.1 Chuẩn bị (chỉ đọc)
- Script kiểm kê: số user local và Google; email trùng khi bỏ phân biệt hoa thường; `google_id` có giá trị hoặc bị trùng; kiểu `users.id` và các FK; constraint và index của email. Không xuất hash hay token.
- Mapping `userId → username` do nhóm duyệt. Không tự lấy phần trước `@` của Gmail. Thiếu mapping thì đề xuất `legacy_<hậu tố id>`.
- `users.google_id` cũ không tự được vào whitelist; Admin phải xác nhận từng dòng.

### 9.2 Mở rộng rồi chuyển contract **[sửa: thêm bước 0, chi tiết Flyway]**
0. **Admin dự phòng:** trong mapping, mọi tài khoản ADMIN có username. Sau backfill, đăng nhập thử bằng username trên DB cô lập trước khi làm trên DB thật.
1. Kiểm tra migration mới nhất lúc bắt đầu (hiện là V22). Dùng số kế tiếp thực tế, không đặt cứng V23.
2. Migration mở rộng: thêm `username`, `auth_version`; `email` cho phép null; tạo 3 bảng mới cùng constraint. Không xóa cột cũ.
3. Backfill theo mapping trong một transaction; dừng hẳn nếu có trùng hoặc thiếu; không dùng `ON CONFLICT` để bỏ qua lặng lẽ.
4. Thứ tự áp dụng: DB cô lập, rồi staging, rồi DB thật (cần duyệt). Lần lượt: schema, backfill, BE mới, FE mới. Không chạy lẫn hai contract; mọi người đăng nhập lại.
5. Feature flag `AUTH_V2_ENABLED` và `GOOGLE_LOGIN_ENABLED`. Flyway chạy khi BE khởi động nên **không khởi động BE mới trên DB chưa chuẩn bị**. Profile cloud đang dùng `ddl-auto=validate`, nên entity mới sẽ fail khi boot nếu schema chưa có.
6. Dọn `email`/`google_id` cũ ở batch sau, sau khi nghiệm thu.

### 9.3 Rollback
Backup DB và mapping trước cutover. Schema mở rộng giữ được dữ liệu, nhưng **BE cũ không chạy được** trên dữ liệu mới (user có email null). Khi có sự cố: tắt auth v2, ngừng ghi, đối soát, rồi chọn sửa tiếp hoặc khôi phục snapshot. Không rollback bằng cách xóa user hay bảng identity.

## 10. Lộ trình và batch **[sửa: chia mốc, thêm người phụ trách đề xuất]**

| Mốc | Batch | Nội dung | Phụ trách đề xuất |
|---|---|---|---|
| **M1 – MVP** (sau Review 2) | A | Chốt contract, mapping mẫu, câu hỏi §14 | Hoàng + Trì |
| | B | BE: username provisioning và login, JWT `authMethod`/`authVersion`, guard theo phiên, migration draft, rate limit | Hoàng |
| | D | FE: login username, form cấp tài khoản, null-safe email | Trì |
| | T1 | Cập nhật test BE (14 file) và E2E WF-01 theo username | Hoàng + Trung |
| **M2 – Google và profile** | C | BE: whitelist, verifier (thư viện), challenge, Google login, profile, link, revoke | Hoàng |
| | E | FE: GIS controller, tab whitelist, Cài đặt hồ sơ, header | Trì |
| | T2 | Verifier giả trong E2eServer, spec E2E auth mới | Trung |
| **M3 – Tài liệu và cutover** | F | SRS/SDD/Report 1/BM cập nhật (§12), runbook | Hoàng |
| | G | DB cô lập, staging với OAuth thật, cutover có duyệt | Cả nhóm |

Mỗi mốc là một nhóm thay đổi cùng contract; không deploy riêng từng batch khi contract hoặc schema còn lệch. Nếu M2 chưa xong trước Defense 1.1, giữ đăng nhập Google hiện có (theo email đã cấp) sau flag và ghi vào Known Gaps.

## 11. Test (chạy sau khi được cho phép)

### 11.1 **[mới]** Test hiện có phải sửa
- BE: 14 file ở §3 (đổi email thành username; `GoogleSignInTest` viết lại theo whitelist).
- `E2eServer`: seed Admin bằng username (`E2E_ADMIN_USERNAME`); `E2E_ADMIN_EMAIL` bỏ.
- FE: `e2e/support/api.ts` (`provision`, `login`, `setupWorkspace`), `wf01-team-setup.spec.ts`; test Vitest của `LoginScreen`, `AdminPortal`, `CreateWorkspaceForm`, `ManageMembersModal`.

### 11.2 **[mới]** Test Google tự động không cần Google thật
- `E2eFakes.FakeGoogleVerifier` (`@Primary` trong `E2eServer`): nhận credential dạng `e2e:<sub>:<gmail>:<nonce>` và trả claims như Google; các giá trị `e2e-invalid*` trả lỗi tương ứng (chữ ký, hết hạn, sai aud).
- FE ở chế độ E2E: GIS được thay bằng một nút test gọi thẳng callback với credential giả (chỉ bật khi `VITE_E2E=true`, không có trong bản build production).
- Spec mới `wf01b-auth.spec.ts`:
  - đăng nhập bằng username;
  - Google trong whitelist (lần đầu tạo user, lần sau vào đúng user);
  - Google ngoài whitelist;
  - liên kết Google từ profile, rồi đăng nhập Google vào cùng user;
  - revoke chặn phiên Google nhưng không chặn mật khẩu;
  - phiên Google không bị bắt đổi mật khẩu.
- Google thật chỉ kiểm tra tay trên staging với Gmail test (G).

### 11.3 Ma trận nghiệm thu
Giữ ma trận của bản đầu: username, provision, whitelist, Google token, Google-only, mật khẩu theo phiên, profile, liên kết, JWT, đồng thời, FE, regression, migration. Bổ sung thêm:
- **[mới]** Thời gian phản hồi giống nhau khi username tồn tại và không tồn tại (chênh lệch không đáng kể).
- **[mới]** Rate limit: lần thứ 11 trong 5 phút trả 429; đăng nhập đúng sau cửa sổ thì qua.
- **[mới]** Admin dự phòng đăng nhập được sau backfill trên DB cô lập.
- **[mới]** CI E2E xanh với bộ spec đã cập nhật; link báo cáo GitHub Pages vẫn hoạt động.

## 12. Đồng bộ tài liệu **[sửa: theo Report 3 chính thức và form Review 2]**

| Tài liệu | Cập nhật |
|---|---|
| Report 3 (`docs/deliverables/reports/…Software_Requirement_Specification.docx`, bản của Lê) | Phạm vi; UC-01 Login (username hoặc Google whitelist); UC-24 tách cấp local và whitelist; FR-01…FR-05, NFR-08/09 viết lại; **FR mới từ FR-56** (whitelist, profile, liên kết, revoke); business rules; messages `ACCOUNT_NOT_IN_SYSTEM` và nhóm xung đột; screen flow (Cài đặt hồ sơ, tab Whitelist); Record of Changes. |
| Use case diagram (`docs/diagrams/source/STEMotion_UseCase.drawio`, PNG ở `docs/diagrams/figures/`) | **Thêm use case** "Manage Google Whitelist" (Admin), "Edit Profile / Link Google" (Authorized User), rồi xuất lại PNG cho Report 1 và Report 3. Không cập nhật thì SRS lệch diagram. |
| Report 4 SDD | ERD (`users.username`, `auth_version`; 3 bảng mới; sửa `docs/diagrams/05-erd.mmd`), state machine mới cho `google_login_whitelist` (ACTIVE ⇄ REVOKED), sequence cho login username, đổi mật khẩu bắt buộc, đăng nhập Google, liên kết, revoke, đổi tên; package/class auth; khai báo công nghệ (thư viện verifier). |
| Report 1 | FE-01: tài khoản là username; Google cho Gmail trong whitelist; FE-15: quản lý whitelist; phạm vi profile. |
| BM01 | F-01: tên và tiêu chí nghiệm thu (username, whitelist, ngoài whitelist trả 403); DS-01a/DS-01b dùng username; Known Gap G-06 thay bằng phạm vi M2; số test case cộng thêm theo FR mới (mỗi feature 3 test, mỗi transition mới 1 test). |
| BM04 | RF tương ứng M1/M2, người phụ trách, hạn. |
| BM03 / phiếu đăng ký | **Không đổi.** Phiếu gốc ghi "JWT sessions and Google OAuth 2.0"; username và whitelist là cách hiện thực, không đổi phạm vi. |
| User manual | Cách đăng nhập, liên kết Google trước lần đăng nhập Google đầu tiên, thông báo ngoài whitelist. |

Không ghi "Implemented" trước khi FE và BE chạy trên staging có bằng chứng.

## 13. Tham chiếu

- SRS/SDD chính thức: `docs/deliverables/reports/` (repo BE). Sơ đồ: `docs/diagrams/`.
- Google: [Verify the Google ID token on your server side](https://developers.google.com/identity/gsi/web/guides/verify-google-id-token) (dùng `sub` làm định danh, xác minh chữ ký và claims bằng thư viện); [GIS JavaScript API reference](https://developers.google.com/identity/gsi/web/reference/js-reference) (nonce, initialize dùng chung toàn trang).
- Code liên quan: `AuthService`, `UserService`, `JwtService`, `JwtAuthenticationFilter`, `ResourceAccessInterceptor`, `GoogleTokenVerifier`; FE `LoginScreen`, `GoogleSignInButton`, `App.tsx`, `AdminPortal`, `HeaderNav`, `workspaceService`.

## 14. **[mới]** Câu hỏi cần nhóm chốt trước khi code

1. Thời điểm: M1 bắt đầu sau Review 2? M2 có kịp trước Defense 1.1 không, hay giữ Google kiểu hiện tại sau flag?
2. Ngưỡng rate limit (đề xuất 10 lần / 5 phút theo IP + username)?
3. Challenge LOGIN: giữ cả `nonce` và `challengeSecret`, hay MVP chỉ dùng nonce?
4. Role hệ thống mặc định của user Google-only: WRITER như plan, hay để whitelist mang role do Admin chọn (một trường tùy chọn, mặc định WRITER)?
5. Danh sách username cho các tài khoản hiện có: ai lập, ai duyệt, và báo cho người dùng qua kênh nào?
6. Ai cập nhật use case diagram (drawio) và các report sau khi M1/M2 xong?
