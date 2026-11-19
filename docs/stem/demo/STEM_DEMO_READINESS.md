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
| **Tiểu học (Lớp 1–5)**<br>`stem-profile-primary` | `stem-source-gdpt2018`<br>(CTGDPT 2018 - TT 32/2018) | **`M4-MEAS-01`**<br>(`stem-outcome-primary-math-01`) | "Sử dụng được một số dụng cụ thông dụng để thực hành cân, đo, đong, đếm, xem thời gian với các đơn vị đo đã học."<br>*(Tóm tắt)*: Thực hành đo, xem thời gian bằng dụng cụ thông dụng và giải quyết vấn đề thực tiễn với các số đo đã học (nhiệt độ thuộc chương trình lớp 3, không dùng ở lớp 4). | `03-cttoan.pdf`, trang 38 (Lớp 4 - Đo lường - Thực hành đo đại lượng) | `stem-rubric-primary`<br>(3 tiêu chí, đạt ≥ 70) | `00000000-0000-0000-0000-000000000005`<br>(Chính sách STEM cấp Tiểu học) |
| **THCS (Lớp 6–9)**<br>`stem-profile-lower-secondary` | `stem-source-gdpt2018`<br>(CTGDPT 2018 - TT 32/2018) | **`KHTN8-THERM-01`**<br>(`stem-outcome-lower-sec-khtn-01`) | **Trích nguyên văn:**<br>"Lấy được ví dụ về hiện tượng dẫn nhiệt, đối lưu, bức xạ nhiệt và mô tả sơ lược được sự truyền năng lượng trong mỗi hiện tượng đó." | `11-ctkhoa-hoc-tu-nhien.pdf`<br>Trang 49<br>SHA256: `4d809882...` | `stem-rubric-lower-secondary`<br>(3 tiêu chí, đạt ≥ 75) | `00000000-0000-0000-0000-000000000010`<br>(Chính sách STEM cấp THCS) |
| **THCS (Lớp 6–9)**<br>`stem-profile-lower-secondary` | `stem-source-gdpt2018`<br>(CTGDPT 2018 - TT 32/2018) | **`M8-STAT-01`**<br>(`stem-outcome-lower-sec-math-01`) | "Lựa chọn và biểu diễn được dữ liệu vào bảng, biểu đồ thích hợp ở dạng: bảng thống kê; biểu đồ tranh; biểu đồ dạng cột/cột kép (column chart), biểu đồ hình quạt tròn (cho sẵn) (pie chart); biểu đồ đoạn thẳng (line graph)."<br>*(Tóm tắt)*: Thu thập, tổ chức dữ liệu thực nghiệm vào bảng và biểu diễn bằng biểu đồ thích hợp; phân tích xu thế biến thiên của đại lượng đo để rút ra kết luận. | `03-cttoan.pdf`, trang 68 (Lớp 8 - Mô tả và biểu diễn dữ liệu) | `stem-rubric-lower-secondary`<br>(3 tiêu chí, đạt ≥ 75) | `00000000-0000-0000-0000-000000000010`<br>(Chính sách STEM cấp THCS) |
| **THPT (Lớp 10–12)**<br>`stem-profile-upper-secondary` | `stem-source-gdpt2018`<br>(CTGDPT 2018 - TT 32/2018) | **`PHY11-ELEC-01`**<br>(`stem-outcome-upper-sec-physics-01`) | **Trích nguyên văn:**<br>"Phát biểu được định luật Ohm cho vật dẫn kim loại." | `12-ctvat-li.pdf`<br>Trang 21<br>SHA256: `0a8754c7...` | `stem-rubric-upper-secondary`<br>(3 tiêu chí, đạt ≥ 80) | `00000000-0000-0000-0000-000000000017`<br>(Chính sách STEM cấp THPT) |
| **THPT (Lớp 10–12)**<br>`stem-profile-upper-secondary` | `stem-source-gdpt2018`<br>(CTGDPT 2018 - TT 32/2018) | **`M11-DERIV-01`**<br>(`stem-outcome-upper-sec-math-01`) | "Giải quyết được một số vấn đề có liên quan đến môn học khác hoặc có liên quan đến thực tiễn gắn với đạo hàm (ví dụ: xác định vận tốc tức thời của một vật chuyển động không đều,...)."<br>*(Tóm tắt)*: Dùng đạo hàm để mô tả tốc độ biến đổi của đại lượng vật lí trong bài toán liên môn (bài toán tối ưu bằng đạo hàm thuộc chương trình lớp 12). | `03-cttoan.pdf`, trang 96 (Lớp 11 - Đạo hàm - Các quy tắc tính đạo hàm) | `stem-rubric-upper-secondary`<br>(3 tiêu chí, đạt ≥ 80) | `00000000-0000-0000-0000-000000000017`<br>(Chính sách STEM cấp THPT) |

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
  [PASS] Khớp nguyên văn tại trang 38 của 03-cttoan.pdf
         Chủ đề/mục: Lớp 4 - Hình học và đo lường - Đo lường - Thực hành đo đại lượng
         SHA-256: f35d34ff84da2ca3... (khớp)
         Trích dẫn: "Sử dụng được một số dụng cụ thông dụng để thực hành cân, đo, đong, đếm, xem thời gian với các đơn vị đo đã học."

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
  [PASS] Khớp nguyên văn tại trang 68 của 03-cttoan.pdf
         Chủ đề/mục: Lớp 8 - Một số yếu tố thống kê - Mô tả và biểu diễn dữ liệu trên các bảng, biểu đồ
         SHA-256: f35d34ff84da2ca3... (khớp)
         Trích dẫn: "Lựa chọn và biểu diễn được dữ liệu vào bảng, biểu đồ thích hợp ở dạng: bảng thống kê; biểu đồ tranh; biểu đồ dạng cột/cột kép (column chart), biểu đồ hình quạt tròn (cho sẵn) (pie chart); biểu đồ đoạn thẳng (line graph)."

