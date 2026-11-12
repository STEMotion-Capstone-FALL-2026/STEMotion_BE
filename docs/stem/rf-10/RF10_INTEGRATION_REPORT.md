# BÁO CÁO TÍCH HỢP RF-10 (BM04) — STEM DOMAIN FLYWAY MIGRATION & API MERGE

- **Ngày thực hiện:** 12/11/2026 (Asia/Saigon)
- **Nhánh tích hợp:** `integrate/stem-rf10-20261112` (tạo từ `origin/main` tại `837c200`)
- **Nhánh nguồn STEM:** `origin/feat/stem-p3-isolated-20261105` (commit `9c7fdf5`)
- **Mục tiêu:** Chuẩn bị nhánh tích hợp RF-10 (Production Flyway bridge SQL cho các bảng STEM và API nhánh cô lập) để Owner và Lê Trần review. **Không merge vào main, không deploy.**

---

## 1. Kết quả Merge & Xử lý Xung đột

- **Nhánh cơ sở (Base):** `origin/main` tại commit `837c200`.
- **Nhánh nguồn (Source):** `origin/feat/stem-p3-isolated-20261105` tại commit `9c7fdf5` (merge-base `f6c6f0b`).
- **Thực thi:**
  ```bash
  git merge --no-ff origin/feat/stem-p3-isolated-20261105
  ```
- **Kết quả:** Chiến lược `ort` hoàn tất tự động với **0 xung đột** (tạo merge commit `5e66889`).
  - Toàn bộ 184 file mã nguồn STEM, package `com.stemotion.domain.stem.*`, AI Grounding P6, Fingerprint P5, và các hook trong library/production/review đều được tích hợp sạch sẽ.
  - Các tài liệu `docs/deliverables/**` và `docs/diagrams/**` giữ nguyên 100% bản của `main`.
  - Các mã yêu cầu (FR-62..73, BR-ST01..15) giữ nguyên vẹn theo chuẩn tài liệu chính thức.

---

## 2. Quyết định Đánh số Migration & Vị trí Lưu trữ Bridge SQL

### 2.1. Đánh số Migration
- Đối chiếu `deploy/auth-v2-drafts/` và `deploy/roles-mail-drafts/`: các file nháp tại đây là script chạy ngoài Flyway (`00_preflight...`, `01_expand...`).
- Auth v2 thực tế đã cutover trên production qua bảng ledger riêng `supabase_migrations.schema_migrations` (`20261102131936`), không qua Flyway.
- Flyway gốc dừng ở `V22__script_draft_revision.sql`. Do đó, giữ nguyên số hiệu `V23`, `V24`, `V25` như Lê đã thiết kế.

### 2.2. Vị trí Lưu trữ Cô lập (`db/bridge-prod`)
- **Vấn đề đã khắc phục:** Ba file `V23`–`V25` chứa các điều kiện tiền đề đặc thù của production (PostgreSQL 17, user `postgres`, ledger `supabase_migrations`, parent table ID kiểu `UUID`). Nếu đặt trong `classpath:db/migration`, Flyway mặc định sẽ tự quét và áp dụng vào DB dev/test trống, gây lỗi đứt gãy schema cục bộ.
- **Giải pháp:** Sử dụng `git mv` di chuyển 3 file bridge SQL sang:
  `src/main/resources/db/bridge-prod/`
  - `V23__assert_stem_bridge_prerequisites.sql`
  - `V24__create_stem_domain.sql`
  - `V25__create_stem_artifact_lineage.sql`
- **Xác minh SHA-256 (khớp 100% với `R2_HANDOFF.md`):**
  - `V23`: `d3ebb16fe9c262bf30b1c9d57f7ece38c4185fc29ad3244cd0e69d38b7d0815c`
  - `V24`: `5cfdb78bf3e8cc3af80f4beeb8ac159f20bd2d053e75b03fd553bd5a3c7bf502`
  - `V25`: `a7de399149abfb5289d37f5487b19edc6389ffda4ba0d6232adf83b17bf56cda`
  - Nội dung SQL của Lê được giữ nguyên vẹn từng byte, không chỉnh sửa trực tiếp.

### 2.3. Cấu hình Flyway Locations
- **Profile Production (`application-cloud.yml`):**
  ```yaml
  spring:
    flyway:
      enabled: false
      locations: classpath:db/migration,classpath:db/bridge-prod
  ```
  *(Không thêm `classpath:db/auth-v2-e2e` vào cloud).*
- **Profile Mặc định & Dev/Test (`application.yml`):**
  Chỉ giữ `locations: classpath:db/migration`. Môi trường dev/test không bao giờ tự ý quét `db/bridge-prod`.
- **Môi trường Test:** Giữ nguyên hai file fixture `V91000__stem_p3_test_only.sql` và `V92000__stem_p5_artifact_snapshots_test_only.sql` trong `src/test/resources/db/` như trên nhánh của Lê để phục vụ kiểm thử cục bộ.

