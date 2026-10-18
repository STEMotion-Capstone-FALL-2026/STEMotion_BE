# API Audit Checklist — STEMotion

Ngày audit: 17/10/2026 (Asia/Saigon). Phạm vi: đọc source, đặc tả, test có sẵn; không triển khai tính năng, không chạy migration, không commit/push.

## 0. Cập nhật sau audit (17/10/2026, BE)

Audit đọc BE `c7fbc49` và worker `16c984e`. Mục này ghi những gì đã đổi sau đó, để đọc phần còn lại cho đúng. Các mục bên dưới **không sửa lại**; khi mâu thuẫn thì mục 0 này là trạng thái hiện tại.

### 0.1. BE đã sửa

| # | Vấn đề trong audit | Đã sửa | Ảnh hưởng FE |
|---|---|---|---|
| 1 | `GET /workspaces`: mục 2.2 ghi "ADMIN thấy tất cả", thực tế admin chỉ thấy tổ mình là thành viên | ADMIN nhận **tất cả** workspace; tổ admin không ngồi thì `myRole`/`isLeader` = `null`. `/workspaces/mine` giữ nguyên (chỉ membership) | Xem FE-2 |
| 2 | Mục 2.4/3.4: `send-back` không bắt buộc lý do, FE gửi `{}` | `POST /scripts/{projectId}/send-back` **bắt buộc** `{"note": "..."}` không rỗng, thiếu → **400 `REVIEW_REASON_REQUIRED`**. Lý do lưu vào status history (target CLIP) | **Bắt buộc sửa FE-1**, nếu không nút trả clip sẽ lỗi 400 |
| 3 | Mục 2.4: `request-changes` không bắt buộc ghi chú | `POST /scripts/{id}/request-changes` **bắt buộc** `{"note": "..."}`, cùng mã lỗi 400. Note lưu vào `reviewNote` của version bị trả + version mới + status history | FE-1 |
| 4 | Mục 2.6: dispatch trước commit, callback đầu có thể 404 | Job chỉ gửi cho worker **sau khi transaction commit**. Worker không gọi được → job `FAILED` (`errorMessage` = "Render worker unreachable…"), project về `NOT_RENDERED`; request start vẫn trả 202 | FE-3 |
| 5 | Mục 2.6: hai lần lưu composition đầu cùng lúc → 500 | Lần thua nhận **409** ("just saved elsewhere; reload") như stale revision | FE-4 |
| 6 | Mục 2.3: archive bị interceptor đòi WRITER | `DELETE /scripts/{id}` chỉ cần là thành viên ở interceptor; `canArchiveProject` cho owner, leader (mọi role) và ADMIN | FE-5 |
| 7 | Mục 2.5/6: mọi lỗi AI thành `AI_SERVICE_UNAVAILABLE` | Mã riêng: `AI_INVALID_OUTPUT` (502), `AI_PROVIDER_FAILED` (502), `AI_SERVICE_KEY_MISMATCH` (502), `AI_TIMEOUT` (504), `AI_BUSY` (429), `AI_INVALID_INPUT` (400), `AI_NOT_CONFIGURED` (503); `AI_SERVICE_UNAVAILABLE` (503) chỉ khi không kết nối được ai-service | FE-6 |
| 8 | ai-service trả 502 khi AI thiếu props template (vd DIAGRAM_EXPLAINER thiếu `labels`) | ai-service kiểm tra props bắt buộc như `ScenePropsValidator` và **tự gọi lại 1 lần** kèm lý do | Không |
| 9 | Đăng nhập phân biệt hoa/thường email | `A@gmail.com` đăng nhập được tài khoản `a@gmail.com` nếu chỉ có đúng 1 tài khoản khớp; tạo tài khoản trùng email khác hoa/thường bị 409 | Không |

### 0.2. Đính chính audit (đã lỗi thời)

- **UC-28 / mục 2.4, 4.1 "không đo caption/audio sync thật":** đã có check `CAPTION_SYNC` trong `GET /scripts/{projectId}/qa-report` (commit `ea6612b`), đọc `captionSync` từ callback COMPLETED (Whisper forced alignment): FAIL nếu lời đọc vượt độ dài cảnh, WARN nếu thiếu/coverage < 90%/có từ độ tin cậy thấp.
- **UC-20 / mục 2.5 "worker không có hook gọi align/caption":** worker `3d3e163` gọi `/transcribe/align` sau TTS, render phụ đề theo từng từ và gửi `captionSync` trong callback. Phần còn thiếu đúng như audit: TTS-preview/sync-durations qua BE.
- **Mục 2.6 "callback không gửi caption artifact":** callback có `captionSync` (số liệu đồng bộ), chưa có file caption riêng.

### 0.3. Việc cho team FE

- [ ] **FE-1 (bắt buộc, trước khi deploy BE này lên demo):** `reviewService.ts` đang gửi `apiClient.post('/scripts/${projectId}/send-back', {})`. Đổi thành gửi `{ note }`; UI trả clip và trả kịch bản phải bắt nhập lý do. Hiển thị lỗi `REVIEW_REASON_REQUIRED` nếu BE trả 400.
- [ ] **FE-2:** `GET /workspaces` giờ trả mọi tổ cho ADMIN. `workspaceService.getMyWorkspaces()` fallback sang `/workspaces` khi `/mine` rỗng, nên admin không ngồi tổ nào sẽ thấy tất cả tổ ở các màn "của tôi" — kiểm tra có đúng ý không; màn quản trị nên dùng `getAllWorkspaces()`. `myRole`/`isLeader` có thể `null`.
- [ ] **FE-3:** `POST /render/projects/{id}/start` có thể trả `estimatedSeconds: null`. Lỗi worker không còn báo ngay ở request start: poll `GET /render/status/{jobId}` và hiển thị `errorMessage` khi `FAILED`.
- [ ] **FE-4:** `PUT /scripts/{projectId}/composition` trả 409 thì tải lại composition và báo người dùng (đã đúng nếu FE xử lý 409 chung cho stale revision).
- [ ] **FE-5:** Leader là REVIEWER/PRODUCER giờ archive được: hiện nút archive theo owner/leader/admin, không theo role WRITER.
- [ ] **FE-6:** `App.tsx` (chỗ bắt lỗi AI action, khoảng dòng 810) map `AI_NOT_CONFIGURED` thành "Chưa cấu hình GEMINI_API_KEY", nhưng provider hiện là OpenAI chạy ở ai-service. Sửa câu này và dùng `err.message` từ BE (đã là tiếng Việt) cho các mã ở 0.1 #7; áp dụng cho cả luồng "Tạo bằng AI".
- [ ] **FE-7 (lỗi FE đã thấy khi test web deploy):**
  - Admin dashboard hiện tỉ lệ render thành công "-1%" (BE trả `-1` = không có dữ liệu; FE nên hiện "—").
  - Lỗi API bị nuốt rồi hiện "chưa được phân vào tổ chuyên môn" (các `catch {}` trong `workspaceService.ts`); cần hiện lỗi thật.
  - Thư viện hiển thị project chưa publish như đã publish.
  - Reviewer: nút duyệt vẫn còn sau khi đã duyệt (chưa refresh trạng thái).
  - Test `workspaceService.test.ts` ("resolveActiveWorkspaceId returns null…") đang fail trên main từ commit `23cfe79`.
- [ ] **FE-8:** API BE đã có nhưng FE chưa nối (mục 3.3): `/scripts/review-queue`, `/scripts/mine`, `/qa-comments/{id}/reopen`, `/scripts/{id}/qa-report` (giờ có `CAPTION_SYNC`), `/render/jobs/{id}/retry`, `/library/*`, `/admin/ai-usage`.

### 0.4. BE còn lại

**Cập nhật 18/10/2026:** đã làm theo checklist BE trong `STEMotion_FE/docs/FE_ACCOMPLISHMENTS_TODO.md` mục 9 (migration V20): comment/OPEN gate theo clip, `GET /scripts/{id}/review-history`, Writer được resolve (UC-26) + `resolvedBy/resolvedAt`, reply thread, TTS-preview và sync-durations qua BE, duyệt composition trên web player (`submit-video-review`), visual theme, cấu hình template.

Còn lại: thư viện chung hay theo workspace (cần team chốt); YouTube OAuth, asset upload/gợi ý asset, SCORM (P1/P2, lý do ghi ở checklist FE mục 9).

## 1. Tổng quan

- **Tổng số API theo đặc tả: chưa có con số HTTP chính thức.** PDF mô tả chức năng, không định nghĩa method/path. Diagram use case chính có **34 use case**, không được quy đổi 1:1 thành endpoint. Mã UC-01…UC-34 trong báo cáo này là mã truy vết do audit gán, không phải mã đã ban hành trong SRS.
- **BE: 72 operations / 14 RestController**, tính cặp method + path; có tính `/auth/register` đang bị khóa. Không tính Swagger/Actuator/framework HEAD/OPTIONS.
- **AI FastAPI: 10 operations nghiệp vụ/health**, gồm 4 route đăng ký động; `/openapi.json` do framework cung cấp được ghi riêng.
- **Worker local: 8 routes Express khai báo trực tiếp**, cộng 2 static mounts `/audio`, `/images`; callback nhận ở BE, không phải route worker.
- **FE cần: 43 cặp method/path trong section 3** của `FE_ACCOMPLISHMENTS_TODO.md`; **13 trùng route BE**, **30 khác route/method** sau chuẩn hóa tên biến đường dẫn. Khác route không đồng nghĩa thiếu chức năng. Section 7 còn yêu cầu `GET /publications` ngoài 43 dòng này.
- **FE source local: 42 operations trong service layer**, trong đó `/workspace-invitations/accept` đã bị BE bỏ. **FE origin/main đã có local: 49 operations**, tất cả có route tương ứng trong BE; chưa đồng nghĩa field, quyền và hành vi đã khớp. Không cộng direct fetch ở component cũ vào số service-layer này.
- Swagger BE snapshot có **61 operations: 60 trùng code, 12 code-only, 1 snapshot-only**. Đây là so sánh snapshot trong repo, chưa phải spec lấy từ runtime hiện tại.

### 1.1. Chốt nguồn và giới hạn kiểm chứng

| Nguồn | Phiên bản / cách đọc | Cách sử dụng |
|---|---|---|
| BE `STEMotion_BE` | Đã chuyển theo chỉ dẫn của người dùng sang `main`, fast-forward tới ref local `origin/main`, SHA `c7fbc49` | Nguồn BE chính; đủ V1–V19 và `services/ai/` |
| BE nhánh ban đầu | `feat/narrative-arc-step1`, SHA `6317685`, chỉ V1–V4 | Không dùng kết luận thiếu API từ nhánh này |
| FE checkout | `docs/checklist-2026-10-14`, SHA `daaf47f` | Ghi nhận mismatch của bản đang checkout |
| FE ref local `origin/main` | SHA `5d3b438`, đọc qua `git show`, không checkout | Nguồn đối chiếu FE mới hơn; khớp nhiều chức năng mô tả trong file FE |
| Worker checkout | `main`, SHA `16c984e`; ref `origin/main` là `712078f` | Audit checkout và ghi khác biệt ref mới; không checkout/pull worker |
| PDF | `FA26SE054_STEMOTION_AI_ASSISTED_STEM_VIDEO_PRODUCT_hungld.pdf`, 5 trang | PyMuPDF trích text, render và xem đủ 5 trang |
| Diagram | Toàn bộ 21 file kể cả backup và Phase0 | XML/PlantUML/metadata PNG + xem các ảnh; inventory ở phụ lục |
| Ghi chú cũ | `ERD_Review_Notes.md`, `architecture-context.md`, `proposal-vs-implementation.md`, Phase0 | Nguồn yêu cầu/lịch sử, không lấy kết luận “chưa có FastAPI/captions/composition” làm trạng thái hiện tại |

Không fetch remote mới, không xác nhận SHA production. Không đọc giá trị secret trong `.env`. Không khởi động BE kết nối DB chỉ để export Swagger vì startup có thể áp Flyway. Lúc kiểm tra không thấy local listener ở 8080/8000/8001/4000; phần runtime chưa được kiểm chứng được ghi rõ.

Thay đổi `.gitignore` có sẵn đã được lưu bản sao/patch trong `.audit/` trước khi fast-forward; các dòng ignore riêng của người dùng được giữ lại. Không tạo commit merge; đây là fast-forward ref đã có local.

### 1.2. Quy ước trạng thái và auth

- ✅ **Done**: có triển khai và test tương ứng trong repo hoặc có bằng chứng chạy; ghi rõ mức test, không suy ra đã pass production.
- ❌ **Missing**: không có khả năng phục vụ UC/FE, không chỉ khác tên route.
- ⚠️ **Partial**: có route nhưng contract, validation, persistence hoặc phạm vi UC còn thiếu.
- 🔍 **Unverified**: có code, chưa đủ bằng chứng test cho operation cụ thể.

Mặc định BE dùng JWT. `ResourceAccessInterceptor` kiểm tra membership ACTIVE theo workspace/project/job/scene/comment và chặn user `mustChangePassword` ngoài `/auth/me`, `/auth/change-password`. `WorkspaceAccess` cho system ADMIN đi qua các kiểm tra role workspace. API liệt kê `/members` cũng yêu cầu leader/admin qua interceptor, dù controller không có `@PreAuthorize`. Vì vậy không suy quyền chỉ từ annotation.

