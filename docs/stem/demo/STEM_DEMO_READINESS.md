# Báo cáo Sẵn sàng Demo STEM (STEM Demo Readiness)

Ngày báo cáo: 2026-11-13 (Cập nhật sau review vòng 1)
Nhánh thực hiện: `feat/stem-demo-ready-20261112`
Trạng thái: Hoàn tất chuẩn hoá dữ liệu tiếng Việt có dấu, đối soát trích nguyên văn YCCĐ, cập nhật mã test AT16 và cơ chế kiểm chứng `verify-quotes`.

---

## 1. Bảng nội dung gói dữ liệu Demo (`stem-demo-pack.v1.json`)

Toàn bộ 19 bản ghi trong gói demo đều ở trạng thái `status: "DRAFT"`, `verificationStatus: "UNVERIFIED"`, `verifiedBy: null`. Không chứa chuỗi `"TEST_ONLY"` trong tên chính sách và rubric. Bảng dưới đây được trích xuất trực tiếp từ file gói `src/main/resources/stem/demo-pack/stem-demo-pack.v1.json`:

| Cấp học / Hồ sơ (PROFILE) | Nguồn chương trình (SOURCE_EDITION) | Mã YCCĐ (OUTCOME) | Trích nguyên văn (`text`) / Tóm tắt (`summary`) | Locator (File PDF & Trang) | Rubric đánh giá (RUBRIC) | Chính sách (POLICY ID) |
|---|---|---|---|---|---|---|
| **Tiểu học (Lớp 1–5)**<br>`stem-profile-primary` | `stem-source-gdpt2018`<br>(CTGDPT 2018 - TT 32/2018) | **`SCI4-ENG-01`**<br>(`stem-outcome-primary-science-01`) | **Trích nguyên văn:**<br>"Vận dụng được kiến thức về vật dẫn nhiệt tốt hoặc kém để giải thích một số hiện tượng tự nhiên; để giải quyết một số vấn đề đơn giản trong cuộc sống." | `10-ctkhoa-hoc.pdf`<br>Trang 11<br>SHA256: `bdcf1384...` | `stem-rubric-primary`<br>(3 tiêu chí, đạt ≥ 70) | `00000000-0000-0000-0000-000000000005`<br>(Chính sách STEM cấp Tiểu học) |
| **Tiểu học (Lớp 1–5)**<br>`stem-profile-primary` | `stem-source-gdpt2018`<br>(CTGDPT 2018 - TT 32/2018) | **`M4-MEAS-01`**<br>(`stem-outcome-primary-math-01`) | `text: null`<br>*(Tóm tắt ý đồ sư phạm)*: Thực hiện được việc đo, đọc số đo thời gian và nhiệt độ bằng dụng cụ đo thông dụng; thực hiện ước lượng và so sánh các số đo trong giải quyết vấn đề thực tiễn. | `null`<br>*(Cần người kiểm tra)* | `stem-rubric-primary`<br>(3 tiêu chí, đạt ≥ 70) | `00000000-0000-0000-0000-000000000005`<br>(Chính sách STEM cấp Tiểu học) |
| **THCS (Lớp 6–9)**<br>`stem-profile-lower-secondary` | `stem-source-gdpt2018`<br>(CTGDPT 2018 - TT 32/2018) | **`KHTN8-THERM-01`**<br>(`stem-outcome-lower-sec-khtn-01`) | **Trích nguyên văn:**<br>"Lấy được ví dụ về hiện tượng dẫn nhiệt, đối lưu, bức xạ nhiệt và mô tả sơ lược được sự truyền năng lượng trong mỗi hiện tượng đó." | `11-ctkhoa-hoc-tu-nhien.pdf`<br>Trang 49<br>SHA256: `4d809882...` | `stem-rubric-lower-secondary`<br>(3 tiêu chí, đạt ≥ 75) | `00000000-0000-0000-0000-000000000010`<br>(Chính sách STEM cấp THCS) |
| **THCS (Lớp 6–9)**<br>`stem-profile-lower-secondary` | `stem-source-gdpt2018`<br>(CTGDPT 2018 - TT 32/2018) | **`M8-STAT-01`**<br>(`stem-outcome-lower-sec-math-01`) | `text: null`<br>*(Tóm tắt ý đồ sư phạm)*: Thu thập, tổ chức dữ liệu thực nghiệm vào bảng và biểu diễn bằng biểu đồ thích hợp; phân tích xu thế biến thiên của đại lượng đo để rút ra kết luận. | `null`<br>*(Cần người kiểm tra)* | `stem-rubric-lower-secondary`<br>(3 tiêu chí, đạt ≥ 75) | `00000000-0000-0000-0000-000000000010`<br>(Chính sách STEM cấp THCS) |
| **THPT (Lớp 10–12)**<br>`stem-profile-upper-secondary` | `stem-source-gdpt2018`<br>(CTGDPT 2018 - TT 32/2018) | **`PHY11-ELEC-01`**<br>(`stem-outcome-upper-sec-physics-01`) | **Trích nguyên văn:**<br>"Phát biểu được định luật Ohm cho vật dẫn kim loại." | `12-ctvat-li.pdf`<br>Trang 21<br>SHA256: `0a8754c7...` | `stem-rubric-upper-secondary`<br>(3 tiêu chí, đạt ≥ 80) | `00000000-0000-0000-0000-000000000017`<br>(Chính sách STEM cấp THPT) |
| **THPT (Lớp 10–12)**<br>`stem-profile-upper-secondary` | `stem-source-gdpt2018`<br>(CTGDPT 2018 - TT 32/2018) | **`M11-FUNCT-01`**<br>(`stem-outcome-upper-sec-math-01`) | `text: null`<br>*(Tóm tắt ý đồ sư phạm)*: Mô hình hoá các mối quan hệ đại lượng vật lí bằng hàm số; giải bài toán tối ưu đơn giản và biện luận sự phụ thuộc giữa các thông số mạch điện. | `null`<br>*(Cần người kiểm tra)* | `stem-rubric-upper-secondary`<br>(3 tiêu chí, đạt ≥ 80) | `00000000-0000-0000-0000-000000000017`<br>(Chính sách STEM cấp THPT) |