---

## 3. Kết quả Kiểm tra Thực tế

### 3a) Database Postgres trống (`stem_empty`)
- Chạy `mvn flyway:migrate` với cấu hình mặc định (`locations: classpath:db/migration`).
- **Kết quả:** Áp dụng thành công 22 migration từ `V1` đến `V22` (`BUILD SUCCESS`).
- **Kiểm tra schema:** Hoàn toàn **không có bất kỳ bảng STEM nào** (`Did not find any relation named "public.stem*"`). DB trống hoạt động ổn định, không bị văng lỗi ở V23.

### 3b) Database mô phỏng Production (`stem_prod_sim`)
> *Ghi chú quan trọng: Đây là thử nghiệm mô phỏng điều kiện tiền đề trên môi trường Docker PostgreSQL 17 cục bộ, không phải bằng chứng diễn tập trên production thực tế.*

- **Thiết lập:** DB PostgreSQL 17.10 cục bộ chứa:
  - Ledger `supabase_migrations.schema_migrations` với bản ghi `20261102131936`.
  - `public.flyway_schema_history` với Rank 1 là BASELINE và 21 checksum V2–V22 khớp tuyệt đối.
  - 10 bảng cha core có khóa chính `id UUID`, bảng `users` có cột Auth v2.
- **Thực thi:** Chạy Flyway với locations của profile cloud (`classpath:db/migration,classpath:db/bridge-prod`).
- **Kết quả:**
  ```text
  [INFO] Current version of schema "public": 22
  [INFO] Migrating schema "public" to version "23 - assert stem bridge prerequisites"
  [INFO] Migrating schema "public" to version "24 - create stem domain"
  [INFO] Migrating schema "public" to version "25 - create stem artifact lineage"
  [INFO] Successfully applied 3 migrations to schema "public", now at version v25
  [INFO] BUILD SUCCESS
  ```
  Khởi tạo hoàn tất **11 bảng STEM** (`stem_catalog`, `stem_briefs`, `stem_evidence`, `stem_settings`, `stem_assessments`, `stem_validation_runs`, `stem_validation_limits`, `stem_receipts`, `stem_audit`, `stem_p5_snapshots`, `stem_p5_compatibility`) và **4 append-only trigger** bất biến.

### 3c) Kết quả Chạy Kiểm thử (`mvn -q verify`)
- **Tổng số test thực thi:** **595 test**.
- **Passed:** **577 test**.
- **Failures:** **0 test**.
- **Skipped:** **17 test**.
- **Errors:** **1 test** (`com.stemotion.e2e.StemP6HttpTest`).

#### Phân tích chi tiết về test duy nhất bị lỗi (`StemP6HttpTest`):
- **Lỗi ghi nhận:**
  ```text
  Cannot run program "D:\CapstoneFall2026\STEMotion_BE\.audit\stem-p6\exact-lock\venv\Scripts\python.exe": CreateProcess error=2, The system cannot find the file specified
  ```
- **Nguyên nhân:** Mã kiểm thử `StemP6HttpTest.java` (dòng 44) gọi trực tiếp binary Python từ đường dẫn tuyệt đối `.audit/stem-p6/exact-lock/venv/Scripts/python.exe`. Thư mục `.audit` này là môi trường ảo cục bộ trên máy của Lê, nằm trong `.gitignore` và không được commit vào repository.
- **Đối chứng với nhánh gốc của Lê:** Khi thực hiện kiểm tra đối chứng trên nhánh `origin/feat/stem-p3-isolated-20261105`, test này cũng **fail lỗi tương tự** vì cùng lý do thiếu môi trường virtualenv cục bộ.
- **Kết luận:** Ngoại trừ test đặc thù môi trường cục bộ nói trên, toàn bộ **577 test** chức năng, unit test, integration test của cả Core và STEM P3/P5/P6 đều đạt tỷ lệ pass 100%.

### 3d) Kiểm tra Khởi động App khi STEM mode OFF
- Khởi động ứng dụng với profile mặc định trỏ vào DB Postgres trống (đã chạy V1–V22):
  - Toàn bộ các bean STEM (`StemService`, `StemRepository`, `StemArtifactService`, `StemController`, `StemArtifactController`...) đều được đánh dấu `@ConditionalOnProperty(name = "app.stem.enabled", havingValue = "true")`.
  - Khi STEM mode ở trạng thái OFF (mặc định), không có bất kỳ bean STEM nào được khởi tạo. Ứng dụng **hoàn toàn không cần và không đòi hỏi các bảng STEM** khi khởi động.
  - *Lưu ý về Hibernate:* Khi bật `ddl-auto: validate`, Hibernate sẽ kiểm tra các thực thể JPA của Auth v2 (`google_auth_challenges`...). Do Auth v2 nằm ngoài Flyway V1–V22, trên môi trường dev cục bộ cần bổ sung các bảng auth v2 hoặc đặt `ddl-auto: none`. Bản thân các bảng STEM không phải là thực thể JPA mà truy cập qua `JdbcTemplate` nên không ảnh hưởng tới Hibernate validation.