Response thành công BE trả **DTO trực tiếp**, không có success envelope chung. Lỗi advice là `{status, code, message, timestamp}`; security entrypoint 401 có thể không cùng body. File FE section 4 mô tả `{success:false,error:{…},meta:{…}}` khác code. `apiClient` hiện hỗ trợ đọc lỗi flat, nhưng team phải chốt định dạng chính thức.

## 2. Chi tiết từng API

Bảng đầy đủ method/path/request DTO/response DTO/`@PreAuthorize`/success status/source và tham số ở **phụ lục A**. Các nhóm dưới nêu mức hoàn thiện UC và khoảng trống cần quyết định.

### 2.1. Auth & Users

| Method | Path (prefix `/api/v1`) | Mô tả | UC liên quan | Trạng thái | Ghi chú |
|---|---|---|---|---|---|
| POST | `/auth/login` | JWT email/password | UC-01 | ✅ Done | `AuthControllerTest`; tài khoản INACTIVE/BANNED bị từ chối; không có refresh-token API |
| POST | `/auth/google` | Google ID credential | UC-01 | ✅ Done | `GoogleSignInTest`; chỉ tài khoản admin đã cấp, cần cấu hình Google audience |
| GET | `/auth/google/enabled` | Cờ cấu hình Google | UC-01 | 🔍 Unverified | Public; cấu hình có không chứng minh OAuth chạy |
| GET | `/auth/me` | Profile + mustChangePassword | UC-01 | ✅ Done | JWT, DTO có flag bắt buộc đổi password |
| POST | `/auth/change-password` | Đổi password | UC-01 | ✅ Done | currentPassword/newPassword, tối thiểu 8 ký tự; first-login gate |
| POST | `/auth/register` | Route giữ tương thích | UC-01 | ✅ Done | **Cố ý khóa**: SecurityConfig denyAll; service trả forbidden; không coi là public registration hoàn thành |
| POST/GET | `/users` | Cấp/list tài khoản | UC-23 | ✅ Done | ADMIN; cấp bằng RegisterRequest, không phải `/admin/users/provision` |
| GET/PATCH/DELETE | `/users/{id}` | Xem/sửa/xóa tài khoản | UC-23 | ✅ Done | GET admin hoặc self; sửa/xóa ADMIN; test provisioning/access |

FE ghi “tự động làm mới phiên” nhưng không có refresh token/rotation API trong code. Logout hiện client xóa token, không có revocation; cần chốt đây có phải yêu cầu còn lại hay chỉ sửa mô tả.

### 2.2. Workspaces

| Method | Path (prefix `/api/v1`) | Mô tả | UC liên quan | Trạng thái | Ghi chú |
|---|---|---|---|---|---|
| GET | `/workspaces`, `/workspaces/mine` | Workspace người dùng đọc được | EXT-W | ✅ Done | Cả hai gọi findMine; ADMIN thấy tất cả, user thấy membership |
| GET | `/workspaces/{id}` | Chi tiết | EXT-W | ✅ Done | Kiểm tra quyền theo workspace |
| POST/PUT/DELETE | `/workspaces`, `/workspaces/{id}` | Tạo/sửa/xóa | EXT-W | ✅ Done | ADMIN; tạo cần `leaderUserId`; test WorkspaceServiceTest |
| GET | `/workspaces/{id}/members`, `/workspaces/{id}/candidates` | Thành viên/ứng viên | EXT-W | ✅ Done | Leader hoặc system ADMIN; member thường không được list qua các route này |
| POST | `/workspaces/{id}/members` | Gán user có sẵn | EXT-W | ✅ Done | `{userId,role,leader}`; alias `isLeader`; không mời email |
| PATCH/DELETE | `/workspaces/{id}/members/{memberId}` | Role/status/leader hoặc remove | EXT-W | ✅ Done | ACTIVE/INACTIVE; workspace role WRITER/REVIEWER/PRODUCER |
| POST | `/workspace-invitations/accept` | Invitation cũ FE local còn gọi | EXT-W | ❌ Missing | Đã chủ động bỏ ở V17; **không khuyến nghị phục hồi** nếu giữ policy admin provision |

FE local cũ gửi `{email,role}`, không có `userId`; tự tạo workspace khi chưa có membership và không truyền leader. FE origin/main đã chuyển sang policy V17; cần chốt dùng đúng FE commit trước khi test reviewer/producer.

### 2.3. Projects

| Method | Path (prefix `/api/v1`) | Mô tả | UC liên quan | Trạng thái | Ghi chú |
|---|---|---|---|---|---|
| GET/POST | `/workspaces/{workspaceId}/scripts` | List/tạo project + version đầu | UC-02, UC-32 | ✅ Done | Project dùng resource tên scripts; POST role WRITER trong workspace |
| GET | `/scripts/mine`, `/scripts/{id}` | Project của tôi/chi tiết | UC-02, UC-32 | ✅ Done | `{id}` thực chất projectId |
| PUT | `/scripts/{id}` | Metadata + toàn bộ scenes | UC-03, UC-04 | ✅ Done | Giữ scene ID; chỉ draft/change-requested, không sửa bản đã submit/approve |
| DELETE | `/scripts/{id}` | Archive project | UC-03 | ⚠️ Partial | Service cho owner/leader/admin, nhưng interceptor vẫn đòi WRITER với DELETE resource gốc; reviewer/producer làm leader có thể bị chặn trước service |

Không có `/projects` controller riêng; đây là khác vocabulary giữa tài liệu FE và contract đang chạy. Archive không hard-delete reviewed history. Không có endpoint restore/list archived riêng. Danh sách workspace scripts chưa phân trang/tìm kiếm server-side như nhu cầu mở rộng của FE.

### 2.4. Scripts & Scenes, Reviewer

| Method | Path (prefix `/api/v1`) | Mô tả | UC liên quan | Trạng thái | Ghi chú |
|---|---|---|---|---|---|
| POST | `/scripts/{id}/submit-review` | Nộp/resubmit | UC-07 | ✅ Done | Có test SubmittedScriptTest; cần scene, trạng thái hợp lệ |
| GET | `/scripts/review-queue` | Queue reviewer | UC-08 | ✅ Done | Chỉ workspace user có role REVIEWER; test scope |
| POST | `/scripts/{id}/approve` | Approve script | UC-09 | ✅ Done | Ghi event target SCRIPT/version; note tùy chọn |
| POST | `/scripts/{id}/request-changes` | Reject/return script, tạo version mới | UC-10 | ⚠️ Partial | Nhận `{note}` nhưng body/note đều tùy chọn; không bắt buộc lý do reject |
| GET | `/scripts/{id}/versions`, `/scripts/{id}/diff?left=&right=` | History/diff script | UC-06, UC-15 | ✅ Done | left/right là version IDs; kiểm tra thuộc project |
| GET/POST | `/scripts/{projectId}/qa-comments` | List/create comment | UC-11, UC-14, UC-26 | ⚠️ Partial | Script inline hoặc timestamp; chưa có clipId/renderJobId filter, reply, anchor |
| GET | `/scenes/{sceneId}/comments` | Inline per-scene | UC-11 | 🔍 Unverified | Kiểm tra membership qua scene |
| PUT | `/qa-comments/{commentId}/resolve` | Mark resolved | UC-26 | ⚠️ Partial | Chỉ PRODUCER/REVIEWER (+ADMIN); Writer trong UC Read & Resolve Feedback chưa được phép; không lưu resolvedBy/resolvedAt |
| PUT/DELETE | `/qa-comments/{commentId}/reopen`, `/qa-comments/{commentId}` | Mở lại/xóa | UC-26 | ✅ Done | Reopen REVIEWER; delete author hoặc ADMIN; ProjectAndNoteGuardsTest |
| GET | `/scripts/{projectId}/qa-report` | Pre-publish QA | UC-28 | ⚠️ Partial | Heuristic duration/pacing/char-count/contrast; không đo caption/audio sync thật |
| POST | `/scripts/{projectId}/approve-video` | Approve current Clip | UC-12, UC-13 | ✅ Done | IN_QA + hết OPEN notes + current artifact; review event CLIP |
| POST | `/scripts/{projectId}/send-back` | Return clip cho Producer | UC-12, UC-16 | ⚠️ Partial | Có lưu reason từ `{note}`, nhưng không bắt buộc; FE service gửi `{}` nên reason bị mất |
| GET (đề xuất) | `/scripts/{id}/review-history` | Đọc quyết định + reviewer/reason/target | UC-08…UC-16 | ❌ Missing | `StatusHistoryService.findByProject` có nội bộ nhưng chưa expose controller |
| GET (đề xuất) | `/scripts/{id}/clips` | Chọn artifact/re-render để compare/review | UC-15 | ❌ Missing | Đã có render history + videoUrl, chưa public stable clipId/current clip DTO |
| POST (đề xuất) | `/qa-comments/{id}/replies` | Trao đổi theo từng góp ý | UC-26 / FE TODO | ❌ Missing | Cần parentId/thread + quyền và persistence |

**Rủi ro vòng feedback:** list/count OPEN đang theo project, không theo Clip. Note cũ của render A có thể xuất hiện ở render B và chặn approve B; không có API trả lịch sử quyết định để Producer đọc reason của lần trả về. Không xóa comment cũ để né lỗi; cần target artifact/version và chính sách carry-forward rõ ràng.

### 2.5. AI Features

| Method | Path (prefix `/api/v1`) | Mô tả | UC liên quan | Trạng thái | Ghi chú |
|---|---|---|---|---|---|
| GET | `/ai/health` | Configured flag | UC-05 | 🔍 Unverified | JWT; flag cấu hình, không phải readiness của model |
| POST | `/ai/script/generate`, `/ai/script/approaches` | Draft và 3 cách dẫn chuyện | UC-05 | ✅ Done | AiScriptServiceTest/AiApproachTest; response có requestId và usage metadata |
| POST | `/ai/script/action` | resegment/grade/extract/terms | UC-05 | ✅ Done | Không có `/segment` riêng ở public BE; action phải truyền đúng enum |
| PUT | `/ai/requests/{requestId}/decision` | ACCEPTED/IGNORED | UC-25 | ✅ Done | Kiểm tra requester; quyết định không tự save nội dung script |
| POST | `/ai/transcribe/align` | Upload audio + known text | UC-20 | ✅ Done | Multipart; Writer/Producer active ở ít nhất một workspace; test access/client |
| POST | `/ai/internal/transcribe/align` | Worker-to-BE alignment | UC-20 | ✅ Done | `X-AI-Alignment-Key`; thiếu config 503, key sai 401 |
| GET/POST (đề xuất) | `/scripts/{id}/tts-preview`, `/scripts/{id}/sync-durations` | Proxy TTS cho FE | UC-20 | ❌ Missing | Worker đã có compute route dùng shared key; BE chưa proxy và authorize theo project |
| POST (đề xuất) | `/scripts/{id}/assets/suggestions` | Suggest/generate assets | UC-19 | ❌ Missing | Icon/visualPrompt không tương đương asset catalog hoặc image-generation API |

FastAPI chuyên biệt `/script/resegment|grade|extract|terms` có, nhưng **không expose trực tiếp cho browser**. TTS hiện ở Node worker, không nằm trong FastAPI như tên gói chức năng trong PDF; chấp nhận được nếu team cập nhật kiến trúc. Alignment có source và test, nhưng worker local/ref origin/main đang đọc **không có hook gọi align/caption generation**; không dùng lời xác nhận happy-case của người dùng để suy ra checkout worker này đã tích hợp Whisper. Cần xác định artifact/entry thực sự được deploy và chứng minh bằng payload/audio/caption trước khi đóng UC-20.

### 2.6. Render & Worker, Production, Publish, Library

| Method | Path (prefix `/api/v1`) | Mô tả | UC liên quan | Trạng thái | Ghi chú |
|---|---|---|---|---|---|
| GET/PUT | `/scripts/{projectId}/composition` | Open/save Producer draft | UC-17, UC-18 | ✅ Done | `{baseRevision,scenes}`; revision 0 trước save; 409 stale/locked; giữ ID/order/type của script đã approve |
| POST | `/render/projects/{projectId}/start` | Render saved composition | UC-21 | ✅ Done | 202; snapshot input_props + compositionRevision; output clamp 1080p30 |
| GET | `/render/status/{jobId}`, `/render/projects/{projectId}/jobs` | Progress/history | UC-22, UC-15 | ✅ Done | History có scriptVersionNo; chưa phải clip selection/review API |
| POST | `/render/jobs/{jobId}/retry` | Retry | UC-21 | ✅ Done | Chỉ FAILED, budget; dùng snapshot cũ |
| DELETE | `/render/jobs/{jobId}` | Cancel | UC-21 | ⚠️ Partial | Worker không abort Chromium job đang chạy; in-process chỉ xóa pending bookkeeping, BullMQ remove active có thể lỗi; BE nuốt lỗi cancel rồi vẫn đánh FAILED/cancelled |
| POST | `/render/callback` | Progress/completed webhook | UC-22 | ✅ Done | Shared key; terminal callbacks idempotent, chống progress đi lùi; không có attemptId trong payload |
| GET | `/scripts/{projectId}/publications` | Publish history | UC-29 | 🔍 Unverified | Legacy PublishResponse, không phải library gallery |
| POST | `/scripts/{projectId}/publish` | Internal/LMS publish | UC-29 | ⚠️ Partial | Internal artifact-backed có test; YOUTUBE trả 503 PUBLISH_TARGET_UNAVAILABLE |
| GET | `/library/clips`, `/library/clips/{projectId}`, `/library/filters` | Library/export | UC-30 | ⚠️ Partial | Có subject/grade + video/embed; thiếu workspace/query/date/pagination; list chưa scope workspace |
| POST/GET (đề xuất) | `/integrations/youtube/connect`, `/integrations/youtube/callback` | YouTube OAuth | UC-29 | ❌ Missing | Chưa có channel credentials/token lifecycle |
| POST (đề xuất) | `/scripts/{id}/assets` | Upload media vào catalog | UC-19 | ❌ Missing | Chưa có API/entity asset_media/scene_assets trong app |

