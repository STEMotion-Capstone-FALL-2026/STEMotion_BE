# Báo cáo RF-11 — STEM Strict Mode, Rubric, Fingerprint, Template 6 bước (BE)

- **Mã yêu cầu:** RF-11 (BM04, hạn 24/11/2026)
- **Kỹ sư phụ trách:** Nguyễn Việt Hoàng
- **Nhánh thực hiện:** `feat/stem-rf11-strict-20261112` (phát triển từ `integrate/stem-rf10-20261112`, HEAD `cb53e4c`)
- **Trạng thái:** Hoàn tất BE, toàn bộ kiểm tra tự động và acceptance tests đạt 100%.

---

## 1. Kiểm kê trước khi code (Bước A)

### (a) Các vị trí đọc `mode` / `effectiveMode`
- `src/main/java/com/stemotion/domain/stem/StemService.java:88`: Đọc `mode.mode()` trong `view(version)` để xác định effective mode của workspace.
- `src/main/java/com/stemotion/domain/stem/StemService.java:105`: Validate giá trị mode khi lưu settings (`OFF`, `ADVISORY`, `STRICT`).
- `src/main/java/com/stemotion/domain/stem/StemService.java:111`: Kiểm tra xung đột legacy mode khi lưu settings.
- `src/main/java/com/stemotion/domain/stem/StemService.java:128`: Đọc mode trong `submission()` (chặn submit nếu STRICT thiếu brief).
- `src/main/java/com/stemotion/domain/stem/StemService.java:142`: Đọc mode trong `prepare()` (bắt buộc rubric assessment & matching fingerprint khi STRICT).
- `src/main/java/com/stemotion/domain/stem/StemService.java:296`: Đọc mode trong `checkRenderFingerprint()` (bỏ qua nếu OFF, audit nếu ADVISORY, chặn 409 nếu STRICT).
- `src/main/java/com/stemotion/domain/stem/StemService.java:319`: Đọc mode trong `checkSubmitVideoReview()` (kiểm tra snapshot composition).
- `src/main/java/com/stemotion/domain/stem/StemService.java:333`: Đọc mode trong `checkVideoReviewFingerprint()` (kiểm tra snapshot composition lúc approve/send back).
- `src/main/java/com/stemotion/domain/stem/StemService.java:346`: Đọc mode trong `checkPublishFingerprint()` (kiểm tra fingerprint composition/clip lúc publish).
- `src/main/java/com/stemotion/domain/stem/StemValidator.java:105`: Phương thức `transition(mode, findings, assessmentCurrent)` chặn chuyển trạng thái khi mode là `STRICT` và có finding severity `ERROR`.

### (b) Các vị trí gọi `StemValidator.transition` và `StemService.submission`
- `StemValidator.transition`:
  - `StemService.java:132`: Gọi khi brief bị null ở mode STRICT (`TECH-BRIEF`).
  - `StemService.java:136`: Gọi trong `submission()` với effective mode và toàn bộ findings của brief.
- `StemService.submission`:
  - `src/main/java/com/stemotion/domain/script/service/ScriptService.java:293`: Gọi trong `submitForReview(projectId, currentUserId)`.
  - `src/main/java/com/stemotion/domain/review/service/FeedbackService.java:241`: Gọi trong `submitForVideoReview(projectId, producerId)`.

### (c) Chỗ sử dụng `StemFingerprint`
- `src/main/java/com/stemotion/domain/stem/StemFingerprint.java`: POJO chuẩn hoá JSON canonical và băm SHA-256 (`project`, `hash`, `normalizeJson`).
- `src/main/java/com/stemotion/domain/stem/StemService.java:234`: `computeScriptFingerprint()` tính hash ngữ nghĩa của script (scenes + brief + metadata).
- `src/main/java/com/stemotion/domain/stem/StemService.java:260`: `computeCompositionFingerprint()` tính hash presentation contract của composition.
- `src/main/java/com/stemotion/domain/stem/StemArtifactService.java:33`: Khởi tạo và sử dụng `StemFingerprint` cho lineage artifacts.

