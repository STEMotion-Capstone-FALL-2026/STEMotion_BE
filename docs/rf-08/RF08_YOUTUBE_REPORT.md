# Báo cáo Triển khai RF-08: Đăng video lên YouTube (OAuth kênh, Resumable Upload, Auto Metadata)

Ngày: 2026-11-12
Nhánh thực hiện: `feat/rf08-youtube-publish`
Trạng thái: Hoàn thành, 10/10 test tự động PASS, diễn tập schema UUID PASS.

---

## 1. Kiểm kê hệ thống (Bước A)

1. **Luồng `PublishService.publish` & `Publication`:**
   - File: `src/main/java/com/stemotion/domain/production/service/PublishService.java:57`
   - Trước đây chặn `PublishTarget.YOUTUBE` với lỗi 503 `PUBLISH_TARGET_UNAVAILABLE`.
   - Bảng `publications` (tạo từ `V13__publication_transition.sql`) lưu trữ các bản ghi xuất bản cho `INTERNAL_LIBRARY` và `YOUTUBE`, có ràng buộc duy nhất `UNIQUE(project_id, platform)`.
2. **Nơi lấy file video của clip:**
   - File: `src/main/java/com/stemotion/domain/production/service/ClipArtifactService.java:47` (`requireCurrent`).
   - `Clip` liên kết với `RenderJob` và chứa `videoUrl` (`clip.getVideoUrl()`). Hỗ trợ cả URL đám mây (`https://...`) và file cục bộ (`file:...`).
3. **Cơ chế chạy nền:**
   - Sử dụng `ExecutorService` chuyên dụng trong `YoutubeUploadService` kết hợp với `TransactionSynchronizationManager.registerSynchronization` để kích hoạt worker bất đồng bộ ngay sau khi giao dịch commit, trả về ngay HTTP 201 cho client với trạng thái `PENDING`.
4. **Phân quyền workspace:**
   - `WorkspaceAccess`: Kết nối kênh, ngắt kết nối và publish lên YouTube yêu cầu vai trò `PRODUCER` trong workspace hoặc `ADMIN` toàn hệ thống.
5. **Dữ liệu có sẵn cho metadata:**
   - Tiêu đề dự án (`project.getTitle()`), môn học (`project.getSubject()`), khối lớp (`project.getGradeLevel()`), mục tiêu và nội dung STEM brief, mã nhúng LMS (`VideoEmbed.build`), kèm dòng cảnh báo bắt buộc: `"Nội dung minh hoạ, cần giáo viên kiểm tra"`.

---

## 2. Thiết kế và Triển khai (Bước B & Bước C)

1. **Bảo mật và Mã hoá:**
   - `TokenEncryptionService`: Mã hóa refresh token bằng **AES-256-GCM** (tag 128-bit, IV ngẫu nhiên 12-byte) trước khi lưu DB.
   - `YoutubeStateService`: State token trong luồng OAuth được ký số bằng **HMAC-SHA256**, có thời hạn hiệu lực 10 phút, gắn chặt với `workspaceId` và `userId` người khởi tạo.
   - Tuyệt đối không log, không trả về API refresh token hay access token.
2. **Migration mới `V27__youtube_channel_connections.sql`:**
   - Tạo bảng `youtube_channel_connections`: `id`, `workspace_id`, `channel_id`, `channel_title`, `refresh_token_ciphertext`, `scopes`, `connected_by`, `connected_at`, `revoked_at`.
   - Thêm các cột theo dõi upload vào bảng `publications`: `external_id`, `external_url`, `upload_status`, `upload_error`, `attempts`.
   - Không tạo foreign key tới các bảng cũ để đảm bảo tương thích tuyệt đối giữa schema VARCHAR(36) (dev/CI) và schema UUID (production).
   - **Lưu ý Production:** Cần áp dụng file migration `V27` thủ công lên production cùng đợt với gói bridge `V23`–`V26`.
3. **Worker Upload Resumable:**
   - Tự động làm mới `access_token` từ `refresh_token` đã giải mã.
   - Giao thức Resumable Upload 2 bước của Google (`POST` khởi tạo phiên -> nhận header `Location` -> `PUT` truyền stream dữ liệu video).
   - Xử lý lỗi:
     - Gặp `quotaExceeded`, `uploadLimitExceeded`, 401: Đánh dấu `FAILED`, không retry.
     - Gặp lỗi mạng hoặc 5xx: Tự động thử lại tối đa 3 lần với giãn cách tăng dần (exponential backoff).
   - Khi upload thành công: Cập nhật `upload_status='UPLOADED'`, `external_id`, `external_url` (`https://www.youtube.com/watch?v=...`).
   - Hỗ trợ republish khi trạng thái trước đó là `FAILED`: Tái sử dụng bản ghi `Publication` hiện có, không vi phạm ràng buộc unique.

---

## 3. Hợp đồng API cho Frontend (FE Contract)

### 3.1. Kết nối kênh YouTube
- `POST /api/v1/workspaces/{workspaceId}/youtube/connect`
  - Quyền: `PRODUCER` hoặc `ADMIN`.
  - Phản hồi: `200 OK`
    ```json
    {
      "authorizationUrl": "https://accounts.google.com/o/oauth2/v2/auth?client_id=...&state=..."
    }
    ```
- `GET /api/v1/youtube/oauth/callback?code=...&state=...`
  - Xử lý callback từ Google, chuyển hướng trình duyệt (302 Found) về trang cài đặt workspace (`app.youtube.return-url`).