[PHY11-ELEC-01] (stem-outcome-upper-sec-physics-01):
  [PASS] Khớp nguyên văn tại trang 21 của 12-ctvat-li.pdf
         Chủ đề/mục: Chuyên đề Mạch điện và điện trở
         SHA-256: 0a8754c7823eaaa2... (khớp)
         Trích dẫn: "Phát biểu được định luật Ohm cho vật dẫn kim loại."

[M11-DERIV-01] (stem-outcome-upper-sec-math-01):
  [PASS] Khớp nguyên văn tại trang 96 của 03-cttoan.pdf
         Chủ đề/mục: Lớp 11 - Đạo hàm - Các quy tắc tính đạo hàm
         SHA-256: f35d34ff84da2ca3... (khớp)
         Trích dẫn: "Giải quyết được một số vấn đề có liên quan đến môn học khác hoặc có liên quan đến thực tiễn gắn với đạo hàm (ví dụ: xác định vận tốc tức thời của một vật chuyển động không đều,...)."

======================================================================
  KẾT QUẢ TỔNG HỢP VERIFY-QUOTES
======================================================================
  Số YCCĐ đạt (PASS nguyên văn)      : 6
  Số YCCĐ chưa đối soát (text: null) : 0
  Số YCCĐ lỗi (FAIL)                 : 0
  YCCĐ qua verify-quotes: 6/6
======================================================================
```

---

## 2. Danh sách "Cần người kiểm tra" (Chuyên môn Sư phạm) và kết quả đối chiếu 19/11/2026

Các hạng mục sau được chuẩn bị ở mức kỹ thuật nhưng cần giáo viên / chuyên gia giáo dục rà soát trước khi chuyển sang `status: "ACTIVE"` và `verificationStatus: "VERIFIED"`:

1. **YCCĐ môn Toán: đã đóng ngày 19/11/2026.** Cả ba YCCĐ đã có trích dẫn nguyên văn và qua `verify-quotes` trên `03-cttoan.pdf` (SHA-256 `f35d34ff…`).
   - **`M4-MEAS-01`:** dùng YCCĐ "Thực hành đo đại lượng" lớp 4 (trang 38). Lớp 4 không có yêu cầu về nhiệt độ (đơn vị đo nhiệt độ thuộc lớp 3, trang 32), nên tóm tắt đã bỏ nhiệt độ.
   - **`M8-STAT-01`:** dùng YCCĐ "Mô tả và biểu diễn dữ liệu trên các bảng, biểu đồ" lớp 8 (trang 68), đúng ý tóm tắt cũ.
   - **`M11-FUNCT-01` đổi thành `M11-DERIV-01`:** Toán 11 không có bài toán tối ưu bằng đạo hàm; nội dung này thuộc lớp 12. Hồ sơ THPT của gói là lớp 11 và quy tắc khớp lớp (AT01) không cho dùng YCCĐ lớp 12, nên đổi sang YCCĐ đạo hàm lớp 11 gắn với môn học khác và thực tiễn (trang 96). Id và logicalKey giữ nguyên.

2. **Ánh xạ 6 bước sư phạm trong `lessonTemplate`:**
   - **Cấp Tiểu học (CV 909):** Văn bản CV 909/BGDĐT-GDTH chia tiến trình bài học STEM thành 5 hoạt động. Để chuẩn hoá 6 bước kỹ thuật của hệ thống, hoạt động 2 ("Nghiên cứu kiến thức nền và đề xuất giải pháp") được ánh xạ thành 2 bước: `BACKGROUND_KNOWLEDGE` (Nghiên cứu kiến thức nền) và `PROPOSE_SOLUTION` (Đề xuất phương án thiết kế). Bước `SELECT_SOLUTION` tương ứng Hoạt động 3 ("Lựa chọn giải pháp và lập kế hoạch").
   - **Cấp THCS (CV 3089):** CV 3089/BGDĐT-GDTrH Mục 2 cũng tổ chức theo 5 hoạt động thiết kế kỹ thuật. Tương tự, hoạt động 2 được tách thành nghiên cứu kiến thức nền và xây dựng bản thiết kế sơ bộ (`PROPOSE_SOLUTION`), hoạt động 3 là trình bày và bảo vệ phương án (`SELECT_SOLUTION`).
   - **Cấp THPT (TT 86, đã đối chiếu 19/11/2026):** Thông tư 86/2026/TT-BGDĐT "Quy định về tổ chức hoạt động giáo dục tích hợp khoa học, công nghệ, kĩ thuật và toán học trong giáo dục phổ thông" (ký và có hiệu lực 30/9/2026). Điều 7 khoản 1 điểm c: bài học STEM gồm các hoạt động xác định vấn đề và giải pháp; thực hành khám phá kiến thức khoa học; thiết kế, chế tạo, lập trình, thử nghiệm và hoàn thiện sản phẩm. Điều 6 khoản 2 điểm b nêu quy trình thiết kế kĩ thuật. Bản trước dẫn chung "Điều 5, 6, 7" và nhắc "mô hình hoá", "nghiệm thu"; hai cụm này không có trong văn bản. Nay mỗi bước dẫn đúng điểm, khoản; văn bản không có bước "chia sẻ" riêng nên bước 6 ánh xạ vào "thử nghiệm, đánh giá và cải tiến".

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