### 1.1. Kết quả kiểm chứng tự động (`deploy/stem-demo/verify-quotes.py`)

Chạy công cụ kiểm chứng trích dẫn nguyên văn với các tệp PDF chính thức lưu tại `D:\stem-sources`:

```text
======================================================================
  STEM DEMO: KIỂM CHỨNG TRÍCH DẪN NGUYÊN VĂN YCCĐ (VERIFY QUOTES)
======================================================================
Gói dữ liệu : D:\CapstoneFall2026\STEMotion_BE\src\main\resources\stem\demo-pack\stem-demo-pack.v1.json
Thư mục nguồn: D:\stem-sources
----------------------------------------------------------------------
Tổng số YCCĐ cần kiểm tra: 6

[M4-MEAS-01] (stem-outcome-primary-math-01):
  Trạng thái: text = null (Chưa có trích dẫn nguyên văn đối soát)
  Tóm tắt ý đồ sư phạm: Thực hiện được việc đo, đọc số đo thời gian và nhiệt độ bằng dụng cụ đo thông dụng; thực hiện ước lượng và so sánh các số đo trong giải quyết vấn đề thực tiễn.
  -> Đưa vào danh sách 'CẦN NGƯỜI KIỂM TRA'

[SCI4-ENG-01] (stem-outcome-primary-science-01):
  [PASS] Khớp nguyên văn tại trang 11 của 10-ctkhoa-hoc.pdf
         Chủ đề/mục: Chủ đề Năng lượng - Nhiệt (Các vật dẫn nhiệt tốt và dẫn nhiệt kém; ứng dụng trong đời sống)
         SHA-256: bdcf1384bc385b48... (khớp)
         Trích dẫn: "Vận dụng được kiến thức về vật dẫn nhiệt tốt hoặc kém để giải thích một số hiện tượng tự nhiên; để giải quyết một số vấn đề đơn giản trong cuộc sống."

[KHTN8-THERM-01] (stem-outcome-lower-sec-khtn-01):
  [PASS] Khớp nguyên văn tại trang 49 của 11-ctkhoa-hoc-tu-nhien.pdf
         Chủ đề/mục: Chủ đề Nhiệt (Năng lượng nhiệt, Dẫn nhiệt, đối lưu, bức xạ nhiệt)
         SHA-256: 4d8098825841e7de... (khớp)
         Trích dẫn: "Lấy được ví dụ về hiện tượng dẫn nhiệt, đối lưu, bức xạ nhiệt và mô tả sơ lược được sự truyền năng lượng trong mỗi hiện tượng đó."

[M8-STAT-01] (stem-outcome-lower-sec-math-01):
  Trạng thái: text = null (Chưa có trích dẫn nguyên văn đối soát)
  Tóm tắt ý đồ sư phạm: Thu thập, tổ chức dữ liệu thực nghiệm vào bảng và biểu diễn bằng biểu đồ thích hợp; phân tích xu thế biến thiên của đại lượng đo để rút ra kết luận.
  -> Đưa vào danh sách 'CẦN NGƯỜI KIỂM TRA'

[PHY11-ELEC-01] (stem-outcome-upper-sec-physics-01):
  [PASS] Khớp nguyên văn tại trang 21 của 12-ctvat-li.pdf
         Chủ đề/mục: Chuyên đề Mạch điện và điện trở
         SHA-256: 0a8754c7823eaaa2... (khớp)
         Trích dẫn: "Phát biểu được định luật Ohm cho vật dẫn kim loại."

[M11-FUNCT-01] (stem-outcome-upper-sec-math-01):
  Trạng thái: text = null (Chưa có trích dẫn nguyên văn đối soát)
  Tóm tắt ý đồ sư phạm: Mô hình hoá các mối quan hệ đại lượng vật lí bằng hàm số; giải bài toán tối ưu đơn giản và biện luận sự phụ thuộc giữa các thông số mạch điện.
  -> Đưa vào danh sách 'CẦN NGƯỜI KIỂM TRA'

======================================================================
  KẾT QUẢ TỔNG HỢP VERIFY-QUOTES
======================================================================
  Số YCCĐ đạt (PASS nguyên văn)      : 3
  Số YCCĐ chưa đối soát (text: null) : 3
  Số YCCĐ lỗi (FAIL)                 : 0
  YCCĐ qua verify-quotes: 3/6
======================================================================
```