Callback hiện không gửi measuredDuration, caption artifact, checksum hoặc attempt token. `clips.duration_sec` có thể NULL. QA còn dựa trên scene timing/narration heuristic; chưa có bằng chứng kiểm tra video/audio output thực tế. Re-render cùng composition có snapshot nhưng chưa có dedupe key/idempotency API theo composition revision; snapshot tái lập input không tự đảm bảo chỉ có một output/job.

Rủi ro concurrency chưa tái hiện: render start kiểm tra trạng thái rồi dispatch trong transaction, chưa thấy DB lock/unique active-job hoặc dispatch-after-commit; callback nhanh có thể đến trước commit. Composition lock chỉ khóa row đã có: hai lần save đầu tiên cùng baseRevision=0 có thể đụng UNIQUE script_version_id và thành 500 thay vì 409 nếu không xử lý riêng. Cần test đồng thời trên DB cô lập trước khi đóng NFR, không suy từ unit tests.

Không cần tạo alias `/render/jobs` chỉ để khớp file FE: service FE cả local và origin/main đã dùng route BE hiện tại. Cần sửa tài liệu trước hoặc ban hành contract mới có migration FE rõ ràng.

### 2.7. Admin

| Method | Path (prefix `/api/v1`) | Mô tả | UC liên quan | Trạng thái | Ghi chú |
|---|---|---|---|---|---|
| GET | `/admin/metrics`, `/admin/overview`, `/admin/render-jobs` | Dashboard/queue | UC-24, UC-31 | ⚠️ Partial | Có AdminMetricsServiceTest; storage là filesystem BE host, không phải object storage/worker; GPU=-1 unavailable |
| GET | `/admin/ai-usage` | Theo ngày/model/user, paging | UC-31 | ✅ Done | ADMIN; from/to, model,userId,page,size; tiền không biết giữ null; có pricing provenance V18 |
| GET | `/templates`, `/templates/{id}` | Catalog template | UC-27 | ✅ Done | category/activeOnly; default activeOnly=true |
| POST/PUT/DELETE | `/templates`, `/templates/{id}` | CRUD metadata | UC-27 | ✅ Done | ADMIN; schema/subject/assetPack/slots; không tự tạo Remotion component mới |

FE admin dùng PUT, tài liệu section 3 ghi PATCH; `propsSchema`, `slots` là JSON **string** trong DTO BE, không phải object JSON tự do. CandidateUserResponse không có `systemRole` mà FE origin/main type khai báo; không chặn add member nhưng cần chốt field.

## 3. Mismatch BE ↔ FE

### 3.1. Top 3 cần họp chốt

1. **Nguồn contract và commit dùng để tích hợp:** FE document ghi `/projects`, `/admin/users`, `/render/jobs`; FE origin/main thực tế dùng `/scripts`, `/users`, `/render/projects/{id}/start` và khớp BE. Chọn contract hiện tại làm chuẩn hay đổi API; không thực hiện cả hai ngầm. Snapshot Swagger cũng stale.
2. **Contract review theo artifact:** `{note}` hay `reason`, lý do reject có bắt buộc, ai được resolve, note gắn scriptVersionId/clipId và carry-forward thế nào. FE `sendBackVideo` vẫn gửi `{}`, BE không reject body trống; thiếu lịch sử đọc lại.
3. **Production boundary và phát hành:** FE mới đã save composition qua baseRevision, trong khi FE checkout cũ chưa có service này; TTS-preview cần BE proxy thay vì lộ shared worker key; worker nào có Whisper đang chạy phải xác định. Chốt các commit FE/BE/worker cho acceptance và YouTube là chưa available.

### 3.2. Endpoint FE document gọi nhưng route BE chưa có

Danh sách đủ 43 dòng và mapping chi tiết được sinh ở phụ lục B. **30 route khác** được giải thích theo nhóm:

| FE document | BE tương ứng / quyết định |
|---|---|
| GET `/projects?workspaceId={id}`; POST `/projects` | GET/POST `/workspaces/{workspaceId}/scripts` |
| GET `/projects/{id}`; GET `/projects/{id}/scripts/current` | GET `/scripts/{id}` |
| POST `/projects/{id}/archive` | DELETE `/scripts/{id}` là archive, cần sửa semantic trong tài liệu |
| GET `/projects/{id}/versions` | GET `/scripts/{id}/versions` |
| PUT/DELETE `/projects/{id}/scripts/scenes/{sceneId}`; POST `/projects/{id}/scripts/reorder` | PUT `/scripts/{id}` toàn danh sách scene; chưa có per-scene API. FE source dùng toàn danh sách; không tự coi ba route này là bắt buộc |
| POST `/ai/script/segment` | POST `/ai/script/action` với action=resegment |
| POST `/ai/requests/{id}/decision` | **PUT** `/ai/requests/{requestId}/decision` |
| POST `/ai/script/pedagogical-review` | Có grade/terms/extract qua action; không có đánh giá sư phạm tổng hợp theo schema riêng |
| GET/POST `/projects/{id}/comments` | GET/POST `/scripts/{projectId}/qa-comments` |
| POST `/projects/{id}/review/decision` | POST `/scripts/{id}/approve` hoặc `/request-changes`; video dùng approve-video/send-back |
| GET/PUT `/projects/{id}/composition` | GET/PUT `/scripts/{projectId}/composition` |
| POST `/render/jobs` | POST `/render/projects/{projectId}/start` |
| GET `/render/jobs/{id}/status` | GET `/render/status/{jobId}` |
| POST `/render/jobs/{id}/cancel` | DELETE `/render/jobs/{jobId}` |
| POST `/publications/youtube` | Không có real YouTube upload; `/scripts/{id}/publish` target YOUTUBE bị 503 |
| GET `/publications/{id}/embed` | Embed field trong publication/library response; chưa route riêng |
| GET `/admin/metrics/overview` | GET `/admin/overview` |
| GET `/admin/users`; POST `/admin/users/provision`; PATCH/DELETE `/admin/users/{id}` | GET/POST `/users`; PATCH/DELETE `/users/{id}` |
| GET/POST `/admin/templates`; PATCH `/admin/templates/{id}` | GET/POST `/templates`; **PUT** `/templates/{id}` |

### 3.3. BE có nhưng FE chưa dùng

Phụ lục C liệt kê đầy đủ 23 operations chưa được service FE origin/main tham chiếu, có phân biệt endpoint nội bộ/health/cố ý khóa. Cần nối đặc biệt: `/scripts/review-queue`, `/scripts/mine`, `/qa-comments/{id}/reopen`, `/scripts/{id}/qa-report`, `/render/jobs/{id}/retry`, `/library/*`, `/admin/ai-usage`. Không coi callback/internal alignment là FE TODO.

FE local còn direct fetch worker ở component `ProducerView`, `VideoCreationStudio`, và nguồn FE mới cũng có worker calls cần kiểm chứng reachability/auth. Các component cũ không được mặc định là main flow đang dùng. Worker compute route cần X-Api-Key, browser không nên giữ server shared secret; API path tồn tại vẫn có thể 401.

### 3.4. Field / behavior mismatch quan trọng

- Video send-back FE gửi `{}`; BE nhận `{note}` tùy chọn; không có reason để Producer đọc lại qua API.
- Script return FE có note nhưng BE không validate not-blank. UI bắt buộc không thay thế validation server.
- `resolve` không cho Writer mặc dù diagram UC-26 gắn Writer; thiếu resolvedBy/resolvedAt/reply.
- FE local invitation policy trước V17 khác BE admin assignment; FE origin/main đã khớp `{userId,role,isLeader}`.
- FE document gọi library `GET /publications` ở TODO section 7; BE là `/library/clips`; FE gallery chưa nối đầy đủ.
- FE document nói error envelope, refresh-token, YouTube hoàn thành trong section 1 nhưng section 5 lại ghi unavailable. Không dùng dấu [x] làm bằng chứng E2E.
- BE StartRenderRequest vẫn có resolution/fps nhưng service clamp 1920x1080@30. FE TODO 9:16/multitrack là thay đổi contract/layout, không chỉ thêm nút.
- Library list/filter không có workspaceId. Nếu thư viện chia sẻ toàn hệ thống là chủ ý thì ghi SRS; nếu phải private theo workspace thì cần sửa trước khi release.
- API archive có chồng quyền interceptor/annotation như mục 2.3; cần test owner/leader mang role REVIEWER và PRODUCER.

## 4. Mismatch Đặc tả ↔ Thực tế

### 4.1. UC thiếu API hoặc chưa đủ năng lực

- UC-19 Swap Suggested Assets: thiếu upload/catalog/attach/detach/media ownership và bảng nối trong app.
- UC-20 Regenerate/Tune Narration & Captions: FastAPI align có; chưa BE proxy TTS-preview/sync và chưa thấy worker source đang audit gọi Whisper; thiếu quản lý caption/audio artifact theo revision.
- UC-15 Compare Versions: script diff và render history có; thiếu API stable clip ID + snapshot/metadata để review/compare từng artifact, thiếu composition history immutable đọc lại.
- UC-26 Read & Resolve Feedback: có flat comment/resolve, thiếu Writer resolve, actor/time và threads mà FE TODO yêu cầu.
- UC-28 QA: thiếu caption/audio sync thật; overflow/contrast là heuristic, không kiểm tra rendered frame.
- UC-29 Publish YouTube: thiếu OAuth connect/callback/upload/status, metadata/thumbnail/privacy/tags; đang explicit 503.
- UC-24 Monitor Storage: metrics BE filesystem chưa phải dung lượng asset/video object store.
- UC-31 AI usage: có persistence/aggregation/pricing provenance; chưa FE drill-down service cho `/admin/ai-usage`.

Không khẳng định tất cả các UC này “không có API nào”: đa số **Partial**, chi tiết ở bảng. Riêng upload/swap asset và YouTube OAuth/upload chưa có endpoint phục vụ đầy đủ.

### 4.2. Phát sinh ngoài PDF / cần cập nhật hồ sơ

- Workspace, membership, leader, candidates, first-login password policy, archive và health/readiness là mở rộng triển khai.
- AI narrative approaches, COMPOSED/ILLUSTRATED_EXPLAINER, pricing provenance, internal alignment bridge là mở rộng kỹ thuật/chức năng.
- Asset generation ảnh AI, 9:16/1:1, BGM/SFX, annotations vẽ, reply threads, WebSocket/presence/locking, SCORM/LTI, dark mode, undo/redo, export Word/PDF là FE roadmap. PDF không yêu cầu rõ tất cả các hạng mục này; không nâng toàn bộ thành P0 bắt buộc đồ án.
- TTS Node thay vì FastAPI; providers OpenAI/Gemini cấu hình được. `architecture-context.md` và `proposal-vs-implementation.md` ngày 10/10 đã lỗi thời về FastAPI/Google login/mock render/Whisper; cần refresh sau khi team duyệt audit.
- DB diagram scenes thuộc composition, code vẫn giữ scenes của script và composition.scenes JSONB snapshot. Có composition riêng ở V19 nhưng chưa ánh xạ 1:1 với mô hình scene vật lý gốc.
- ReviewDecision được consolidate vào append-only status_histories V11, không có entity/bảng review_decisions riêng từ migrations. Cần cập nhật ERD và ghi quyết định thiết kế.
- Reliability: snapshot input có, dedupe idempotency per composition revision chưa có; retry tái dùng jobId không kèm attemptId làm callback cũ khó phân biệt. Chưa test tải 300 jobs/ngày, 200 concurrent, AI <15s/render <5 phút/dashboard <3s.

## 5. Ưu tiên triển khai

### 5.1. P0 — độ đúng của flow / contract hiện có

1. Chốt FE `origin/main` + BE `c7fbc49` + worker deployed SHA; cập nhật Swagger snapshot, sửa danh mục endpoint FE. Chứng minh worker captions trên đúng source/artifact, không sửa code trong phase audit.
2. Bắt buộc nonblank note khi script request-changes/video send-back; FE gửi note; expose review history theo target và reviewer. Đây là **mở rộng route đã có**, không cần tạo thêm route reject trùng chức năng.
3. Gắn comment/OPEN gate vào scriptVersionId/clipId hiện hành; chốt carry-forward và Writer resolve. Tránh note render cũ chặn clip mới.
4. Nối composition `{baseRevision,scenes}` và flush trước render; test return → Producer edit → rerender → review, và sửa quyền archive owner/leader nếu test xác nhận chặn.

### 5.2. P1 — UC trọng tâm còn thiếu

1. Clip list/detail/review-history DTO; expose clipId trong render history và quyết định review để compare A/B đáng tin cậy.
2. BE proxy TTS-preview/sync-durations + caption align/save theo project/revision; thêm artifact metrics và QA caption/audio sync.
3. Asset upload/list/delete + attach/detach slot; quyền workspace và MIME/size/URL policy; entity/migration catalog.
4. YouTube OAuth/connect/callback + publish job/status nếu giữ phạm vi one-click publish trong đề cương.
5. Library workspace scope/search/paging; nối gallery FE. Storage metrics thật, AI usage drill-down.

### 5.3. P2 — mở rộng và UX