### (d) Bảng/record rubric và assessment đã có
- DB schema `stem_assessments` (`src/main/resources/db/bridge-prod/V24__create_stem_domain.sql:65-80`):
  Các cột: `id`, `version_id`, `brief_id`, `decision_id`, `actor_id`, `phase` (`SCRIPT`/`VIDEO`), `brief_revision`, `script_revision`, `mode_revision`, `composition_revision`, `payload`.
- DB table `stem_catalog`: Hỗ trợ kind `RUBRIC` và `POLICY`.
- DTO records (`StemDtos.java`):
  - `Criterion(key, judgment, rationale, evidenceIds)`.
  - `Assessment(expectedBriefRevision, expectedScriptRevision, expectedCompositionRevision, expectedModeRevision, rubricVersionId, criteria, idempotencyKey, expectedFingerprint)`.

### (e) Các điểm workflow
- `ScriptService.java:293`: `ScriptService.submitForReview` (gọi `stem.submission`).
- `ScriptService.java:313, 333`: `ScriptService.approve` (gọi `stem.prepare` và `stem.finish` cùng transaction).
- `FeedbackService.java:241, 242`: `FeedbackService.submitForVideoReview` (gọi `stem.submission` và `stem.checkSubmitVideoReview`).
- `FeedbackService.java:266, 267, 293, 300`: `FeedbackService.approveVideo`:
  - Cả 2 nhánh (đã có clip và chưa có clip gọi `renderApproved`) đều qua `stem.prepare`, `stem.checkVideoReviewFingerprint` và `stem.finish`.
- `FeedbackService.java:312`: `FeedbackService.sendBackToProduction` (gọi `stem.checkVideoReviewFingerprint`).
- `RenderService.java:78`: `RenderService.startFrozen` (gọi `stemService.checkRenderFingerprint`).
- `PublishService.java:86`: `PublishService.publish` (gọi `stem.checkPublishFingerprint`).

---

## 2. Quyết định thiết kế

1. **STRICT Mode Isolation & Idempotency:**
   - Không tự động bật STRICT ở bất kỳ đâu. Mode mặc định của workspace vẫn là `OFF`.
   - `StemService.saveSettings` chỉ cho phép Admin thiết lập mode `STRICT`, có kiểm tra `baseRevision`, ghi audit `MODE_ASSIGNED`, và hỗ trợ chuyển đổi linh hoạt `STRICT -> OFF` mà không làm mất hay xóa dữ liệu brief/assessment đã có.
2. **Template bài học STEM 6 bước:**
   - Định nghĩa 6 step keys chuẩn: `OPENING_PROBLEM` (1), `BACKGROUND_KNOWLEDGE` (2), `PROPOSE_SOLUTION` (3), `SELECT_SOLUTION` (4), `BUILD_TEST` (5), `CLOSING_SHARE_REFLECT` (6).
   - Tương thích ngược: Brief cũ không có trường `steps` vẫn deserialize an toàn, trả finding `TECH-LESSON-OPENING` và `TECH-LESSON-CLOSING` thay vì lỗi 500.
   - Severity:
     - Thiếu hoặc unbound bước 1: `TECH-LESSON-OPENING` (ERROR - chặn ở STRICT).
     - Thiếu hoặc unbound bước 6: `TECH-LESSON-CLOSING` (ERROR - chặn ở STRICT).
     - Trùng lặp, sai thứ tự hoặc step key lạ: `TECH-LESSON-ORDER` (ERROR - chặn ở STRICT).
     - Thiếu bước giữa 2–5: `TECH-LESSON-STEPS` (WARN - cảnh báo, không chặn submit/approve).
3. **Rubric Reviewer Gates & Role Enforcement:**
   - Bắt buộc vai trò `REVIEWER` có ghế active thực tế thông qua `WorkspaceAccess.requireActiveStemSeat` (không cho phép Admin bypass). Admin không có ghế REVIEWER chấm rubric sẽ nhận `403 Forbidden` và DB không thay đổi (`AT24`).
   - Ở STRICT mode: `ScriptService.approve` và `FeedbackService.approveVideo` (cả 2 nhánh) bắt buộc phải có rubric assessment hợp lệ khớp fingerprint của đối tượng được chấm.