---

## 2. Danh sách "Cần người kiểm tra" (Chuyên môn Sư phạm)

Các hạng mục sau được chuẩn bị ở mức kỹ thuật nhưng cần giáo viên / chuyên gia giáo dục rà soát trước khi chuyển sang `status: "ACTIVE"` và `verificationStatus: "VERIFIED"`:

1. **YCCĐ chưa có văn bản trích dẫn chính thức (`text: null`):**
   - **`M4-MEAS-01` (`stem-outcome-primary-math-01`):** Đo đạc, đọc số đo thời gian và nhiệt độ bằng dụng cụ thông dụng. Trong CTGDPT 2018 môn Toán lớp 4, đơn vị nhiệt độ (°C) được xếp ở lớp 3 và tích hợp thực hành ở lớp 4, nên chưa có một câu YCCĐ đơn nhất trùng khớp hoàn toàn. Cần chuyên gia sư phạm chốt câu trích dẫn chính thức hoặc tách thành mục tiêu liên môn.
   - **`M8-STAT-01` (`stem-outcome-lower-sec-math-01`):** Thu thập số liệu thực nghiệm và vẽ đồ thị biểu diễn biến thiên. Trong CTGDPT 2018 môn Toán lớp 8, YCCĐ nằm rải rác ở phần thống kê (bảng biểu, biểu đồ đoạn thẳng). Cần chuyên gia xác định số trang và câu văn cụ thể trong phụ lục môn Toán.
   - **`M11-FUNCT-01` (`stem-outcome-upper-sec-math-01`):** Mô hình hoá quan hệ đại lượng bằng hàm số và tối ưu thông số mạch điện. CTGDPT 2018 môn Toán 11 tập trung vào đạo hàm, giới hạn và hàm số lượng giác/mũ; bài toán tối ưu thực tiễn mạch điện mang tính liên môn Toán - Lí. Cần chuyên gia giáo dục phê duyệt văn bản định vị.

2. **Ánh xạ 6 bước sư phạm trong `lessonTemplate`:**
   - **Cấp Tiểu học (CV 909):** Văn bản CV 909/BGDĐT-GDTH chia tiến trình bài học STEM thành 5 hoạt động. Để chuẩn hoá 6 bước kỹ thuật của hệ thống, hoạt động 2 ("Nghiên cứu kiến thức nền và đề xuất giải pháp") được ánh xạ thành 2 bước: `BACKGROUND_KNOWLEDGE` (Nghiên cứu kiến thức nền) và `PROPOSE_SOLUTION` (Đề xuất phương án thiết kế). Bước `SELECT_SOLUTION` tương ứng Hoạt động 3 ("Lựa chọn giải pháp và lập kế hoạch").
   - **Cấp THCS (CV 3089):** CV 3089/BGDĐT-GDTrH Mục 2 cũng tổ chức theo 5 hoạt động thiết kế kỹ thuật. Tương tự, hoạt động 2 được tách thành nghiên cứu kiến thức nền và xây dựng bản thiết kế sơ bộ (`PROPOSE_SOLUTION`), hoạt động 3 là trình bày và bảo vệ phương án (`SELECT_SOLUTION`).
   - **Cấp THPT (TT 86):** Quy định theo Điều 5, Điều 6 và Điều 7 của Thông tư 86/2026/TT-BGDĐT về phát hiện vấn đề, mô hình hoá toán học chuyên sâu và nghiệm thu thử nghiệm.