Threads/replies và frame drawing; 9:16/1:1, multitrack BGM/SFX; realtime/presence/locking; AI image generation; SCORM/LTI; export Word/PDF; undo/redo/dark mode. Ưu tiên riêng nếu team bổ sung vào acceptance chính thức.

### 5.4. Top 5 API/contract cần bổ sung cho Reviewer/Producer

| Ưu tiên | Endpoint đề xuất hoặc mở rộng | Kết quả UC cần đạt |
|---|---|---|
| 1 | Mở rộng POST `/scripts/{id}/request-changes`, POST `/scripts/{id}/send-back` | Lý do reject bắt buộc, lưu đúng target; FE không gửi body rỗng |
| 2 | GET `/scripts/{id}/review-history` | Writer/Producer đọc reviewer, reason, time, scriptVersionId/clipId và decision |
| 3 | GET `/scripts/{id}/clips`, GET `/clips/{clipId}` + mở rộng comment DTO/filter | Chọn đúng lần render, compare A/B và chỉ đếm note của artifact được review |
| 4 | POST `/qa-comments/{id}/replies` + mở rộng resolve | Feedback loop có trao đổi, resolvedBy/resolvedAt và Writer/Producer/Reviewer đúng quyền |
| 5 | GET `/scripts/{id}/tts-preview`, POST `/scripts/{id}/sync-durations` + caption save | Producer nghe/sync/cập nhật captions bằng auth người dùng, shared key chỉ ở server |

Tên route đề xuất chưa phải contract đã chốt. Asset và YouTube đứng tiếp theo ở P1. Reviewer reject/Producer return **đã có endpoint**, phần thiếu là validation, FE payload, read API và artifact scoping.

### 5.5. Acceptance checklist sau khi team duyệt

- [ ] Writer submit v1 → Reviewer reject có reason → Writer đọc reason/history → sửa v2/resubmit → approve; v1 bất biến, comment không mất.
- [ ] Producer mở composition approved, save revision với baseRevision; conflict 409 khi stale; không ghi vào script đã approve.
- [ ] Render A → Reviewer timestamp note + send-back(reason) → Producer thấy reason/note → sửa/reply/resolve → render B → Reviewer xem A/B → approve B; note A không vô tình chặn B.
- [ ] Không được approve/render/publish sai workspace/role; kiểm tra Writer resolve theo quyết định team; leader archive với từng role.
- [ ] TTS-preview qua BE, captions đo từ audio, QA báo sync thực; callback late/retry không ghi đè artifact đúng.
- [ ] Internal publish + library có workspace policy; YouTube unavailable được hiển thị trung thực tới khi upload thật có acceptance.

## 6. FastAPI: route, models, auth, env

Tất cả route compute dùng `X-AI-Api-Key`, so sánh constant-time qua require_service_key; key trống 503, sai/thiếu 401. Health/ready public. Không có JWT người dùng; BE chịu trách nhiệm RBAC. Schema errors 422; provider timeout 504, lỗi provider/validation 502, thiếu cấu hình provider 503. Test provider dùng stub, không gọi LLM tính phí.

| Method | Path | Request | Response | Auth / status |
|---|---|---|---|---|
| GET | `/health` | — | status/provider/configured dict | Public, 200; không chứng minh model ready |
| GET | `/ready` | — | status/configured/weightsPresent/whisperVerified | Public, 200 hoặc 503 |
| POST | `/internal/v1/generate` | GenerationRequest | GenerationResponse | Service key; 200/401/422/502/503/504 |
| POST | `/script/generate` | ScriptInput | GenerationResponse | Service key; như trên |
| POST | `/script/action` | ActionInput | GenerationResponse | Service key; như trên |
| POST | `/script/resegment` | AnalysisInput | GenerationResponse | add_api_route động; service key |
| POST | `/script/grade` | AnalysisInput | GenerationResponse | add_api_route động; service key |
| POST | `/script/extract` | AnalysisInput | GenerationResponse | add_api_route động; service key |
| POST | `/script/terms` | AnalysisInput | GenerationResponse | add_api_route động; service key |
| POST | `/transcribe/align` | multipart file,text,language=vi | dict alignment | Service key; 200/401/413/422/502/503 |
| GET | `/openapi.json` | — | OpenAPI | Framework route mặc định; docs/redoc UI disabled; không tính trong 10 operations trên |

Models: GenerationRequest `{systemInstruction(1..20000),prompt(1..80000),responseSchema,task=generate}`; task generate/resegment/grade/extract/terms. ScriptInput `{topic(1..1000),subject,gradeLevel(1..64),targetDurationSec?(0..300),narrativeBrief?(≤500),approach?(≤800)}`, extra fields forbidden. AnalysisInput `{scriptText(1..60000),subject?,gradeLevel?}`; ActionInput thêm action enum. GenerationResponse `{result:object,model,elapsedMs,inputTokens?,outputTokens?,totalTokens?}`; unknown usage=null. Align known text 1..10000, language 2..8, audio 1..10MiB; response schema chưa được ràng buộc bằng response_model. Fields output alignment cần lấy từ `alignment.py` trong appendix model evidence, không giả định trùng GenerationResponse.

| Env | Bắt buộc khi nào | Default / ghi chú |
|---|---|---|
| `AI_SERVICE_SHARED_KEY` hoặc legacy `AI_SERVICE_KEY` | Mọi compute call | Shared key mới ưu tiên; không có thì 503 |
| `OPENAI_API_KEY` | AI_PROVIDER=openai (default) | Không có key thì provider không configured |
| `GEMINI_API_KEY` | AI_PROVIDER=gemini | Chọn provider khác trả 503 |
| `AI_PROVIDER` | Có default | openai / gemini |
| `OPENAI_MODEL_DRAFT`, `OPENAI_MODEL_LIGHT` | Có default | gpt-4o / gpt-4o-mini |
| `GEMINI_MODEL` | Có default | gemini-3.5-flash-lite trong source; không suy ra model khả dụng tại provider |
| `WHISPER_MODEL_DIR` | Cần model assets ở path để readiness | `/opt/whisper/small`; model.bin, config.json, tokenizer.json phải tồn tại |
| `WHISPER_STARTUP_MODE` | Có default | required; optional cho smoke, ready vẫn 503 nếu thiếu weights |
| `WHISPER_STARTUP_TIMEOUT_SEC` | Có default | 90, hợp lệ 1..600 |
| `WHISPER_MODEL`, `WHISPER_LOCAL_FILES_ONLY`, `HF_HUB_OFFLINE`, `AI_WHISPER_VERIFIED` | Do launcher đặt | Offline model path + trạng thái preflight; không tự đặt verified để né kiểm tra |
| `PORT`, `UVICORN_WORKERS` | Có default | 8000 / 1; port 1..65535, workers >0 |

Whisper còn đọc `WHISPER_DEVICE=cpu`, `WHISPER_COMPUTE_TYPE=int8`; align chỉ nhận audio đã decode dài 0.1–30 giây và transcript vừa context. Response align: `{words:[{text,start,end}],segments:[{text,start,end,words}],model,durationSec,method:"forced-alignment",tokensIn:null,tokensOut:null}`; xem nguyên văn alignment.py trong evidence.

Không có một danh sách env “đều bắt buộc” cho mọi chế độ: key provider là điều kiện theo provider, Whisper weights bắt buộc với required startup. Env bổ sung nếu có trong source được lưu trong appendix evidence sinh từ quét `os.getenv`.

## 7. Worker: đầy đủ routes và callback

Nguồn chính `STEMotion_RENDER/src/server.ts`; compute guard `security.ts`, config `config.ts`. Public video/static hiện không có workspace authorization. API key sai/thiếu 401; worker chưa cấu hình key 503.

| Method | Path | Request | Response / status | Auth local |
|---|---|---|---|---|
| GET | `/health` | — | `{status:'UP',queue,remotionEntry}` 200 | Public |
| GET | `/render-status/:jobId` | jobId | JobStatusRecord hoặc `{error}`; 200/404 | Public local; ref origin/main đã thêm requireApiKey |
| GET | `/videos/for-script/:scriptId` | scriptId | `{exists,video?,allVideos?}`; 200/400/500 | Public local; ref origin/main đã thêm requireApiKey |
| GET | `/videos/:filename` | Range; download=1; filename? | MP4 200/206, missing 404 | Public; chưa validate Range đủ để đảm bảo 416 đúng chuẩn |
| GET | `/tts-preview` | query text | audio/mpeg stream, cache/range; 200/206/400/500 + guard errors | requireApiKey; không nhận per-request voice dù render hỗ trợ ttsVoice |
| POST | `/sync-durations` | `{script:{scenes},fps?}` | `{script,success:true}`; 200/400/500 + guard | requireApiKey; per-scene synthesis lỗi có thể fallback ước lượng thời lượng |
| POST | `/render` | RenderRequest | `{jobId,status:'QUEUED',estimatedSeconds}`; 202/400/500 + guard | requireApiKey |
| DELETE | `/render/:jobId` | jobId | 204 removed /404 không remove được | requireApiKey; active render không abort; queue.cancel rejection chưa có local try/catch ở Express4 handler |
| GET/HEAD | `/audio/*`, `/images/*` | Static path | File 200/206/304/404 theo Express | Public; 2 mounts, không phải 2 fixed operations |

RenderRequest `{jobId,composition:'FullSTEMVideo',inputProps:{script:{scenes}},width=1920,height=1080,fps=30,ttsVoice?,callbackUrl?}`. Guard yêu cầu ID safe 1..128, 1..100 scenes, fixed callbackURL, 1080p30, script fps=30. Deep props validation chủ yếu ở BE, worker envelope không thay thế validation scene.

Callback outbound: worker POST tới `API_CALLBACK_URL` (default BE `/api/v1/render/callback`), headers `X-Api-Key` và `X-Render-Api-Key`, JSON `{jobId,status,progress?,stage?,videoUrl?,errorMessage?}`. BE dùng X-Render-Api-Key. Callback lỗi network/non-2xx bị log rồi bỏ qua, không có durable retry/outbox. BE status API chỉ đọc DB, chưa tự poll worker để hòa giải callback bị mất. Queue retry và callback retry là hai vấn đề khác nhau.

Worker env quan trọng: RENDER_SERVICE_KEY, API_CALLBACK_URL, PUBLIC_BASE_URL, AUDIO_BASE_URL, REMOTION_ENTRY/SRC_ROOT; USE_TTS + TTS_PROVIDER/TTS_API_KEY/TTS_VOICE/TTS_SPEED; Redis và MinIO theo cờ enable. Không đưa secret worker vào bundle FE. Worker source được đọc không có route `/callback`, `/transcribe/align`, hoặc caption persistence.

## 8. Database V1–V19 và entity/relationship

Đã đọc đủ 19 file theo version số, không chạy lên DB. Chi tiết SQL nguyên văn về columns/constraints/indexes/triggers ở [API_AUDIT_EVIDENCE_2026-10-17.md](API_AUDIT_EVIDENCE_2026-10-17.md) để không làm mất các nhánh native enum/UUID so với varchar baseline.

| Migration | Tác động |
|---|---|
| V1 | 11 tables: users, workspaces, workspace_members, clip_projects, script_versions, scenes, feedback_comments, remotion_templates, render_jobs, publish_records, status_histories; PK/FK/check/index |
| V2 | Data patch nâng tài khoản dev thành ADMIN; không schema |
| V3 | Scene type ILLUSTRATED_EXPLAINER |
| V4 | Resolution RES_1440P |
| V5 | users.display_name + backfill + NOT NULL |
| V6 | scenes.order_index + đồng bộ order_no + NOT NULL |
| V7 | feedback target_type/script_version_id/scene_key/timestamp_val/clip_id + chk_feedback_target |
| V8 | Subject CS/ComputerScience bridge và users status BANNED/native enum |
| V9 | Invitation token hash/expiry + partial unique index; bị gỡ V17 |
| V10 | clips artifact, unique render_job_id, URL/duration checks, backfill completed jobs |
| V11 | Review event metadata trong status_histories; validate target trigger; append-only update/delete/truncate; bảo vệ reviewed script/clip |
| V12 | ai_requests + payload/status/token/cost + checks single optional target, nonnegative usage/currency; requester index |
| V13 | publications canonical + project_id + UNIQUE(project_id,platform); giữ publish_records adapter |
| V14 | remotion_templates.subject/asset_pack/slots JSONB + slots object CHECK |
| V15 | Scene type COMPOSED, varchar/native enum |
| V16 | Native enum workspace status thêm INACTIVE, transaction riêng |
| V17 | Xóa pending seat không user; INVITED→INACTIVE; bỏ invitation columns; user_id NOT NULL; is_leader; unique workspace/user; workspace roles; must_change_password |
| V18 | AI pricing version/rates + provenance constraint + usage index |
| V19 | compositions, scenes snapshot JSONB/revision + render_jobs composition FK/revision/input_props; index/check/backfill |

### 8.1. Tables và mapping hiện tại

V1 + bốn bảng thêm V10/V12/V13/V19 = **15 tables do migrations quản lý**, tương ứng 15 @Entity. Không tính flyway_schema_history; shared DB có thể có bảng legacy khác mà migration IF NOT EXISTS giữ nguyên.