4. **Kiểm tra Fingerprint (Bảo vệ tính toàn vẹn):**
   - Tạo render job: kiểm tra fingerprint script hiện tại so với lần approve (`checkRenderFingerprint`).
   - Submit review / QA: snapshot composition tại `submitForVideoReview`, kiểm tra không bị sửa đổi lén trước khi reviewer duyệt (`checkVideoReviewFingerprint`).
   - Publish: kiểm tra composition/clip hiện tại khớp với lần duyệt video (`checkPublishFingerprint`).
   - Lệch fingerprint ở STRICT mode: trả về **`409 STEM_FINGERPRINT_STALE`** kèm `expected`/`actual` hash (không kèm nội dung). Ở ADVISORY: ghi log audit `STEM_FINGERPRINT_MISMATCH` và không chặn. Ở OFF: không ảnh hưởng.

---

## 3. Danh sách file thay đổi

| File | Thao tác | Mục đích |
|---|---|---|
| `src/main/resources/db/bridge-prod/V26__allow_stem_strict_mode.sql` | Tạo mới | Cập nhật CHECK constraint `stem_settings.mode` cho phép `'STRICT'` |
| `src/test/resources/db/stem-p3/V91001__stem_strict_mode_test_only.sql` | Tạo mới | Fixture test embedded Postgres chấp nhận `'STRICT'` |
| `src/main/java/com/stemotion/domain/stem/StemDtos.java` | Sửa đổi | Thêm `LessonStep`, hỗ trợ `steps` trong `Brief` và `expectedFingerprint` trong `Assessment` |
| `src/main/java/com/stemotion/domain/stem/StemValidator.java` | Sửa đổi | Bổ sung kiểm tra 6 bước bài học STEM (`TECH-LESSON-*`) |
| `src/main/java/com/stemotion/domain/stem/StemService.java` | Sửa đổi | Cho phép lưu mode `STRICT`, tích hợp kiểm tra rubric và fingerprint gates |
| `src/main/java/com/stemotion/domain/production/service/RenderService.java` | Sửa đổi | Gọi `checkRenderFingerprint` trước khi tạo render job |
| `src/main/java/com/stemotion/domain/review/service/FeedbackService.java` | Sửa đổi | Gọi `checkSubmitVideoReview` và `checkVideoReviewFingerprint` |
| `src/main/java/com/stemotion/domain/production/service/PublishService.java` | Sửa đổi | Gọi `checkPublishFingerprint` trước khi publish |
| `src/test/java/com/stemotion/domain/stem/StemRf11AcceptanceTest.java` | Tạo mới | Bộ acceptance test đầy đủ cho RF-11 (AT03, AT24, RF11_*) |
| `src/test/java/com/stemotion/domain/stem/StemPostgresTest.java` | Sửa đổi | Cập nhật số migration mong đợi (25 -> 26) và test STRICT |
| `src/test/java/com/stemotion/e2e/StemFullContextRegressionTest.java` | Sửa đổi | Cập nhật số migration mong đợi (25 -> 26) |
| `src/test/java/com/stemotion/e2e/StemArtifactP5Test.java` | Sửa đổi | Cập nhật số migration mong đợi (26 -> 27) |
| `src/test/java/com/stemotion/e2e/StemP6HttpTest.java` | Sửa đổi | Cập nhật số migration mong đợi (26 -> 27) |

---

## 4. Migration V26

- **File:** `src/main/resources/db/bridge-prod/V26__allow_stem_strict_mode.sql`
- **Nội dung:** Thay thế CHECK constraint trên cột `mode` của bảng `stem_settings`:
  ```sql
  -- Requires Lê review; applied only with V23–V25 in R3/R5
  ALTER TABLE public.stem_settings DROP CONSTRAINT stem_settings_mode_check;
  ALTER TABLE public.stem_settings ADD CONSTRAINT stem_settings_mode_check CHECK (mode IN ('OFF', 'ADVISORY', 'STRICT'));
  ```
- **Lưu ý đặc biệt:**
  > [!IMPORTANT]
  > **File migration này cần Lê review và đưa vào gói R3/R5; chưa áp production.**
  > Migration không nằm trong `db/migration/` mặc định, không ảnh hưởng đến database hiện hành.

