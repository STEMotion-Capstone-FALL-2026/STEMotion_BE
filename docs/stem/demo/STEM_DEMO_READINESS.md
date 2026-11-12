# Báo cáo Sẵn sàng Demo STEM (STEM Demo Readiness)

Ngày báo cáo: 2026-11-12
Nhánh thực hiện: `feat/stem-demo-ready-20261112`
Trạng thái: Hoàn tất các hạng mục kỹ thuật chuẩn bị cho Demo STEM ở chế độ STRICT.

---

## 1. Bảng nội dung gói dữ liệu Demo (`stem-demo-pack.v1.json`)

Toàn bộ 19 bản ghi trong gói demo đều ở trạng thái `status: "DRAFT"`, `verificationStatus: "UNVERIFIED"`, `verifiedBy: null`.

| Cấp học / Đối tượng | Nguồn chương trình (SOURCE_EDITION) | Hồ sơ cấp học (PROFILE) | Yêu cầu cần đạt (OUTCOME) | Trích nguyên văn (`text`) | Locator | Rubric đánh giá | Chính sách (POLICY ID) |
|---|---|---|---|---|---|---|---|
| **Tiểu học (Lớp 4)** | `GDPT2018-KHTN4-2018` & `GDPT2018-TOAN-2018` | `PROFILE-PRIMARY-4` | `OUTCOME-PRI-4-HEAT` (Dẫn nhiệt và cách nhiệt) | Có ("Nêu được một số vật dẫn nhiệt tốt...") | Chương 4: Năng lượng và sự biến đổi | `RUBRIC-PRIMARY-4-STRICT` (3 tiêu chí, đạt ≥ 70) | `00000000-0000-0000-0000-000000000005` |
| **Tiểu học (Lớp 4)** | | | `OUTCOME-PRI-4-MEASURE` (Đo nhiệt độ) | `null` (Cần kiểm tra) | `null` | | |
| **THCS (Lớp 8)** | `GDPT2018-KHTN-2018` & `GDPT2018-TOAN-2018` | `PROFILE-LOWER-SEC-8` | `OUTCOME-LOWER-8-HEAT` (Truyền nhiệt & đối lưu) | Có ("Lấy được ví dụ về các hình thức truyền nhiệt...") | Chủ đề 6: Nhiệt - Sự truyền nhiệt | `RUBRIC-LOWER-SEC-8-STRICT` (3 tiêu chí, đạt ≥ 75) | `00000000-0000-0000-0000-000000000006` |
| **THCS (Lớp 8)** | | | `OUTCOME-LOWER-8-GRAPH` (Biểu đồ nhiệt độ) | `null` (Cần kiểm tra) | `null` | | |
| **THPT (Lớp 11)** | `GDPT2018-VL-2018` & `GDPT2018-TOAN-2018` | `PROFILE-UPPER-SEC-11` | `OUTCOME-UPPER-11-MOTION` (Chuyển động biến đổi đều) | Có ("Vận dụng được các công thức của chuyển động...") | Chương 1: Động học - Bài CĐ biến đổi | `RUBRIC-UPPER-SEC-11-STRICT` (3 tiêu chí, đạt ≥ 80) | `00000000-0000-0000-0000-000000000007` |
| **THPT (Lớp 11)** | | | `OUTCOME-UPPER-11-VECTOR` (Phân tích lực & vectơ) | `null` (Cần kiểm tra) | `null` | | |

- **Thống kê YCCĐ:** Tổng cộng 6 YCCĐ: 3 YCCĐ có trích dẫn nguyên văn kèm locator; 3 YCCĐ có `text: null` (nằm trong danh sách cần chuyên gia kiểm tra).

---

## 2. Danh sách "Cần người kiểm tra" (Chuyên môn Sư phạm)

Các hạng mục sau được chuẩn bị ở mức kỹ thuật nhưng cần giáo viên / chuyên gia giáo dục rà soát trước khi chuyển sang `status: "ACTIVE"` và `verificationStatus: "VERIFIED"`:

1. **YCCĐ chưa có văn bản trích dẫn chính thức (`text: null`):**
   - `OUTCOME-PRI-4-MEASURE`: Đọc và so sánh số đo nhiệt độ trên nhiệt kế (Toán 4). Cần đối chiếu trang chính xác trong Thông tư 32/2018/TT-BGDĐT.
   - `OUTCOME-LOWER-8-GRAPH`: Đọc và giải thích biểu đồ biến thiên nhiệt độ (Toán 8 / KHTN 8). Cần rà soát locator trong chương trình môn Toán THCS.
   - `OUTCOME-UPPER-11-VECTOR`: Phân tích vectơ vận tốc và gia tốc trong mặt phẳng (Vật lí 11 / Toán 11). Cần rà soát chuẩn kiến thức môn Vật lí 11 mới.