| Table → Entity | Quan hệ / cardinality | Columns bổ sung / điểm đối chiếu |
|---|---|---|
| users → User | User 1:N projects, versions, jobs, comments, memberships | full_name + display_name đồng bộ; role/status/google_id/password_hash/must_change_password |
| workspaces → Workspace | 1:N members, projects | created_by→users; name,department |
| workspace_members → WorkspaceMember | Workspace N:M User qua membership | role/status/is_leader; unique(workspace_id,user_id); bỏ toàn bộ invitation fields |
| clip_projects → ClipProject | Workspace/owner N:1; 1:N versions/jobs/history/publish | script_status + video_status, derived pipelineStatus; archived,video_url,fps,total_duration_seconds |
| script_versions → ScriptVersion | Project N:1; 1:N scenes; 0..1 composition | version_no,review_note,is_current; unique(project_id,version_no); không unique partial current version |
| scenes → Scene | N:1 script_versions; props JSONB | order_no + order_index; template type enum; không FK composition_id/template_id theo diagram |
| feedback_comments → FeedbackComment | N:1 project, nullable scene, N:1 author | script_version_id/scene_key/target_type/timestamp_val; DB có clip_id nhưng entity không map/ghi; thiếu resolved_by/at |
| remotion_templates → RemotionTemplate | Metadata catalog độc lập | props_schema TEXT, slots JSONB; subject/asset_pack; scene dùng type chứ không FK template |
| render_jobs → RenderJob | N:1 project/version/requester; optional composition; 0..1 Clip | status/progress/resolution/fps/attempt_count, input_props snapshot, composition_revision |
| clips → Clip | 1:1 completed render job (unique FK); 1:N publications | video_url, duration_sec nullable, qa_status/report; chưa public clip controller |
| publish_records → PublishRecord | Project 1:N legacy adapter | targets CSV/title/description/embed/youtube_url; không canonical clip FK |
| publications → Publication | N:1 project/clip/publisher | platform/metadata/published_at; unique(project_id,platform), khác diagram unique(clip_id,platform) |
| status_histories → StatusHistory | Project 1:N; actor N:1; review target polymorphic | V11 trigger validate project/version/clip; append-only; chưa API GET |
| ai_requests → AiRequest | requester, optional một trong version/scene/clip | payload/response/decision/status/model/token/cost/pricing provenance; chưa FK project trực tiếp |
| compositions → Composition | ScriptVersion 1:0..1, producer/project N:1 | revision>=1, scenes JSON array; mỗi save ghi đè row và tăng revision, render giữ snapshot |

### 8.2. Mismatch DB cần team theo dõi

1. **Hai baseline khác nhau:** V1 IDs VARCHAR(36), BaseEntity hiện columnDefinition UUID; V10/12/13/19 tạo artifact UUID và lấy kiểu FK động. Không thể kết luận fresh V1–V19 khởi động được với ORM chỉ vì shared Supabase chạy; cần acceptance DB sạch + validate sau team duyệt, không chạy ở audit này.
2. **Feedback clip_id tồn tại DB nhưng không entity/request/response:** timestamp note mới vẫn gắn project; gate theo project; chặn compare/review từng artifact.
3. **Resolved audit thiếu:** không resolved_by/resolved_at/parent_id/anchor; is_resolved trong diagram khác status OPEN/RESOLVED trong code.
4. **Review decision:** V11 đã giải quyết persistence bằng status_histories + target validator; lý do nullable, thiếu read endpoint. Không tiếp tục kết luận “chưa lưu ai duyệt” như ERD review cũ.
5. **Asset tables:** asset_media/scene_assets có ở thiết kế, không được migrations/app entity hiện tại tạo. Có thể hiện hữu trong shared DB legacy; audit này không truy vấn DB nên không khẳng định physically absent ở production.
6. **Composition model:** một mutable snapshot JSON per script version, không entity per production scene; render_jobs snapshot có reproducibility nhưng chưa lịch sử composition riêng/read API.
7. **Publication uniqueness:** unique project/platform giới hạn một internal publication mỗi project, khác clip/platform trong diagram; cần chốt republish phiên bản mới.
8. **DB/ORM unmapped cột:** feedback clip_id; composition resolution/fps retained cho shared baseline; entity mapping và danh sách DDL chi tiết có trong evidence. Không suy ra extra DB column luôn là lỗi nếu đó là compatibility field.
9. **Indexes:** V1 có workspace/owner/status project; version ordering; scene ordering; feedback project/time, scene; render project/time, external ID; publish project/time; history project/time. V9 token unique bị V17 bỏ. Thêm V11 target/time, V12 requester/time, V18 created/model/requester, V19 composition project và render composition. Không có index chuyên biệt clip-scoped comments/replies vì contract chưa có.

## 9. Use cases, actors, diagram inventory và kiểm chứng

### 9.1. Toàn bộ use case chính (mã audit)

| ID | Use case | Actor chính | API / kết luận |
|---|---|---|---|
| UC-01 | Login via OAuth/JWT | Guest/User | auth login/google/me; register bị khóa theo policy |
| UC-02 | Create Clip Project | Writer | POST workspace scripts |
| UC-03 | Edit Clip Project | Writer | PUT/DELETE scripts; archive quyền cần chốt |
| UC-04 | Write/Edit Script by Scene | Writer | PUT scripts full list |
| UC-05 | Request AI Assistance | Writer | generate/action/approaches |
| UC-06 | View Script History | Writer | versions |
| UC-07 | Submit Script | Writer | submit-review/resubmit |
| UC-08 | Review Script | Reviewer | review-queue/detail |
| UC-09 | Approve Script | Reviewer | approve |
| UC-10 | Reject Script | Reviewer | request-changes; note chưa bắt buộc |
| UC-11 | Add Inline Comments | Reviewer | qa-comments + scene |
| UC-12 | Review Rendered Clip | Reviewer | videoUrl/jobs/qa-report; clip target thiếu |
| UC-13 | Approve Clip | Reviewer | approve-video |
| UC-14 | Add Timestamp Annotations | Reviewer | qa-comments; target project |
| UC-15 | Compare Versions | Reviewer | script diff + render jobs; clip detail thiếu |
| UC-16 | Reject Clip | Reviewer | send-back; note chưa bắt buộc |
| UC-17 | Open Draft Composition | Producer | GET composition |
| UC-18 | Edit Scene Properties | Producer | PUT composition |
| UC-19 | Swap Suggested Assets | Producer | Thiếu asset catalog/upload/slot binding |
| UC-20 | Regenerate/Tune Narration & Captions | Producer | Worker TTS + AI align; orchestration/persistence còn thiếu |
| UC-21 | Submit Render Job | Producer | render start/retry/cancel |
| UC-22 | Monitor Render Progress | Producer | status/history/callback |
| UC-23 | Manage Users & Roles | Admin | users |
| UC-24 | Monitor Render Queue & Storage | Admin | admin metrics/render-jobs; storage partial |
| UC-25 | Accept/Ignore AI Suggestions | Writer | PUT AI decision |
| UC-26 | Read & Resolve Feedback | Writer/Producer | qa-comments; Writer resolve mismatch |
| UC-27 | Manage Templates | Admin | templates |
| UC-28 | View Automated Pre-publish QA | Reviewer | qa-report; sync measurement thiếu |
| UC-29 | Publish Clip to YouTube | Producer | Publish target YOUTUBE unavailable |
| UC-30 | Browse & Export Media Library | Authorized User | library + embed; filter/scope partial |
| UC-31 | Monitor AI Cost & Usage | Admin | overview/ai-usage |
| UC-32 | View Pipeline Board | Authorized User | workspace scripts + derived status |

**UC-33 Logout** và **UC-34 Preview Scene** thực hiện client-side, không cần API riêng. Tổng 34 use case tính cả hai. Authenticated User là actor cha; Guest là actor phụ, bốn role nghiệp vụ Admin/Writer/Reviewer/Producer. Workspace leader là cờ quyền, không phải role mới. “Library” trên UI không phải UserRole trong DB.

### 9.2. Diagram inventory

Phụ lục D liệt kê đủ 21 file, cách đọc và giới hạn. Các PNG ERD_Week2 nhúng cùng mxfile ba trang; không xem metadata của một ảnh như bằng chứng ảnh đó hiển thị cả ba trang. `ERD_Week2.drawio` rời là phiên bản hai trang khác; Phase0 là draft có chú thích ngày 10/10, không dùng làm quyết định đã approve.

`db.png` không có text/XML nhúng và ảnh gốc bị crop: đã đọc trực quan phần có sẵn, phục hồi phần tương ứng bằng DB Diagram/PlantUML/XML. Các file conceptual drawio/backup có thể chứa ảnh nhúng; đã đọc XML và đối chiếu PNG. Không có PDF lỗi đọc. Không cần OCR khi XML/PlantUML có nguồn đủ; không tự điền chữ bị crop từ suy đoán.

### 9.3. Verification log

Kết quả chạy test trong lượt audit được ghi ở mục 10.2. Không dùng test DB/E2E cần tài khoản thật để thay đổi dữ liệu. Các test source được dẫn là bằng chứng mức unit/access; main-flow happy case do người dùng xác nhận là bối cảnh, không thay thế kiểm tra các nhánh return/reject còn lại.


## 10. Swagger/OpenAPI và kết quả verification

### 10.1. Export và đối chiếu

- BE có springdoc-openapi-starter-webmvc-ui, route `/v3/api-docs`. Không có BE runtime local đang nghe port 8080 tại lần kiểm tra; chưa export runtime BE mới. Không tự khởi động Spring với DB thật hoặc chạy migration trong audit.
- Đã xuất bản sao nguyên trạng [BE OpenAPI snapshot](API_AUDIT_BE_OPENAPI_SNAPSHOT_2026-10-17.json) từ `STEMotion_BE/docs/api/openapi.json`. Đây là snapshot lưu trong repo, **không phải spec được tái sinh từ code c7fbc49**.
- Đã gọi `app.openapi()` trực tiếp từ FastAPI code hiện tại, không startup Whisper/provider, lưu [AI OpenAPI export hiện tại](API_AUDIT_AI_OPENAPI_EXPORTED_2026-10-17.json). Tất cả 10 method/path khớp [AI snapshot](API_AUDIT_AI_OPENAPI_SNAPSHOT_2026-10-17.json); chưa khẳng định mọi schema/property byte-for-byte giống nhau.

**Code BE có nhưng BE snapshot thiếu (12):**

| Method | Path |
|---|---|
| GET | `/api/v1/admin/overview` |
| GET | `/api/v1/admin/render-jobs` |
| GET | `/api/v1/admin/ai-usage` |
| POST | `/api/v1/ai/transcribe/align` |
| POST | `/api/v1/ai/internal/transcribe/align` |
| POST | `/api/v1/ai/script/approaches` |
| POST | `/api/v1/auth/google` |
| GET | `/api/v1/auth/google/enabled` |
| POST | `/api/v1/auth/change-password` |
| GET | `/api/v1/scripts/{projectId}/composition` |
| PUT | `/api/v1/scripts/{projectId}/composition` |
| GET | `/api/v1/workspaces/{id}/candidates` |

**Snapshot có nhưng code không có (1):** POST `/api/v1/workspace-invitations/accept`, policy trước V17. `/auth/register` còn ở cả code và snapshot nhưng đã khóa; status 201 trong snapshot/controller không thể dùng làm bằng chứng tài khoản được tạo.

OpenApiConfig hiện chỉ cấu hình info/server, chưa mô tả SecurityScheme/JWT + các shared keys đầy đủ. `@Operation` không đồng nghĩa đã document đủ lỗi 400/401/403/409/502/503 hoặc constraints. Phụ lục A là kết quả đọc code; diff với **live** Swagger cần làm lại khi có môi trường BE không gây mutation DB. Không báo “12 API thiếu trong Swagger runtime” khi chỉ kiểm được snapshot.

### 10.2. Verification thực hiện trong audit

| Kiểm tra | Kết quả | Giới hạn |
|---|---|---|
| Parse toàn bộ 14 controller | 72 operations, đủ method/request/response/role/source | Static scan; interceptor được đọc riêng |
| Compare BE snapshot | 60 common, 12 code-only, 1 snapshot-only | Không có live BE OpenAPI |
| FE doc vs BE | 43 rows, 13 route exact, 30 khác | Đã chuẩn hóa placeholder + bỏ query trong route matching |
| FE source origin/main vs BE | 49 service operations đều match route | Không đảm bảo payload/flow; 23 BE operations chưa thấy service reference |
| FastAPI export | 10 operations; route set khớp snapshot | Export in-process, không inference |
| BE selected tests | **75 passed, 0 failures/errors, 0 skipped** | 15 suites unit/MockMvc; không DB/E2E/provider thật |
| Worker `npm test` | **18 tests: 16 pass, 2 fail** | ffmpeg native exit 3221225477 và server test health không ready; chưa xác định kết quả ngoài sandbox |
| FastAPI pytest thử chạy | **Chưa có kết quả hoàn tất; đã dừng** | Python mặc định thiếu pytest; dùng venv có sẵn vẫn chưa hoàn tất trong lượt thử; không báo pass |
| PDF/diagrams | PDF 5/5 trang + 11 PNG được xem; 21 file Diagram inventory | db.png bị crop sẵn, đã dùng XML/PlantUML/ảnh đầy đủ đối chiếu |
| Flyway | Đọc đủ V1–V19, thống kê 15 entity/tables | Không query schema runtime, không apply migration |

BE wrapper `mvnw.cmd` lỗi “no main manifest attribute”; đã dùng Maven 3.9.9 có sẵn trong `.m2`, chế độ offline. Lượt thử đầu dynamic ByteBuddy agent gặp lỗi named-pipe; rerun bằng **preloaded javaagent** thành công. Không sửa pom, wrapper hay test source.

