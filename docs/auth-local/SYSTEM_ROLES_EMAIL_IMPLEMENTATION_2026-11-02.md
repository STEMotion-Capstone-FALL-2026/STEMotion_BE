# Báo cáo triển khai role hệ thống và email whitelist trên local

Ngày 02/11/2026, Asia/Saigon. Source local đã triển khai; DB chung chưa migrate, chưa push/deploy.
Master plan: [phân quyền và email whitelist](SYSTEM_ROLES_WHITELIST_EMAIL_MASTER_PLAN_2026-11-02.md).

**Cập nhật test Brevo thật:** sau khi người dùng cấu hình API key/sender và chỉ định Gmail nhận test,
đã chạy một thông báo thật qua POST whitelist → outbox → worker → Brevo HTTP trên DB embedded riêng
(port 18082, max attempts 1). Outbox chuyển PENDING → ACCEPTED, event
`87bdfc34-5b9e-49d3-930c-77dd94d2f791`. Không gửi lại, không bulk-send; instance test đã dừng.
Provider acceptance đã xác nhận, inbox delivery vẫn chờ người nhận kiểm tra. API key không có quyền
đọc resource account nhưng gửi transactional email thành công; không cần mở rộng quyền chỉ để test.
APP_PUBLIC_URL còn placeholder đã được sửa thành URL Vercel do người dùng cung cấp. Backend manual
18080 vẫn dùng local preview, không tự đổi các thông báo local cũ thành gửi thật. Các đoạn chưa gửi
mail thật bên dưới phản ánh trạng thái trước lần smoke-test này.

## Kết quả triển khai

- BE tách SystemRole ADMIN/USER khỏi WorkspaceRole WRITER/REVIEWER/PRODUCER. USER mới không
  có quyền sản xuất nếu chưa có membership; một người giữ được role khác nhau giữa các workspace.
- Local account nhận systemRole, mặc định USER. Google-only account mới USER; account đã link
  Google giữ nguyên ID/role/ownership. Whitelist không có trường cấp ADMIN.
- API user trả systemRole và version; PATCH/DELETE yêu cầu If-Match là version hiện tại. Đổi quyền
  hoặc trạng thái tăng authVersion, phiên cũ bị thu hồi. Ghi audit provisioning/access-change/delete.
- Guard dùng advisory transaction lock chung, đếm Admin ACTIVE có password hoặc Google whitelist
  còn hiệu lực. Không hạ/khóa/xóa Admin truy cập được cuối cùng; không thu hồi Gmail là phương thức
  duy nhất của Google-only Admin cuối cùng. Admin ACTIVE nhưng đã mất Google access không được
  tính như một phương án đăng nhập dự phòng.
- FE tách SystemRole, WorkspaceRole và AppView; bỏ global Writer fallback, đổi profile cache sang v3.
  Form cấp account chỉ USER/ADMIN; thêm vào workspace cần chọn production role rõ ràng.
- Whitelist service ghi quyền + notification outbox trong cùng transaction. Add/restore enqueue;
  duplicate add/PATCH không đổi trạng thái không enqueue lại. Revoke hủy việc đang chờ của grant cũ.
- Worker claim qua SKIP LOCKED/lease/claim token, gọi provider ngoài transaction, dùng DB clock cho
  delay/cooldown. 429/5xx retry có giới hạn; timeout/lease không rõ kết quả thành UNKNOWN để tránh
  tự gửi trùng. Không cam kết exactly-once email hoặc thu hồi email đã được provider nhận.
- Local capture tạo HTML preview, không gửi thư. Adapter Brevo HTTP có key/sender/HTTPS validation,
  phân loại lỗi và provider message ID. Job giữ provider, không tự chuyển pending local sang live.
- FE có badge quyền/bind/mail riêng, trạng thái preview/pending/failure và thao tác gửi lại có cooldown.
  Notification API chỉ ADMIN; recipient/body không lấy từ request gửi lại. Nội dung hướng dẫn account
  local link Google trước, không chứa password/JWT/OTP hoặc request-access.
- Recipient-bearing terminal jobs được dọn sau 30 ngày khi scheduler chạy. Audit không giữ body mail.

## Dữ liệu và môi trường đang chạy

Manual FE: http://localhost:3000. Manual BE: http://127.0.0.1:18080/api/v1, loopback only,
Google verifier thật, AI/render test doubles, email provider local. Môi trường này không thay cho deployment.

Đã snapshot DB tạm trước nâng cấp và restore vào DB embedded có schema mới. Smoke-check sau restore:
2 users, cả hai có systemRole hợp lệ, 1 user đã bind Google và 2 whitelist rows được giữ lại.
Không xuất email, token, hash hoặc snapshot SQL vào báo cáo. Phiên cũ cần đăng nhập lại.
Các whitelist cũ không tự được gửi mail; notification hiện NONE, có thể gửi chủ động khi cần.