2. **Ánh xạ 6 bước sư phạm trong demo brief:**
   - Đã ánh xạ 6 bước (`OPENING_PROBLEM`, `EXPLORATION_QUESTION`, `HYPOTHESIS`, `INVESTIGATION_METHOD`, `DATA_REPRESENTATION`, `CLAIM_EVIDENCE_REASONING`) vào các scene kịch bản mẫu cho 3 cấp học (`primary-valid.json`, `lower-secondary-valid.json`, `upper-secondary-valid.json`). Cần tổ nội dung duyệt lại câu từ kịch bản để tự nhiên hơn trong video thực tế.

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
  - `testImportCatalogAsAdminSuccess` (PASS)
  - `testImportFirstTimeCreatesAllAndSecondTimeSkipsAll` (PASS)
  - `testImportConflictThrows409` (PASS)
  - `testImportAsNonAdminThrows403` (PASS)
  - `testImportWhenStemDisabledThrows404` (PASS)
  - `testAT16PrimaryValidBriefPassesValidation` (PASS)
  - `testAT16PrimaryInvalidBriefFailsValidation` (PASS)
  - `testAT16LowerSecondaryValidBriefPassesValidation` (PASS)
  - `testAT16LowerSecondaryInvalidBriefFailsValidation` (PASS)
  - `testAT16UpperSecondaryValidBriefPassesValidation` (PASS)
  - `testAT16UpperSecondaryInvalidBriefFailsValidation` (PASS)

### 3.3. Kết quả kịch bản Smoke (`smoke-stem.py` với E2eServer)
Chạy tự động thông qua `StemSmokePythonIntegrationTest`:
```
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
```
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
   - Người nắm giữ quyền DB chạy psql trực tiếp với cờ `--single-transaction`:
     ```bash
     cat src/main/resources/db/bridge-prod/V23__assert_stem_bridge_prerequisites.sql \
         src/main/resources/db/bridge-prod/V24__create_stem_domain.sql \
         src/main/resources/db/bridge-prod/V25__create_stem_artifact_lineage.sql \
         src/main/resources/db/bridge-prod/V26__allow_stem_strict_mode.sql \
       | psql "<PROD_DB_URL>" --single-transaction -v ON_ERROR_STOP=1
     ```

5. **Bước 5: Cấu hình biến môi trường và deploy Backend:**
   - Thiết lập cấu hình môi trường cho Cloud Run / Host:
     - `APP_STEM_ENABLED=true`
     - `APP_STEM_ARTIFACT_ENABLED=true`
   - Tiến hành deploy bản build của Backend lên host.

6. **Bước 6: Nạp gói Demo Catalog:**
   - Đăng nhập với quyền Admin trên môi trường host để lấy JWT token.
   - Gọi API nạp dữ liệu:
     ```bash
     curl -X POST "https://<host-api-url>/api/v1/stem/catalog/import" \
          -H "Authorization: Bearer <ADMIN_JWT_TOKEN>" \
          -H "Content-Type: application/json" \
          -d '{"pack": "stem-demo-pack.v1"}'
     ```

7. **Bước 7: Chạy kịch bản Smoke kiểm tra sau triển khai:**
   - Tạo một workspace demo riêng biệt để không ảnh hưởng workspace của người dùng thật.
   - Thiết lập các biến môi trường tài khoản kiểm thử (`STEM_SMOKE_ADMIN_*`, v.v.).
   - Chạy kịch bản:
     ```bash
     python deploy/stem-demo/smoke-stem.py \
            --base-url "https://<host-api-url>" \
            --workspace "<DEMO_WORKSPACE_ID>" \
            --level PRIMARY \
            --i-understand-this-writes-demo-data
     ```

8. **Bước 8: Quy trình Rollback / Tắt khẩn cấp (Emergency Off):**
   - Trong trường hợp phát sinh sự cố hoặc kết thúc phiên demo:
     - Đặt `APP_STEM_ENABLED=false` và khởi động lại Backend (hoặc redeploy).
     - **Không thực hiện DROP các bảng STEM** nhằm bảo toàn tính toàn vẹn dữ liệu và audit ledger.

---

## 5. Blocker

- **Không có blocker.**
- Tất cả các kiểm thử đơn vị, kiểm thử tích hợp, kiểm tra bảo mật và kiểm tra schema đều hoàn thành với kết quả 100% PASS.