### 3.2. Xem trạng thái kết nối kênh
- `GET /api/v1/workspaces/{workspaceId}/youtube`
  - Quyền: Thành viên workspace.
  - Phản hồi: `200 OK`
    ```json
    {
      "connected": true,
      "channelId": "UC_xxxxxxxxxxxx",
      "channelTitle": "Kênh Khoa Học STEMotion",
      "connectedBy": "00000000-0000-0000-0000-000000000010",
      "connectedAt": "2026-11-12T23:30:00"
    }
    ```

### 3.3. Thu hồi kết nối kênh
- `DELETE /api/v1/workspaces/{workspaceId}/youtube`
  - Quyền: `PRODUCER` hoặc `ADMIN`.
  - Phản hồi: `204 No Content`

### 3.4. Xuất bản lên YouTube
- `POST /api/v1/scripts/{projectId}/publish`
  - Request:
    ```json
    {
      "targets": ["YOUTUBE"],
      "title": "Bài học STEM: Định luật Newton",
      "description": "Video mô phỏng vật lí lớp 10",
      "tags": ["STEM", "VatLi", "Lop10"],
      "privacyStatus": "private",
      "selfDeclaredMadeForKids": false
    }
    ```
  - Phản hồi: `201 Created`
    ```json
    {
      "id": "pub-uuid",
      "projectId": "proj-uuid",
      "youtubeUrl": null,
      "uploadStatus": "PENDING",
      "uploadError": null,
      "externalId": null,
      "publishedAt": "2026-11-12T23:30:00"
    }
    ```
  - Mã lỗi đặc thù:
    - `503 PUBLISH_TARGET_UNAVAILABLE`: Khi tính năng YouTube chưa được bật (`app.youtube.enabled=false`).
    - `409 YOUTUBE_NOT_CONNECTED`: Workspace chưa kết nối kênh YouTube nào.
    - `409 CONFLICT`: Dự án đã có bản ghi xuất bản YouTube đang xử lý hoặc thành công.
    - `409 STEM_FINGERPRINT_MISMATCH`: Chặn bởi gate STRICT của STEM nếu kịch bản chưa duyệt đúng fingerprint.

### 3.5. Lịch sử xuất bản
- `GET /api/v1/scripts/{projectId}/publications`
  - Trả về danh sách ấn bản kèm trạng thái `uploadStatus` (`PENDING`, `UPLOADING`, `UPLOADED`, `FAILED`), `uploadError`, `youtubeUrl`, và `externalId`.

---

## 4. Checklist Cấu hình Google Cloud (Dành cho Chủ Repo / DevOps)

1. **Tạo Google Cloud Project & Bật API:**
   - Truy cập [Google Cloud Console](https://console.cloud.google.com/).
   - Bật API: **YouTube Data API v3**.
2. **Cấu hình OAuth Consent Screen:**
   - User Type: External.
   - Thêm Scope: `https://www.googleapis.com/auth/youtube.upload` và `https://www.googleapis.com/auth/youtube.readonly`.
   - Khi ở chế độ **Testing**: Thêm các email tài khoản Google sẽ tham gia test kênh vào mục **Test users**.
   - *Lưu ý:* Video upload qua dự án ở chế độ Testing sẽ tự động bị Google khoá ở chế độ `private`.
3. **Tạo thông tin xác thực OAuth Client ID:**
   - Loại ứng dụng: **Web application**.
   - Thêm **Authorized redirect URI**: `https://<domain-backend>/api/v1/youtube/oauth/callback`.
4. **Thiết lập biến môi trường trên Host / Cloud Run:**
   - `APP_YOUTUBE_ENABLED=true`
   - `YOUTUBE_CLIENT_ID=<Client ID từ Google>`
   - `YOUTUBE_CLIENT_SECRET=<Client Secret từ Google>`
   - `YOUTUBE_TOKEN_KEY=<Chuỗi ngẫu nhiên bí mật 256-bit dùng mã hoá AES-GCM>`
   - `YOUTUBE_REDIRECT_URI=https://<domain-backend>/api/v1/youtube/oauth/callback`
   - `YOUTUBE_RETURN_URL=https://<domain-frontend>/workspaces/%s/settings`
5. **Giới hạn Quota:**
   - Hạn mức mặc định của Google là 10.000 đơn vị/ngày.
   - Mỗi lệnh `videos.insert` tốn 1.600 đơn vị (tối đa ~6 video/ngày khi chưa xin cấp thêm quota).

---

## 5. Kết quả Kiểm thử Tự động

Đã bổ sung bộ kiểm thử `YoutubePublishAcceptanceTest` (10/10 PASS):
1. `RF08_DISABLED_KEEPS_REJECTION`: PASS
2. `RF08_CONNECT_STATE_SIGNED`: PASS
3. `RF08_STATUS_NEVER_RETURNS_TOKEN`: PASS
4. `RF08_PUBLISH_NOT_CONNECTED_409`: PASS
5. `RF08_UPLOAD_HAPPY_PATH`: PASS
6. `RF08_RETRY_5XX_THEN_SUCCESS`: PASS
7. `RF08_QUOTA_NO_RETRY`: PASS
8. `RF08_REVOKED_401_FAILED`: PASS
9. `RF08_STEM_FINGERPRINT_GATE_STILL_APPLIES`: PASS
10. `RF08_REPUBLISH_AFTER_FAILED_REUSES_ROW`: PASS

Kiểm thử diễn tập schema UUID (`rehearse.sh`):
- `[PASS] V27 schema elements exist (table: 1, column: 1)`
- `Rehearsal Result: 6 PASS, 0 FAIL`

---

## 6. Blocker

- **Không có blocker.**