---

## 4. Hiện trạng Schema Drift (Repo ↔ Production)

Qua quá trình tích hợp và thử nghiệm, nhóm ghi nhận sự phân nhánh (schema drift) đáng kể giữa mã nguồn trong repository và cơ sở dữ liệu Production:

1. **Khóa chính bảng Core (VARCHAR(36) vs UUID):**
   - File `V1__initial_schema.sql` trong repository khởi tạo toàn bộ khóa chính (`users.id`, `workspaces.id`, `script_versions.id`, `scenes.id`...) dưới dạng `VARCHAR(36)`.
   - Trong khi đó, Production (Supabase) đã được chuyển đổi sang kiểu `UUID`. Bridge migration `V24` của Lê sử dụng `UUID` cho tất cả các foreign key trỏ về bảng cha.
2. **Auth v2 Ledger nằm ngoài Flyway:**
   - Các bảng định danh Google, thông báo, và phân quyền Auth v2 đã được áp dụng trực tiếp lên Production qua ledger của Supabase (`20261102131936`), không có migration Flyway tương ứng trong `src/main/resources/db/migration/`.

### Đề xuất Follow-up:
1. **Migration chuẩn hóa UUID cho mọi môi trường:** Xây dựng một Flyway migration chuẩn hóa chuyển đổi toàn bộ `VARCHAR(36)` sang `UUID` cho môi trường Dev/Test/CI, giúp DB khởi tạo từ Flyway khớp 100% với Production.
2. **Dựng Staging từ Schema-only Dump của Production:** Môi trường Staging phục vụ R3 rehearsal bắt buộc phải được dựng từ bản dump schema-only (`pg_dump --schema-only`) lấy từ Production (không trích xuất dữ liệu người dùng). Điều này đảm bảo môi trường diễn tập có đúng kiểu dữ liệu UUID, các bảng Auth v2, và ledger Supabase.

---

## 5. Rủi ro còn mở (Theo hồ sơ của Lê)

| Mã rủi ro | Trạng thái | Đánh giá & Khuyến nghị |
|---|---|---|
| **Conflict.C01** (Auth v2 writer & epoch) | **OPEN** | Cần xác nhận không còn bất kỳ external writer cũ nào ghi vào `public.users.role` trước khi triển khai bridge STEM. |
| **Conflict.C02** (Migration Engine & Ledger) | **OPEN** | Xác nhận quy trình chạy bridge SQL: chạy qua CLI độc lập với cờ `--single-transaction` để bảo đảm V23->V24->V25 chạy atomic trong một transaction. |
| **Bridge.BR01** (Rollback & Restore Proof) | **OPEN (R3 BLOCKED)** | Chưa có bằng chứng thử nghiệm khôi phục (`Restore proof NOT_RECEIVED`). Cần diễn tập rollback tại R3 trước khi mở Gate 3. |

---

## 6. Các bước Lê Trần và Owner cần Review trước R3 Rehearsal

1. **Xác nhận cấu trúc phân tách `db/bridge-prod`:** Kiểm tra vị trí mới của V23–V25 và cấu hình `application-cloud.yml`.
2. **Đối chiếu mã SHA-256:** Xác nhận lại 3 mã băm của V23, V24, V25 trong `src/main/resources/db/bridge-prod/`.
3. **Chuẩn bị môi trường Staging schema-only:** Dựng DB diễn tập từ dump schema production để tiến hành R3 rehearsal.
4. **Xử lý test `StemP6HttpTest`:** Bổ sung cấu hình đường dẫn Python động (hoặc biến môi trường `PYTHON_PATH`) để test này có thể chạy trên máy CI/CD mà không phụ thuộc vào thư mục `.audit` cục bộ.

---

## 7. Hướng dẫn cho Owner

Toàn bộ công việc sửa đổi đã được hoàn tất trên nhánh `integrate/stem-rf10-20261112`. **Không có lệnh push nào được thực hiện lên remote.**

Khi Owner sẵn sàng đưa nhánh lên remote để mở PR review, chạy các lệnh sau:

```bash
# 1. Đẩy nhánh tích hợp lên origin
git push origin integrate/stem-rf10-20261112

# 2. Tạo Draft Pull Request vào main
gh pr create --base main --head integrate/stem-rf10-20261112 --title "draft: RF-10 STEM domain Flyway migration & isolated API integration" --body-file docs/stem/rf-10/RF10_INTEGRATION_REPORT.md --draft
```