---

## 3. Kết quả kiểm tra kỹ thuật

### 3.1. API Import Catalog (`POST /api/v1/stem/catalog/import`)
- Thực hiện xác thực quyền Admin (`Role.ADMIN`), kiểm tra schema JSON, import tuần tự theo thứ tự phụ thuộc (`SOURCE_EDITION` -> `PROFILE` -> `OUTCOME` -> `RUBRIC` -> `POLICY`).
- Idempotent: Import lần 1 tạo mới 19 bản ghi (`created: 19, skipped: 0`); import lần 2 bỏ qua toàn bộ (`created: 0, skipped: 19`).
- Phát hiện xung đột (409 Conflict) nếu bản ghi đã tồn tại bị thay đổi nội dung mà không nâng version.
- Ghi audit log vào bảng `stem_audit` khi import thành công.

### 3.2. Kết quả Unit / Acceptance Test (11/11 PASS)
- Lớp kiểm thử: `StemDemoCatalogAcceptanceTest`
- Kết quả: `11 tests run, 0 failures, 0 errors, 0 skipped`
  - `testImportFirstTimeCreatesAllAndSecondTimeSkipsAll` (PASS)
  - `testImportConflictThrows409AndKeepsDbUnchanged` (PASS)
  - `testImportWhenStemDisabledReturns404` (PASS)
  - `testControllerEndpointImport` (PASS)
  - `testImportNonAdminThrows403` (PASS)
  - `AT16_PRIMARY_VALID` (PASS)
  - `AT16_PRIMARY_INVALID` (PASS)
  - `AT16_LOWER_SECONDARY_VALID` (PASS)
  - `AT16_LOWER_SECONDARY_INVALID` (PASS)
  - `AT16_UPPER_SECONDARY_VALID` (PASS)
  - `AT16_UPPER_SECONDARY_INVALID` (PASS)

### 3.3. Kết quả kịch bản Smoke (`smoke-stem.py` với E2eServer)
Chạy tự động thông qua `StemSmokePythonIntegrationTest`:
```text
======================================================================
STEM SMOKE TEST SUMMARY (Target: http://127.0.0.1:49939, Level: PRIMARY)
======================================================================
STEP                                     | STATUS | NOTE
----------------------------------------------------------------------
Login ADMIN                              | PASS   | User e2e-admin authenticated
Login WRITER                             | PASS   | User smoke-writer authenticated
Login REVIEWER                           | PASS   | User smoke-reviewer authenticated
Login PRODUCER                           | PASS   | User smoke-producer authenticated
Catalog Import                           | PASS   | Pack imported (created=0, skipped=19)
Set STRICT Mode                          | PASS   | Workspace set to STRICT with policy 00000000-0000-0000-0000-000000000005
Create Project                           | PASS   | Project f35f021c-afe2-434b-9dc9-6d56406d6729 created
Init STEM Brief                          | PASS   | STEM brief initialized
Save Valid Brief                         | PASS   | Valid 6-step brief saved for PRIMARY
Validate Brief                           | PASS   | Brief validation passed
Submit For Review                        | PASS   | Script transitioned to IN_REVIEW in STRICT mode
Fetch Brief Fingerprint                  | PASS   | Fingerprint: ba8bc85b46b599fc...
Approve Script                           | PASS   | Script successfully approved with matching fingerprint
Error Test: Invalid Brief Blocked        | PASS   | Submit correctly rejected (400 STEM_VALIDATION_FAILED)
Error Test: Stale Fingerprint Blocked    | PASS   | Approval correctly rejected (409 STEM_FINGERPRINT_STALE)
======================================================================
OVERALL RESULT: ALL PASS
```