Các suite BE đã pass: AccountProvisioningAccessTest (7), CompositionAccessTest (4), CompositionServiceTest (9), PublicationFlowTest (5), PublishAvailabilityTest (1), RenderContractTest (1), RenderRetryTest (13), ReviewDecisionEventTest (1), ClipQaServiceTest (11), ScriptDraftAccessTest (5), ProjectArchiveTest (2), ReviewQueueScopeTest (2), SceneIdentityTest (6), SubmittedScriptTest (2), WorkspaceServiceTest (6). Test archive hiện chưa chứng minh leader REVIEWER/PRODUCER đi qua cả interceptor thật; mismatch quyền vẫn để Partial.

Log/evidence tạm: `.audit/api-2026-10-17/` (controller/FE JSON inventories, PDF renders/text, diagram extraction, Maven logs). Bản final có thể review độc lập trong checklist và evidence. **Dừng ở audit; chưa triển khai các đề xuất, chưa commit/push.**

## Phụ lục A. Inventory BE đầy đủ (72 operations)

Nguồn: toàn bộ 14 controller. `@PreAuthorize` giữ nguyên biểu thức; dấu — nghĩa là không có annotation, vẫn có SecurityConfig/interceptor. Admin bypass của workspaceAccess được giải thích ở 1.2. Tên request/response trỏ tới DTO catalog trong evidence.

Status: cột success là nhánh controller/service bình thường; register bị policy chặn. Profile E0 chung: 400 malformed/type/validation, 401 JWT, 403 access/password-change, 404 route/resource, 405 sai method, 409 optimistic conflict nếu mutation tương ứng, 415 media không hỗ trợ, 500 unexpected. Đây là tập lỗi có thể qua framework/service, không phải lời khẳng định mọi operation phát sinh mọi mã; các lỗi nghiệp vụ/upstream đặc thù được ghi riêng.