Google thật đã được người dùng xác nhận thành công trước đợt tách role. Sau nâng cấp đã kiểm tra
backend/config/identity preservation; thao tác popup Google thật sau nâng cấp vẫn do người dùng thực hiện.
E2E Google trong kiểm thử tự động là verifier fixture, không giả là lần consent thật.

Backend fake trên 18081 chỉ dùng cho suite Chromium và đã dừng sau test. Backend manual vẫn chạy.
Không thay .env chứa key thực của người dùng. Các biến mail được bổ sung ở .env.example và runbook;
E2eServer override local-only trong process, không dùng key gửi thật trong .env.

## Bằng chứng kiểm thử

| Đợt | Kết quả | Phạm vi |
|---|---|---|
| BE full suite, loại Supabase tests | 393 cases, 381 pass, 12 skip, 0 failure/error | Auth, scopes, nghiệp vụ, query count, migration, outbox và provider mocked; không DB chung |
| Sau bổ sung guard Google-only Admin cuối cùng | 13 pass | 7 PostgreSQL role/outbox cases + auth policy và provisioning; bao gồm race và usable-login guard |
| Browser Chromium | 27 pass | Auth/link/revoke, role scope giữa workspace, promote Google user, invalidated session, mail preview và pipeline script/review/render/publish với fake worker |
| FE suite ban đầu | 183/184 pass | Một fixture cũ trả myRole ADMIN, bị contract workspace mới từ chối đúng |
| Fixture được sửa và test workspace chạy lại | 11 pass | myRole null cho admin không có production seat; contract không còn gán ADMIN cho membership |
| FE full suite cuối | 184 pass / 36 files | Đã sửa fixture và chạy lại toàn bộ; không còn failure |
| FE build cuối | TypeScript + Vite pass | Còn cảnh báo bundle lớn, không chặn build |
| Provider HTTP/local tests | 4 pass | Request/auth header/recipient/sender, provider acceptance, 429/401, local capture không network, config validation |

Full BE suite chạy trước chốt Google-only Admin; thay đổi cuối đã được compile và chạy nhóm 13 ca
liên quan, không cộng hai đợt để làm như có 406 test độc lập. Source BE được build vào .build-codex
để IDE không ghi error stubs vào CLI output.

Các lỗi phát hiện và sửa khi broaden regression: MVC slices thiếu AuthRateLimiter/UserRepository
sau auth-v2; test transaction guard thiếu annotation trên hai legacy entry chỉ trả lỗi; candidate/
workspace fixtures vẫn mô tả global production role. Không vô hiệu hóa security hoặc nới giới hạn
query để làm test pass. Negative role values vẫn được kiểm tra ở boundary.

## Ranh giới rollout và tài liệu

- [SQL drafts và runbook](../../deploy/roles-mail-drafts/README.md) đã có preflight,
  expand/backfill, transaction/cutover và rollback constraints. Native enum và varchar đã rehearsal
  trên PostgreSQL cô lập; chưa chứng minh mọi trigger/RLS/function/grant trên DB thật tương thích.
- Legacy users.role được giữ phục vụ review/rollback, source mới chỉ dùng users.system_role.
  Không tự sửa V1/V17, không đưa V9001 fixture vào production migration history.
- [Contract hiện tại](../api/AUTH_V2_CONTRACT.md), OpenAPI runtime local và FE API
  notice đã cập nhật. [Changeset SRS/SDD](../AUTH_V2_SRS_SDD_CHANGESET_2026-11-02.md)
  chỉ rõ actor mapping và đoạn cần thay; bản Word/BM gốc chưa được ghi đè hoặc đánh dấu hoàn thành.
- [Hướng dẫn mail](../WHITELIST_EMAIL_LOCAL_SETUP.md): local không cần domain/key.
  Gửi thật còn cần tài khoản provider, sender được xác minh và API key BE. Chưa gửi email thật,
  chưa xác nhận inbox delivery và chưa cấu hình tài khoản dịch vụ/DNS thay người dùng.
- Admin bypass trong nghiệp vụ nội dung được giữ theo runtime hiện tại. Yêu cầu Planned siết Admin
  phải có reviewer membership để quyết định nội dung và project-role features vẫn là việc riêng.
- P0–P5 đã có source/SQL/docs local; P6 có technical contracts/tests/changeset, việc hợp nhất Word/BM
  còn lại; P7 production cutover chưa thực hiện. Không coi đây là báo cáo đã deploy toàn bộ master plan.


This is a repository copy of the workspace audit/report. Local paths and runtime counts are
verification evidence, not production deployment configuration.

Pre-push follow-up: local Brevo links now permit HTTP only for loopback hosts; public hosts require
HTTPS. The local .env URL was set to http://localhost:3000. Keys remain untracked. CI uses the
isolated schema; FE feature-branch E2E uses the paired BE feature branch until coordinated merge.