### 3.4. Kết quả diễn tập R3 với bản dump giả lập (`rehearse-from-dump.sh`)
```text
==========================================================
 STEM Bridge Rehearsal (R3 from Schema-Only Dump)
==========================================================
Dump file : deploy/stem-bridge-rehearsal/prod-sim-schema.sql
Container : stem_rehearsal_dump_1794499968_1264
Host port : 15433
Bridge dir: /d/CapstoneFall2026/STEMotion_BE/src/main/resources/db/bridge-prod
With App  : NO
==========================================================
--> Starting postgres:17 container...
--> Waiting for PostgreSQL 17 to become ready...
PostgreSQL is ready.
--> Loading schema dump into database...
Schema dump loaded successfully.
--> Applying V23–V26 bridge migrations in ONE transaction...
  Applied V23–V26 successfully.

--> Running verification checks...
  [PASS] STEM table count == 11 (actual: 11)
  [PASS] Append-only triggers == 4 (actual: 4)
  [PASS] stem_settings.mode accepts STRICT
  [PASS] stem_settings.mode rejects invalid value
--> Testing re-run failure / transaction rollback...
  [PASS] Re-running V23 failed as expected and rolled back

==========================================================
 Dump Rehearsal Result: 5 PASS, 0 FAIL
==========================================================
RESULT: ALL PASS
```
*(Ghi chú: Đây là kiểm chứng trên bản dump mô phỏng `prod-sim-schema.sql`, chưa phải bản dump thật từ Production).*

---

## 4. Runbook Demo trên Host (Dành riêng cho Chủ Sở Hữu / DevOps)

> [!IMPORTANT]
> Agent **tuyệt đối không thực hiện** các bước dưới đây trên môi trường Production.
> Toàn bộ quy trình phải do người có thẩm quyền nắm giữ thông tin đăng nhập Production thực hiện.

1. **Bước 1: Backup cơ sở dữ liệu và kiểm tra khôi phục:**
   - Thực hiện snapshot / backup toàn phần trên Supabase hoặc hệ quản trị DB.
   - Kiểm tra khả năng restore bản backup ra một DB thử nghiệm độc lập.

2. **Bước 2: Xuất bản dump schema-only từ Production:**
   - Chạy lệnh xuất cấu trúc (không chứa dữ liệu):
     ```bash
     pg_dump -h <prod-host> -U <prod-user> -d <prod-db> --schema-only --no-owner --no-privileges -f /path/outside/repo/prod-schema-only.sql
     ```
   - Đảm bảo file dump nằm ngoài thư mục git repository.

3. **Bước 3: Diễn tập Gate R3 với dump schema-only:**
   ```bash
   cd D:/CapstoneFall2026/STEMotion_BE
   bash deploy/stem-bridge-rehearsal/rehearse-from-dump.sh --dump /path/outside/repo/prod-schema-only.sql --with-app
   ```
   - Xác nhận đầu ra `RESULT: ALL PASS`.

4. **Bước 4: Áp dụng V23–V26 lên Production trong một giao dịch duy nhất:**
   ```bash
   psql -h <prod-host> -U <prod-user> -d <prod-db> -v ON_ERROR_STOP=1 -1      -f src/main/resources/db/bridge-prod/V23__stem_schema_production.sql      -f src/main/resources/db/bridge-prod/V24__stem_policies_production.sql      -f src/main/resources/db/bridge-prod/V25__stem_p5_snapshots_production.sql      -f src/main/resources/db/bridge-prod/V26__stem_p5_strict_production.sql
   ```
   - Kiểm tra mã trả về: 0. Nếu có lỗi, giao dịch tự động hoàn tác toàn bộ.

5. **Bước 5: Kích hoạt cờ tính năng STEM trên Production:**
   - Cấu hình biến môi trường:
     ```bash
     APP_STEM_ENABLED=true
     ```
   - Khởi động lại ứng dụng Backend.

6. **Bước 6: Import gói Catalog mẫu:**
   - Chạy kịch bản `smoke-stem.py` với thông tin tài khoản Admin và URL Production hoặc gọi qua API:
     ```bash
     curl -X POST https://<prod-domain>/api/v1/stem/catalog/import \
       -H "Authorization: Bearer <ADMIN_TOKEN>" \
       -H "Content-Type: application/json" \
       -d '{"pack":"stem-demo-pack.v1","idempotencyKey":"prod-seed-20261112"}'
     ```
   - Xác nhận phản hồi: `{"created": 19, "skipped": 0}`.