| # | Method | Path | Request | Response | PreAuthorize | Success | Lỗi đặc thù / Source |
|---|---|---|---|---|---|---|---|
| 1 | GET | `/api/v1/admin/metrics` | `â€”` | `ResponseEntity<SystemMetrics>` | `hasRole('ADMIN')` | 200 | E0; [AdminController.java:27](../STEMotion_BE/src/main/java/com/stemotion/domain/admin/controller/AdminController.java#L27) |
| 2 | GET | `/api/v1/admin/overview` | `â€”` | `ResponseEntity<AdminDashboardOverview>` | `hasRole('ADMIN')` | 200 | E0; [AdminController.java:33](../STEMotion_BE/src/main/java/com/stemotion/domain/admin/controller/AdminController.java#L33) |
| 3 | GET | `/api/v1/admin/render-jobs` | `â€”` | `ResponseEntity<List<AdminRenderJobSummary>>` | `hasRole('ADMIN')` | 200 | E0; [AdminController.java:39](../STEMotion_BE/src/main/java/com/stemotion/domain/admin/controller/AdminController.java#L39) |
| 4 | GET | `/api/v1/admin/ai-usage` | `â€”` | `AiUsageService.UsagePage` | `hasRole('ADMIN')` | 200 | E0; [AiUsageController.java:16](../STEMotion_BE/src/main/java/com/stemotion/domain/admin/controller/AiUsageController.java#L16) |
| 5 | POST | `/api/v1/ai/transcribe/align` | `multipart: file,text,language` | `JsonNode` | `@workspaceAccess.hasRoleAnywhere(authentication.name, 'WRITER', 'PRODUCER')` | 200 | E0 + 502 invalid/upstream,503 config; multipart oversize chưa handler 413 riêng; [AiAlignmentController.java:23](../STEMotion_BE/src/main/java/com/stemotion/domain/ai/controller/AiAlignmentController.java#L23) |
| 6 | POST | `/api/v1/ai/internal/transcribe/align` | `multipart: file,text,language` | `JsonNode` | `â€” (SecurityConfig + interceptor)` | 200 | 400,401,502,503; không cần JWT; [AiAlignmentController.java:31](../STEMotion_BE/src/main/java/com/stemotion/domain/ai/controller/AiAlignmentController.java#L31) |
| 7 | PUT | `/api/v1/ai/requests/{requestId}/decision` | `AiDecisionRequest` | `ResponseEntity<Map<String,Object>>` | `â€” (SecurityConfig + interceptor)` | 200 | E0 + 403 requester,400/404 request decision; [AiController.java:29](../STEMotion_BE/src/main/java/com/stemotion/domain/ai/controller/AiController.java#L29) |
| 8 | GET | `/api/v1/ai/health` | `â€”` | `ResponseEntity<Map<String, Object>>` | `â€” (SecurityConfig + interceptor)` | 200 | E0; [AiController.java:37](../STEMotion_BE/src/main/java/com/stemotion/domain/ai/controller/AiController.java#L37) |
| 9 | POST | `/api/v1/ai/script/generate` | `GenerateScriptRequest` | `ResponseEntity<AiActionResponse>` | `â€” (SecurityConfig + interceptor)` | 200 | E0 + provider 502/503 (xem gateway/client); [AiController.java:43](../STEMotion_BE/src/main/java/com/stemotion/domain/ai/controller/AiController.java#L43) |
| 10 | POST | `/api/v1/ai/script/approaches` | `ApproachRequest` | `ResponseEntity<AiActionResponse>` | `â€” (SecurityConfig + interceptor)` | 200 | E0 + provider 502/503 (xem gateway/client); [AiController.java:49](../STEMotion_BE/src/main/java/com/stemotion/domain/ai/controller/AiController.java#L49) |
| 11 | POST | `/api/v1/ai/script/action` | `ScriptActionRequest` | `ResponseEntity<AiActionResponse>` | `â€” (SecurityConfig + interceptor)` | 200 | E0 + provider 502/503 (xem gateway/client); [AiController.java:56](../STEMotion_BE/src/main/java/com/stemotion/domain/ai/controller/AiController.java#L56) |
| 12 | POST | `/api/v1/auth/register` | `RegisterRequest` | `ResponseEntity<AuthResponse>` | `â€” (SecurityConfig + interceptor)` | — | 403 denyAll (anonymous có thể 401); không có 201 reachable; [AuthController.java:31](../STEMotion_BE/src/main/java/com/stemotion/domain/auth/controller/AuthController.java#L31) |
| 13 | POST | `/api/v1/auth/login` | `LoginRequest` | `ResponseEntity<AuthResponse>` | `â€” (SecurityConfig + interceptor)` | 200 | 400 validation/inactive;401 invalid credentials;500; [AuthController.java:37](../STEMotion_BE/src/main/java/com/stemotion/domain/auth/controller/AuthController.java#L37) |
| 14 | GET | `/api/v1/auth/me` | `â€”` | `ResponseEntity<UserResponse>` | `â€” (SecurityConfig + interceptor)` | 200 | E0; [AuthController.java:43](../STEMotion_BE/src/main/java/com/stemotion/domain/auth/controller/AuthController.java#L43) |
| 15 | POST | `/api/v1/auth/google` | `GoogleLoginRequest` | `ResponseEntity<com.stemotion.domain.auth.dto.AuthDtos.AuthResponse>` | `â€” (SecurityConfig + interceptor)` | 200 | 400 inactive;401 invalid token;403 chưa cấp/mismatch;503 thiếu OAuth config (client); [AuthController.java:53](../STEMotion_BE/src/main/java/com/stemotion/domain/auth/controller/AuthController.java#L53) |
| 16 | GET | `/api/v1/auth/google/enabled` | `â€”` | `ResponseEntity<java.util.Map<String, Boolean>>` | `â€” (SecurityConfig + interceptor)` | 200 | E0; [AuthController.java:61](../STEMotion_BE/src/main/java/com/stemotion/domain/auth/controller/AuthController.java#L61) |
| 17 | POST | `/api/v1/auth/change-password` | `ChangePasswordRequest` | `ResponseEntity<UserResponse>` | `â€” (SecurityConfig + interceptor)` | 200 | E0; [AuthController.java:67](../STEMotion_BE/src/main/java/com/stemotion/domain/auth/controller/AuthController.java#L67) |
| 18 | POST | `/api/v1/users` | `RegisterRequest` | `ResponseEntity<UserResponse>` | `hasRole('ADMIN')` | 201 | E0; [UserController.java:32](../STEMotion_BE/src/main/java/com/stemotion/domain/auth/controller/UserController.java#L32) |
| 19 | GET | `/api/v1/users` | `â€”` | `ResponseEntity<List<UserResponse>>` | `hasRole('ADMIN')` | 200 | E0; [UserController.java:40](../STEMotion_BE/src/main/java/com/stemotion/domain/auth/controller/UserController.java#L40) |
| 20 | GET | `/api/v1/users/{id}` | `â€”` | `ResponseEntity<UserResponse>` | `hasRole('ADMIN') or #id == authentication.name` | 200 | E0; [UserController.java:46](../STEMotion_BE/src/main/java/com/stemotion/domain/auth/controller/UserController.java#L46) |
| 21 | PATCH | `/api/v1/users/{id}` | `UpdateUserRequest` | `ResponseEntity<UserResponse>` | `hasRole('ADMIN')` | 200 | E0; [UserController.java:52](../STEMotion_BE/src/main/java/com/stemotion/domain/auth/controller/UserController.java#L52) |
| 22 | DELETE | `/api/v1/users/{id}` | `â€”` | `ResponseEntity<Void>` | `hasRole('ADMIN')` | 204 | E0; [UserController.java:61](../STEMotion_BE/src/main/java/com/stemotion/domain/auth/controller/UserController.java#L61) |
| 23 | GET | `/api/v1/library/clips` | `â€”` | `ResponseEntity<List<LibraryItem>>` | `â€” (SecurityConfig + interceptor)` | 200 | E0; [LibraryController.java:26](../STEMotion_BE/src/main/java/com/stemotion/domain/library/controller/LibraryController.java#L26) |
| 24 | GET | `/api/v1/library/clips/{projectId}` | `â€”` | `ResponseEntity<LibraryItem>` | `â€” (SecurityConfig + interceptor)` | 200 | E0; [LibraryController.java:35](../STEMotion_BE/src/main/java/com/stemotion/domain/library/controller/LibraryController.java#L35) |
| 25 | GET | `/api/v1/library/filters` | `â€”` | `ResponseEntity<LibraryService.LibraryFilters>` | `â€” (SecurityConfig + interceptor)` | 200 | E0; [LibraryController.java:41](../STEMotion_BE/src/main/java/com/stemotion/domain/library/controller/LibraryController.java#L41) |
| 26 | GET | `/api/v1/scripts/{projectId}/composition` | `â€”` | `ResponseEntity<CompositionResponse>` | `â€” (SecurityConfig + interceptor)` | 200 | E0 + 400 nonapproved/scene invariant,409 stale revision/locked (PUT); [CompositionController.java:30](../STEMotion_BE/src/main/java/com/stemotion/domain/production/controller/CompositionController.java#L30) |
| 27 | PUT | `/api/v1/scripts/{projectId}/composition` | `UpdateCompositionRequest` | `ResponseEntity<CompositionResponse>` | `@workspaceAccess.hasProjectRole(#projectId, authentication.name, 'PRODUCER')` | 200 | E0 + 400 nonapproved/scene invariant,409 stale revision/locked (PUT); [CompositionController.java:36](../STEMotion_BE/src/main/java/com/stemotion/domain/production/controller/CompositionController.java#L36) |
| 28 | GET | `/api/v1/scripts/{projectId}/publications` | `â€”` | `ResponseEntity<List<PublishResponse>>` | `â€” (SecurityConfig + interceptor)` | 200 | E0; [PublishController.java:32](../STEMotion_BE/src/main/java/com/stemotion/domain/production/controller/PublishController.java#L32) |
| 29 | POST | `/api/v1/scripts/{projectId}/publish` | `PublishRequest` | `ResponseEntity<PublishResponse>` | `@workspaceAccess.hasProjectRole(#projectId, authentication.name, 'PRODUCER')` | 201 | E0 + 400 QA/state,409 duplicate/artifact,503 YOUTUBE unavailable; [PublishController.java:37](../STEMotion_BE/src/main/java/com/stemotion/domain/production/controller/PublishController.java#L37) |
| 30 | POST | `/api/v1/render/projects/{projectId}/start` | `StartRenderRequest` | `ResponseEntity<RenderJobResponse>` | `@workspaceAccess.hasProjectRole(#projectId, authentication.name, 'PRODUCER')` | 202 | E0 + 400 state/budget,409 active render,502 worker,503 config; [RenderController.java:35](../STEMotion_BE/src/main/java/com/stemotion/domain/production/controller/RenderController.java#L35) |
| 31 | GET | `/api/v1/render/status/{jobId}` | `â€”` | `ResponseEntity<RenderJobResponse>` | `â€” (SecurityConfig + interceptor)` | 200 | E0; [RenderController.java:50](../STEMotion_BE/src/main/java/com/stemotion/domain/production/controller/RenderController.java#L50) |
| 32 | GET | `/api/v1/render/projects/{projectId}/jobs` | `â€”` | `ResponseEntity<List<RenderJobResponse>>` | `â€” (SecurityConfig + interceptor)` | 200 | E0; [RenderController.java:56](../STEMotion_BE/src/main/java/com/stemotion/domain/production/controller/RenderController.java#L56) |
| 33 | POST | `/api/v1/render/jobs/{jobId}/retry` | `â€”` | `ResponseEntity<RenderJobResponse>` | `@workspaceAccess.hasJobRole(#jobId, authentication.name, 'PRODUCER')` | 200 | E0 + 400 state/budget,409 active render,502 worker,503 config; [RenderController.java:62](../STEMotion_BE/src/main/java/com/stemotion/domain/production/controller/RenderController.java#L62) |
| 34 | DELETE | `/api/v1/render/jobs/{jobId}` | `â€”` | `ResponseEntity<Void>` | `@workspaceAccess.hasJobRole(#jobId, authentication.name, 'PRODUCER')` | 204 | E0; [RenderController.java:71](../STEMotion_BE/src/main/java/com/stemotion/domain/production/controller/RenderController.java#L71) |
| 35 | POST | `/api/v1/render/callback` | `RenderCallbackRequest` | `ResponseEntity<RenderJobResponse>` | `â€” (SecurityConfig + interceptor)` | 200 | 400 completed thiếu URL;401 thiếu/config key;403 key sai;404 job;500 DB; không JWT; [RenderController.java:83](../STEMotion_BE/src/main/java/com/stemotion/domain/production/controller/RenderController.java#L83) |
| 36 | GET | `/api/v1/templates` | `â€”` | `ResponseEntity<List<TemplateResponse>>` | `â€” (SecurityConfig + interceptor)` | 200 | E0; [TemplateController.java:32](../STEMotion_BE/src/main/java/com/stemotion/domain/production/controller/TemplateController.java#L32) |
| 37 | GET | `/api/v1/templates/{id}` | `â€”` | `ResponseEntity<TemplateResponse>` | `â€” (SecurityConfig + interceptor)` | 200 | E0; [TemplateController.java:40](../STEMotion_BE/src/main/java/com/stemotion/domain/production/controller/TemplateController.java#L40) |
| 38 | POST | `/api/v1/templates` | `TemplateRequest` | `ResponseEntity<TemplateResponse>` | `hasRole('ADMIN')` | 201 | E0; [TemplateController.java:45](../STEMotion_BE/src/main/java/com/stemotion/domain/production/controller/TemplateController.java#L45) |
| 39 | PUT | `/api/v1/templates/{id}` | `TemplateRequest` | `ResponseEntity<TemplateResponse>` | `hasRole('ADMIN')` | 200 | E0; [TemplateController.java:51](../STEMotion_BE/src/main/java/com/stemotion/domain/production/controller/TemplateController.java#L51) |
| 40 | DELETE | `/api/v1/templates/{id}` | `â€”` | `ResponseEntity<Void>` | `hasRole('ADMIN')` | 204 | E0; [TemplateController.java:60](../STEMotion_BE/src/main/java/com/stemotion/domain/production/controller/TemplateController.java#L60) |
| 41 | GET | `/api/v1/scripts/{projectId}/qa-comments` | `â€”` | `ResponseEntity<List<FeedbackResponse>>` | `â€” (SecurityConfig + interceptor)` | 200 | E0; [FeedbackController.java:39](../STEMotion_BE/src/main/java/com/stemotion/domain/review/controller/FeedbackController.java#L39) |
| 42 | POST | `/api/v1/scripts/{projectId}/qa-comments` | `FeedbackRequest` | `ResponseEntity<FeedbackResponse>` | `â€” (SecurityConfig + interceptor)` | 201 | E0; [FeedbackController.java:48](../STEMotion_BE/src/main/java/com/stemotion/domain/review/controller/FeedbackController.java#L48) |
| 43 | GET | `/api/v1/scenes/{sceneId}/comments` | `â€”` | `ResponseEntity<List<FeedbackResponse>>` | `â€” (SecurityConfig + interceptor)` | 200 | E0; [FeedbackController.java:59](../STEMotion_BE/src/main/java/com/stemotion/domain/review/controller/FeedbackController.java#L59) |
| 44 | PUT | `/api/v1/qa-comments/{commentId}/resolve` | `â€”` | `ResponseEntity<FeedbackResponse>` | `@workspaceAccess.hasCommentRole(#commentId, authentication.name, 'PRODUCER', 'REVIEWER')` | 200 | E0; [FeedbackController.java:66](../STEMotion_BE/src/main/java/com/stemotion/domain/review/controller/FeedbackController.java#L66) |
| 45 | PUT | `/api/v1/qa-comments/{commentId}/reopen` | `â€”` | `ResponseEntity<FeedbackResponse>` | `@workspaceAccess.hasCommentRole(#commentId, authentication.name, 'REVIEWER')` | 200 | E0; [FeedbackController.java:73](../STEMotion_BE/src/main/java/com/stemotion/domain/review/controller/FeedbackController.java#L73) |
| 46 | DELETE | `/api/v1/qa-comments/{commentId}` | `â€”` | `ResponseEntity<Void>` | `â€” (SecurityConfig + interceptor)` | 204 | E0; [FeedbackController.java:83](../STEMotion_BE/src/main/java/com/stemotion/domain/review/controller/FeedbackController.java#L83) |
| 47 | GET | `/api/v1/scripts/{projectId}/qa-report` | `â€”` | `ResponseEntity<QaReport>` | `â€” (SecurityConfig + interceptor)` | 200 | E0; [FeedbackController.java:89](../STEMotion_BE/src/main/java/com/stemotion/domain/review/controller/FeedbackController.java#L89) |
| 48 | POST | `/api/v1/scripts/{projectId}/approve-video` | `ReviewDecisionRequest` | `ResponseEntity<Void>` | `@workspaceAccess.hasProjectRole(#projectId, authentication.name, 'REVIEWER')` | 204 | E0 + 400 state/open notes,409 current artifact; [FeedbackController.java:95](../STEMotion_BE/src/main/java/com/stemotion/domain/review/controller/FeedbackController.java#L95) |
| 49 | POST | `/api/v1/scripts/{projectId}/send-back` | `ReviewDecisionRequest` | `ResponseEntity<Void>` | `@workspaceAccess.hasProjectRole(#projectId, authentication.name, 'REVIEWER')` | 204 | E0 + 400 state/open notes,409 current artifact; [FeedbackController.java:105](../STEMotion_BE/src/main/java/com/stemotion/domain/review/controller/FeedbackController.java#L105) |
| 50 | GET | `/api/v1/workspaces/{workspaceId}/scripts` | `â€”` | `ResponseEntity<List<ScriptSummaryResponse>>` | `â€” (SecurityConfig + interceptor)` | 200 | E0; [ScriptController.java:40](../STEMotion_BE/src/main/java/com/stemotion/domain/script/controller/ScriptController.java#L40) |
| 51 | POST | `/api/v1/workspaces/{workspaceId}/scripts` | `CreateScriptRequest` | `ResponseEntity<ScriptResponse>` | `@workspaceAccess.hasWorkspaceRole(#workspaceId, authentication.name, 'WRITER')` | 201 | E0; [ScriptController.java:46](../STEMotion_BE/src/main/java/com/stemotion/domain/script/controller/ScriptController.java#L46) |
| 52 | GET | `/api/v1/scripts/mine` | `â€”` | `ResponseEntity<List<ScriptSummaryResponse>>` | `â€” (SecurityConfig + interceptor)` | 200 | E0; [ScriptController.java:58](../STEMotion_BE/src/main/java/com/stemotion/domain/script/controller/ScriptController.java#L58) |
| 53 | GET | `/api/v1/scripts/review-queue` | `â€”` | `ResponseEntity<List<ScriptSummaryResponse>>` | `@workspaceAccess.isReviewerAnywhere(authentication.name)` | 200 | E0; [ScriptController.java:66](../STEMotion_BE/src/main/java/com/stemotion/domain/script/controller/ScriptController.java#L66) |
| 54 | GET | `/api/v1/scripts/{id}` | `â€”` | `ResponseEntity<ScriptResponse>` | `â€” (SecurityConfig + interceptor)` | 200 | E0; [ScriptController.java:73](../STEMotion_BE/src/main/java/com/stemotion/domain/script/controller/ScriptController.java#L73) |
| 55 | PUT | `/api/v1/scripts/{id}` | `UpdateScriptRequest` | `ResponseEntity<ScriptResponse>` | `@workspaceAccess.hasProjectRole(#id, authentication.name, 'WRITER')` | 200 | E0; [ScriptController.java:79](../STEMotion_BE/src/main/java/com/stemotion/domain/script/controller/ScriptController.java#L79) |
| 56 | POST | `/api/v1/scripts/{id}/submit-review` | `â€”` | `ResponseEntity<ScriptResponse>` | `@workspaceAccess.hasProjectRole(#id, authentication.name, 'WRITER')` | 200 | E0; [ScriptController.java:89](../STEMotion_BE/src/main/java/com/stemotion/domain/script/controller/ScriptController.java#L89) |
| 57 | POST | `/api/v1/scripts/{id}/approve` | `ReviewDecisionRequest` | `ResponseEntity<ScriptResponse>` | `@workspaceAccess.hasProjectRole(#id, authentication.name, 'REVIEWER')` | 200 | E0; [ScriptController.java:99](../STEMotion_BE/src/main/java/com/stemotion/domain/script/controller/ScriptController.java#L99) |
| 58 | POST | `/api/v1/scripts/{id}/request-changes` | `ReviewDecisionRequest` | `ResponseEntity<ScriptResponse>` | `@workspaceAccess.hasProjectRole(#id, authentication.name, 'REVIEWER')` | 200 | E0 + 400 state; **không** 400 khi note trống; [ScriptController.java:110](../STEMotion_BE/src/main/java/com/stemotion/domain/script/controller/ScriptController.java#L110) |
| 59 | GET | `/api/v1/scripts/{id}/versions` | `â€”` | `ResponseEntity<List<ScriptVersionResponse>>` | `â€” (SecurityConfig + interceptor)` | 200 | E0; [ScriptController.java:121](../STEMotion_BE/src/main/java/com/stemotion/domain/script/controller/ScriptController.java#L121) |
| 60 | GET | `/api/v1/scripts/{id}/diff` | `â€”` | `ResponseEntity<VersionDiffResponse>` | `â€” (SecurityConfig + interceptor)` | 200 | E0; [ScriptController.java:127](../STEMotion_BE/src/main/java/com/stemotion/domain/script/controller/ScriptController.java#L127) |
| 61 | DELETE | `/api/v1/scripts/{id}` | `â€”` | `ResponseEntity<Void>` | `@workspaceAccess.canArchiveProject(#id, authentication.name)` | 204 | E0; [ScriptController.java:137](../STEMotion_BE/src/main/java/com/stemotion/domain/script/controller/ScriptController.java#L137) |
| 62 | GET | `/api/v1/workspaces` | `â€”` | `ResponseEntity<List<WorkspaceResponse>>` | `â€” (SecurityConfig + interceptor)` | 200 | E0; [WorkspaceController.java:39](../STEMotion_BE/src/main/java/com/stemotion/domain/workspace/controller/WorkspaceController.java#L39) |
| 63 | GET | `/api/v1/workspaces/mine` | `â€”` | `ResponseEntity<List<WorkspaceResponse>>` | `â€” (SecurityConfig + interceptor)` | 200 | E0; [WorkspaceController.java:44](../STEMotion_BE/src/main/java/com/stemotion/domain/workspace/controller/WorkspaceController.java#L44) |
| 64 | GET | `/api/v1/workspaces/{id}` | `â€”` | `ResponseEntity<WorkspaceResponse>` | `â€” (SecurityConfig + interceptor)` | 200 | E0; [WorkspaceController.java:50](../STEMotion_BE/src/main/java/com/stemotion/domain/workspace/controller/WorkspaceController.java#L50) |
| 65 | POST | `/api/v1/workspaces` | `WorkspaceRequest` | `ResponseEntity<WorkspaceResponse>` | `hasRole('ADMIN')` | 201 | E0; [WorkspaceController.java:55](../STEMotion_BE/src/main/java/com/stemotion/domain/workspace/controller/WorkspaceController.java#L55) |
| 66 | PUT | `/api/v1/workspaces/{id}` | `WorkspaceRequest` | `ResponseEntity<WorkspaceResponse>` | `hasRole('ADMIN')` | 200 | E0; [WorkspaceController.java:66](../STEMotion_BE/src/main/java/com/stemotion/domain/workspace/controller/WorkspaceController.java#L66) |
| 67 | DELETE | `/api/v1/workspaces/{id}` | `â€”` | `ResponseEntity<Void>` | `hasRole('ADMIN')` | 204 | E0; [WorkspaceController.java:75](../STEMotion_BE/src/main/java/com/stemotion/domain/workspace/controller/WorkspaceController.java#L75) |
| 68 | GET | `/api/v1/workspaces/{id}/members` | `â€”` | `ResponseEntity<List<MemberResponse>>` | `â€” (SecurityConfig + interceptor)` | 200 | E0; [WorkspaceController.java:82](../STEMotion_BE/src/main/java/com/stemotion/domain/workspace/controller/WorkspaceController.java#L82) |
| 69 | GET | `/api/v1/workspaces/{id}/candidates` | `â€”` | `ResponseEntity<List<CandidateUserResponse>>` | `â€” (SecurityConfig + interceptor)` | 200 | E0; [WorkspaceController.java:87](../STEMotion_BE/src/main/java/com/stemotion/domain/workspace/controller/WorkspaceController.java#L87) |
| 70 | POST | `/api/v1/workspaces/{id}/members` | `AddMemberRequest` | `ResponseEntity<MemberResponse>` | `â€” (SecurityConfig + interceptor)` | 201 | E0; [WorkspaceController.java:93](../STEMotion_BE/src/main/java/com/stemotion/domain/workspace/controller/WorkspaceController.java#L93) |
| 71 | PATCH | `/api/v1/workspaces/{id}/members/{memberId}` | `UpdateMemberRequest` | `ResponseEntity<MemberResponse>` | `â€” (SecurityConfig + interceptor)` | 200 | E0; [WorkspaceController.java:102](../STEMotion_BE/src/main/java/com/stemotion/domain/workspace/controller/WorkspaceController.java#L102) |
| 72 | DELETE | `/api/v1/workspaces/{id}/members/{memberId}` | `â€”` | `ResponseEntity<Void>` | `â€” (SecurityConfig + interceptor)` | 204 | E0; [WorkspaceController.java:112](../STEMotion_BE/src/main/java/com/stemotion/domain/workspace/controller/WorkspaceController.java#L112) |

## Phụ lục B. 43 endpoint trong FE document

Chuẩn hóa prefix /api/v1, bỏ query khi match route và chuẩn hóa tên placeholder; không bỏ khác biệt method. “Khác” cần đọc mapping mục 3.2, không tự động là Missing.

| Method | FE document path | Route BE trùng method/path? |
|---|---|---|
| POST | `/api/v1/auth/login` | Có |
| POST | `/api/v1/auth/google` | Có |
| GET | `/api/v1/auth/me` | Có |
| POST | `/api/v1/auth/change-password` | Có |
| GET | `/api/v1/workspaces/mine` | Có |
| GET | `/api/v1/workspaces` | Có |
| GET | `/api/v1/workspaces/{id}` | Có |
| POST | `/api/v1/workspaces` | Có |
| GET | `/api/v1/workspaces/{id}/members` | Có |
| GET | `/api/v1/workspaces/{id}/candidates` | Có |
| POST | `/api/v1/workspaces/{id}/members` | Có |
| PATCH | `/api/v1/workspaces/{id}/members/{mId}` | Có |
| DELETE | `/api/v1/workspaces/{id}/members/{mId}` | Có |
| GET | `/api/v1/projects?workspaceId={id}` | Khác / xem 3.2 |
| GET | `/api/v1/projects/{id}` | Khác / xem 3.2 |
| POST | `/api/v1/projects` | Khác / xem 3.2 |
| POST | `/api/v1/projects/{id}/archive` | Khác / xem 3.2 |
| GET | `/api/v1/projects/{id}/versions` | Khác / xem 3.2 |
| GET | `/api/v1/projects/{id}/scripts/current` | Khác / xem 3.2 |
| PUT | `/api/v1/projects/{id}/scripts/scenes/{sceneId}` | Khác / xem 3.2 |
| DELETE | `/api/v1/projects/{id}/scripts/scenes/{sceneId}` | Khác / xem 3.2 |
| POST | `/api/v1/projects/{id}/scripts/reorder` | Khác / xem 3.2 |
| POST | `/api/v1/ai/script/segment` | Khác / xem 3.2 |
| POST | `/api/v1/ai/requests/{id}/decision` | Khác / xem 3.2 |
| POST | `/api/v1/ai/script/pedagogical-review` | Khác / xem 3.2 |
| GET | `/api/v1/projects/{id}/comments` | Khác / xem 3.2 |
| POST | `/api/v1/projects/{id}/comments` | Khác / xem 3.2 |
| POST | `/api/v1/projects/{id}/review/decision` | Khác / xem 3.2 |
| GET | `/api/v1/projects/{id}/composition` | Khác / xem 3.2 |
| PUT | `/api/v1/projects/{id}/composition` | Khác / xem 3.2 |
| POST | `/api/v1/render/jobs` | Khác / xem 3.2 |
| GET | `/api/v1/render/jobs/{id}/status` | Khác / xem 3.2 |
| POST | `/api/v1/render/jobs/{id}/cancel` | Khác / xem 3.2 |
| POST | `/api/v1/publications/youtube` | Khác / xem 3.2 |
| GET | `/api/v1/publications/{id}/embed` | Khác / xem 3.2 |
| GET | `/api/v1/admin/metrics/overview` | Khác / xem 3.2 |
| GET | `/api/v1/admin/users` | Khác / xem 3.2 |
| POST | `/api/v1/admin/users/provision` | Khác / xem 3.2 |
| PATCH | `/api/v1/admin/users/{id}` | Khác / xem 3.2 |
| DELETE | `/api/v1/admin/users/{id}` | Khác / xem 3.2 |
| GET | `/api/v1/admin/templates` | Khác / xem 3.2 |
| PATCH | `/api/v1/admin/templates/{id}` | Khác / xem 3.2 |
| POST | `/api/v1/admin/templates` | Khác / xem 3.2 |

## Phụ lục C. BE chưa có tham chiếu trong service FE origin/main

Có 23 operations. Đây là service-reference inventory; không khẳng định không thể gọi gián tiếp từ component.

| Method | Path | Mục đích / xử lý |
|---|---|---|
| GET | `/api/v1/admin/ai-usage` | Nối UI nếu UC cần |
| POST | `/api/v1/ai/transcribe/align` | Nối UI nếu UC cần |
| POST | `/api/v1/ai/internal/transcribe/align` | Service-to-service; không nối FE |
| GET | `/api/v1/ai/health` | Config/health; không phải UC thiếu |
| POST | `/api/v1/auth/register` | Cố ý khóa; không nối FE |
| GET | `/api/v1/auth/google/enabled` | Config/health; không phải UC thiếu |
| GET | `/api/v1/users/{id}` | Nối UI nếu UC cần |
| GET | `/api/v1/library/clips` | Nối UI nếu UC cần |
| GET | `/api/v1/library/clips/{projectId}` | Nối UI nếu UC cần |
| GET | `/api/v1/library/filters` | Nối UI nếu UC cần |
| GET | `/api/v1/scripts/{projectId}/publications` | Nối UI nếu UC cần |
| POST | `/api/v1/render/jobs/{jobId}/retry` | Nối UI nếu UC cần |
| POST | `/api/v1/render/callback` | Service-to-service; không nối FE |
| GET | `/api/v1/templates/{id}` | Nối UI nếu UC cần |
| GET | `/api/v1/scenes/{sceneId}/comments` | Nối UI nếu UC cần |
| PUT | `/api/v1/qa-comments/{commentId}/reopen` | Nối UI nếu UC cần |
| DELETE | `/api/v1/qa-comments/{commentId}` | Nối UI nếu UC cần |
| GET | `/api/v1/scripts/{projectId}/qa-report` | Nối UI nếu UC cần |
| GET | `/api/v1/scripts/mine` | Nối UI nếu UC cần |
| GET | `/api/v1/scripts/review-queue` | Nối UI nếu UC cần |
| GET | `/api/v1/workspaces/{id}` | Admin/chi tiết workspace; tùy UI cần |
| PUT | `/api/v1/workspaces/{id}` | Admin/chi tiết workspace; tùy UI cần |
| DELETE | `/api/v1/workspaces/{id}` | Admin/chi tiết workspace; tùy UI cần |

## Phụ lục D. Inventory tất cả file Diagram/

| File | Đã đọc bằng | Ghi chú |
|---|---|---|
| `Diagram\.$Conceptual ERD.drawio.bkp` | XML/text | Backup lịch sử; không ưu tiên hơn diagram chính |
| `Diagram\.$ERD_Week2.drawio.bkp` | XML/text | Backup lịch sử; không ưu tiên hơn diagram chính |
| `Diagram\.$UC Diagram.drawio.bkp` | XML/text | Backup lịch sử; không ưu tiên hơn diagram chính |
| `Diagram\Conceptual ERD.drawio` | XML/text | Đã đọc |
| `Diagram\Conceptual ERD.png` | Xem ảnh + embedded XML | Đã đọc |
| `Diagram\Conceptual.drawio.png` | Xem ảnh + embedded XML | Đã đọc |
| `Diagram\DB Diagram.drawio.png` | Xem ảnh + embedded XML | Đã đọc |
| `Diagram\db.png` | Xem ảnh trực quan | Ảnh gốc bị crop, không embedded text; đối chiếu DB Diagram/XML, không suy chữ ngoài ảnh |
| `Diagram\ERD_Week2-Conceptual ERD chính.drawio.png` | Xem ảnh + embedded XML | Đã đọc |
| `Diagram\ERD_Week2-DB Diagram.drawio.png` | Xem ảnh + embedded XML | Đã đọc |
| `Diagram\ERD_Week2-Usecase Diagram chính.drawio.png` | Xem ảnh + embedded XML | Đã đọc |
| `Diagram\ERD_Week2.drawio` | XML/text | Đã đọc |
| `Diagram\UC Diagram.drawio` | XML/text | Đã đọc |
| `Diagram\Usecase Diagram.png` | Xem ảnh + embedded XML | Đã đọc |
| `Diagram\Phase0\build_phase0.py` | XML/text | Draft/nguồn sinh/validation lịch sử ngày 10/10; chưa chứng minh app hiện tại |
| `Diagram\Phase0\Domain-review.png` | Xem ảnh trực quan | Draft/nguồn sinh/validation lịch sử ngày 10/10; chưa chứng minh app hiện tại |
| `Diagram\Phase0\ERD-review.png` | Xem ảnh trực quan | Draft/nguồn sinh/validation lịch sử ngày 10/10; chưa chứng minh app hiện tại |
| `Diagram\Phase0\PHASE0_MAPPING_AND_DECISIONS.md` | XML/text | Draft/nguồn sinh/validation lịch sử ngày 10/10; chưa chứng minh app hiện tại |
| `Diagram\Phase0\STEMotion_Design_Phase0_DRAFT.drawio` | XML/text | Draft/nguồn sinh/validation lịch sử ngày 10/10; chưa chứng minh app hiện tại |
| `Diagram\Phase0\Usecase-review.png` | Xem ảnh trực quan | Draft/nguồn sinh/validation lịch sử ngày 10/10; chưa chứng minh app hiện tại |
| `Diagram\Phase0\VALIDATION.json` | XML/text | Draft/nguồn sinh/validation lịch sử ngày 10/10; chưa chứng minh app hiện tại |

## Phụ lục E. 49 operations service FE origin/main

Đọc ref local 5d3b438, không đổi checkout FE. Mỗi dòng có source service; toàn bộ match route BE sau chuẩn hóa placeholders.

| Method | Path (bỏ prefix /api/v1) | Service |
|---|---|---|
| POST | `/users` | `adminService.ts` |
| GET | `/users` | `adminService.ts` |
| PATCH | `/users/${id}` | `adminService.ts` |
| DELETE | `/users/${id}` | `adminService.ts` |
| GET | `/templates?activeOnly=${activeOnly}` | `adminService.ts` |
| POST | `/templates` | `adminService.ts` |
| PUT | `/templates/${id}` | `adminService.ts` |
| DELETE | `/templates/${id}` | `adminService.ts` |
| GET | `/admin/metrics` | `adminService.ts` |
| GET | `/admin/overview` | `adminService.ts` |
| GET | `/admin/render-jobs` | `adminService.ts` |
| POST | `/auth/login` | `authService.ts` |
| POST | `/auth/google` | `authService.ts` |
| GET | `/auth/me` | `authService.ts` |
| POST | `/auth/change-password` | `authService.ts` |
| GET | `/scripts/${projectId}/composition` | `compositionService.ts` |
| PUT | `/scripts/${projectId}/composition` | `compositionService.ts` |
| GET | `/workspaces/${workspaceId}/scripts` | `projectService.ts` |
| GET | `/scripts/${id}` | `projectService.ts` |
| POST | `/workspaces/${workspaceId}/scripts` | `projectService.ts` |
| PUT | `/scripts/${scriptId}` | `sceneSaveQueue.ts` |
| DELETE | `/scripts/${id}` | `projectService.ts` |
| POST | `/scripts/${id}/submit-review` | `projectService.ts` |
| GET | `/scripts/${id}/versions` | `projectService.ts` |
| GET | `/scripts/${id}/diff?left=${left}&right=${right}` | `projectService.ts` |
| POST | `/scripts/${projectId}/publish` | `publicationService.ts` |
| GET | `/render/projects/${projectId}/jobs` | `renderService.ts` |
| POST | `/render/projects/${projectId}/start` | `renderService.ts` |
| GET | `/render/status/${renderId}` | `renderService.ts` |
| DELETE | `/render/jobs/${renderId}` | `renderService.ts` |
| GET | `/scripts/${projectId}/qa-comments` | `reviewService.ts` |
| POST | `/scripts/${projectId}/approve` | `reviewService.ts` |
| POST | `/scripts/${projectId}/request-changes` | `reviewService.ts` |
| POST | `/scripts/${projectId}/approve-video` | `reviewService.ts` |
| POST | `/scripts/${projectId}/send-back` | `reviewService.ts` |
| POST | `/scripts/${projectId}/qa-comments` | `reviewService.ts` |
| PUT | `/qa-comments/${commentId}/resolve` | `reviewService.ts` |
| POST | `/ai/script/action` | `scriptService.ts` |
| PUT | `/ai/requests/${encodeURIComponent(requestId)}/decision` | `scriptService.ts` |
| POST | `/ai/script/approaches` | `scriptService.ts` |
| POST | `/ai/script/generate` | `scriptService.ts` |
| GET | `/workspaces/mine` | `workspaceService.ts` |
| GET | `/workspaces` | `workspaceService.ts` |
| POST | `/workspaces` | `workspaceService.ts` |
| GET | `/workspaces/${workspaceId}/members` | `workspaceService.ts` |
| GET | `/workspaces/${workspaceId}/candidates` | `workspaceService.ts` |
| POST | `/workspaces/${workspaceId}/members` | `workspaceService.ts` |
| PATCH | `/workspaces/${workspaceId}/members/${memberId}` | `workspaceService.ts` |
| DELETE | `/workspaces/${workspaceId}/members/${memberId}` | `workspaceService.ts` |