---

## 5. Kết quả kiểm thử (Surefire Reports)

Dựa trên kết quả chạy surefire từ `check.sh`:
- **Tổng số test chạy (Run):** 602
- **Thất bại (Failures):** 0
- **Lỗi (Errors):** 0
- **Bỏ qua (Skipped):** 17 (các bài test ngoài cần tài nguyên mạng như `StemP6HttpTest`)
- **Chi tiết bộ test RF-11 (`StemRf11AcceptanceTest`):** 8/8 test PASSED:
  - `AT03`: STRICT, script không có brief → lưu draft OK, submit bị 400 `STEM_VALIDATION_FAILED`.
  - `RF11_STRICT_SETTINGS`: Admin đặt STRICT OK; non-admin 403; revision cũ 409; chuyển STRICT → OFF giữ nguyên dữ liệu.
  - `RF11_LESSON_OPENING_CLOSING_ORDER`: Đầy đủ mã lỗi `TECH-LESSON-OPENING`, `TECH-LESSON-CLOSING`, `TECH-LESSON-ORDER` (ERROR), `TECH-LESSON-STEPS` (WARN); đúng hành vi chặn ở STRICT và không chặn ở OFF/ADVISORY.
  - `RF11_RUBRIC_SCRIPT`: Approve thiếu rubric → 400; rubric fingerprint cũ → 409; rubric hợp lệ → 200 OK.
  - `RF11_RUBRIC_VIDEO`: Đạt trên cả 2 nhánh (nhánh clip và nhánh composition / web player gọi `renderApproved`).
  - `AT24`: Admin không có ghế REVIEWER chấm rubric → 403, DB không thay đổi.
  - `RF11_FINGERPRINT_RENDER_REVIEW_PUBLISH`: STRICT lệch fingerprint → 409 `STEM_FINGERPRINT_STALE`; ADVISORY lệch → không chặn, ghi audit.
  - `RF11_MIGRATION_V26`: Test DB nhận fixture mới chấp nhận mode `'STRICT'`; `db/migration` mặc định không có bảng STEM.

---

## 6. Rủi ro còn mở

- Các nhãn hiển thị của template bài học 6 bước hiện mang nhãn `DRAFT/UNVERIFIED` theo quy ước RF-10/RF-11 và sẽ được chuẩn hóa nội dung đối chiếu với tài liệu giáo dục từ Lê.
- Gói migration V26 cần được chạy đồng bộ cùng V23–V25 khi kích hoạt R3/R5 trên môi trường staging/production.

---

## 7. Chuyển giao cho RF-12 (FE của Trí)

1. **Settings Workspace (Admin Panel):**
   - API `PUT /api/v1/stem/settings` đã hỗ trợ mode `"STRICT"` bên cạnh `"OFF"` và `"ADVISORY"`.
   - Cần hiển thị cảnh báo cho Admin khi chọn STRICT: submit script và approve script/video sẽ bị chặn nếu không đạt rubric/template 6 bước.
2. **Brief Editor Panel (6 bước bài học STEM):**
   - Payload brief trong `StemDtos.Brief` hiện chấp nhận trường `steps` (danh sách `LessonStep`: `stepKey`, `sceneIds`, `activityKey`, `objectiveKeys`).
   - Cần UI để gắn scene/activity vào bước mở đầu (`OPENING_PROBLEM`) và bước kết thúc (`CLOSING_SHARE_REFLECT`).
3. **Reviewer Rubric UI:**
   - Khi workspace ở mode STRICT, nút Duyệt kịch bản và Duyệt video bắt buộc phải gửi kèm đối tượng `stemAssessment` với `rubricVersionId`, `criteria` và `fingerprint`.
   - Nếu kịch bản hoặc video bị sửa đổi sau khi mở form chấm, BE trả về `409 STEM_FINGERPRINT_STALE` với details `{expected, actual}` để FE hiển thị thông báo "Nội dung đã thay đổi, vui lòng tải lại và chấm lại".

---

## 8. Blocker

- **Không có.**
