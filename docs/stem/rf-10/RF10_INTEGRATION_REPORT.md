# BÁO CÁO TÍCH HỢP RF-10 (BM04) — STEM DOMAIN FLYWAY MIGRATION & API MERGE

- **Ngày thực hiện:** 12/11/2026 (Asia/Saigon)
- **Nhánh tích hợp:** `integrate/stem-rf10-20261112` (tạo từ `origin/main` tại `837c200`)
- **Nhánh nguồn STEM:** `origin/feat/stem-p3-isolated-20261105` (commit `9c7fdf5`)
- **Mục tiêu:** Chuẩn bị tích hợp cho RF-10 (Production Flyway migration cho các bảng STEM và API nhánh cô lập) để Owner và Lê Trần review. **Không merge main, không deploy.**

---

## 1. Kết quả kiểm tra Working Tree & Commit đã merge

### 1.1. Kiểm tra trạng thái ban đầu (`git status`)
- Tại Bước 1, repo được kiểm tra bằng lệnh `git status`. Working tree ban đầu ghi nhận 2 file binary trong `docs/deliverables/bm/` bị thay đổi. Thực hiện nghiêm ngặt quy tắc an toàn và ràng buộc cứng không sửa hồ sơ Review 2, tiến trình đã dừng và báo cáo lại cho người phụ trách.
- Sau khi working tree được đưa về trạng thái sạch (`nothing to commit, working tree clean`), nhánh `integrate/stem-rf10-20261112` được tạo từ `origin/main` (`837c200`).

### 1.2. Thực hiện Merge
- Lệnh thực thi:
  ```bash
  git merge --no-ff origin/feat/stem-p3-isolated-20261105
  ```
- Kết quả merge: Chiến lược `ort` tự động hoàn tất thành công (tạo merge commit `5e66889`).
- **Xử lý xung đột:** **0 conflict**. Toàn bộ 184 file code, test, và tài liệu từ nhánh STEM P3–P6 của Lê hòa nhập sạch sẽ vào nhánh tích hợp.
  - Các tài liệu `docs/deliverables/**` và `docs/diagrams/**` giữ nguyên 100% bản của `main`.
  - Mã định danh yêu cầu chức năng (FR-62..73) và quy tắc nghiệp vụ (BR-ST01..15) giữ nguyên tính toàn vẹn theo `docs/stem/STEM_MASTER_PLAN_2026-11-04.md`.

---

## 2. Quyết định đánh số Migration (Bước 3)

### 2.1. Phân tích đối chiếu hồ sơ
1. **Kiểm tra `deploy/auth-v2-drafts/` và `deploy/roles-mail-drafts/`:**
   - Các file trong hai thư mục này được đánh số nội bộ (`00_preflight_read_only.sql`, `01_expand.sql`, `02_backfill...`).
   - Tài liệu `README.md` trong cả hai thư mục ghi rõ: *“These SQL files are deliberately outside production Flyway locations. These files are not production Flyway migrations.”*
2. **Kiểm tra trạng thái Auth v2 trên Production:**
   - Auth v2 đã cutover thành công trên production thông qua ledger riêng của Supabase: `supabase_migrations.schema_migrations` với mã phiên bản `20261102131936` (`stemotion_auth_roles_mail_legacy_username_cutover`).
   - Auth v2 **không** ghi nhận bản ghi nào vào bảng ledger Flyway `public.flyway_schema_history`.
3. **Kiểm tra Flyway hiện hữu trong `src/main/resources/db/migration/`:**
   - Migration cao nhất hiện tại là `V22__script_draft_revision.sql`.
   - Vị trí từ `V23` trở đi hoàn toàn trống.

### 2.2. Quyết định
- **Giữ nguyên đánh số V23, V24, V25** như bản thảo bridge SQL của Lê:
  - `V23__assert_stem_bridge_prerequisites.sql`
  - `V24__create_stem_domain.sql`
  - `V25__create_stem_artifact_lineage.sql`
- **Lý do:** Không tồn tại xung đột số hiệu với Auth v2 hay các bản nháp deploy. Việc giữ V23–V25 bảo đảm tính nhất quán tuyệt đối với các điều kiện kiểm tra checksum và lịch sử ledger đã viết trong `V23`.

---

## 3. Bản copy Bridge SQL (Bước 5)

Ba file bridge SQL từ nhánh `origin/docs/stem-weekly-report-2026-11-12` (`docs/stem/rollout-gate-02/BRIDGE_SQL_R2/`) đã được sao chép trực tiếp vào `src/main/resources/db/migration/`:

| File Migration | SHA-256 thực tế | SHA-256 trong manifest Lê (`R2_HANDOFF.md`) | Kết quả đối chiếu |
|---|---|---|---|
| `V23__assert_stem_bridge_prerequisites.sql` | `d3ebb16fe9c262bf30b1c9d57f7ece38c4185fc29ad3244cd0e69d38b7d0815c` | `d3ebb16fe9c262bf30b1c9d57f7ece38c4185fc29ad3244cd0e69d38b7d0815c` | **Trùng khớp 100%** |
| `V24__create_stem_domain.sql` | `5cfdb78bf3e8cc3af80f4beeb8ac159f20bd2d053e75b03fd553bd5a3c7bf502` | `5cfdb78bf3e8cc3af80f4beeb8ac159f20bd2d053e75b03fd553bd5a3c7bf502` | **Trùng khớp 100%** |
| `V25__create_stem_artifact_lineage.sql` | `a7de399149abfb5289d37f5487b19edc6389ffda4ba0d6232adf83b17bf56cda` | `a7de399149abfb5289d37f5487b19edc6389ffda4ba0d6232adf83b17bf56cda` | **Trùng khớp 100%** |

Nội dung SQL của Lê được giữ nguyên vẹn từng byte, không chỉnh sửa trực tiếp. Các đề xuất hiệu chỉnh được tách thành tài liệu riêng tại `docs/stem/rf-10/PROPOSED_SQL_FIXES_RF10.md`.

---

## 4. Đánh giá việc thay thế Test Migration bằng Migration thật (Bước 6)

### Kết luận: BẮT BUỘC GIỮ LẠI `V91000` VÀ `V92000`
Không thể xóa hai file `V91000__stem_p3_test_only.sql` và `V92000__stem_p5_artifact_snapshots_test_only.sql`.

### Giải thích nguyên nhân:
1. **Sự không tương thích về kiến trúc schema giữa Dev/Test và Production:**
   - Trong môi trường kiểm thử tự động (EmbeddedPostgres), DB được sinh từ đầu bằng các file Flyway `V1..V22`. Trong `V1__initial_schema.sql`, tất cả khóa chính của các bảng cha (`users.id`, `script_versions.id`, `scenes.id`...) đều là kiểu **`VARCHAR(36)`**.
   - Ngược lại, bridge SQL production của Lê (`V24`, `V25`) được thiết kế cho schema production (đã chuyển đổi sang **`UUID`**). Nếu ép test chạy `V24`, PostgreSQL sẽ báo lỗi nghiêm trọng: `Key columns "version_id" and "id" are of incompatible types: uuid and character varying`.
2. **Điều kiện ngặt nghèo của `V23`:**
   - `V23` kiểm tra phiên bản PostgreSQL 17, user `postgres`, sự tồn tại của ledger `supabase_migrations` với entry `20261102131936`, và 22 dòng baseline Flyway. Môi trường nhúng test (Zonky PG 16.2, user cục bộ) không thể thỏa mãn các điều kiện này.
3. **Phụ thuộc mã nguồn kiểm thử:**
   - Mã test trong `StemPostgresTest.java` (dòng 98) và `StemArtifactP5Test.java` (dòng 216) trực tiếp nạp `V91000` và `V92000` qua đường dẫn file/classpath để kiểm tra phản ứng khi trôi dạt prerequisite. Xóa hai file này sẽ gây lỗi `FileNotFoundException` khi chạy test.

---

## 5. Kết quả kiểm tra trên PostgreSQL 17 Local qua Docker (Bước 7)

Kiểm tra được thực hiện trên container Docker `postgres:17-alpine` (PostgreSQL 17.10) tại cổng `5433`:

### 7a) Kiểm tra trên Database trống (`stem_empty`)
- **Quy trình:** Chạy `mvn flyway:migrate` từ V1 đến mới nhất trên DB chưa có dữ liệu.
- **Kết quả:**
  - Áp dụng thành công các phiên bản từ `V1` đến `V22` (22 script hoàn tất).
  - **Dừng và báo lỗi tại `V23__assert_stem_bridge_prerequisites.sql`:**
    ```text
    ERROR: Bridge prerequisite ledger missing (thiếu supabase_migrations.schema_migrations)
    ERROR: Flyway history differs from the R1 V1 baseline and V2-V22 checksums (V1 là migration SQL thông thường, không phải BASELINE)
    ERROR: Expected public.users.id to be a single-column UUID primary key (users.id là varchar(36))
    ```
  - Khi bỏ qua V23 để áp dụng thử `V24`, tiến trình thất bại ngay lập tức:
    ```text
    ERROR: foreign key constraint "stem_briefs_version_id_fkey" cannot be implemented
    DETAIL: Key columns "version_id" and "id" are of incompatible types: uuid and character varying.
    ```
- **Ý nghĩa:** Chứng minh rằng bộ bridge SQL của Lê **chỉ** có thể chạy trên database đã đạt trạng thái cutover của production, hoàn toàn không hỗ trợ khởi tạo từ database trống chạy Flyway V1.

### 7b) Kiểm tra trên Database đã có V1–V22 và Auth v2 Baseline (`stem_prod_sim`)
- **Thiết lập:** Tạo database mô phỏng chính xác trạng thái production trước giờ G:
  - Khởi tạo bảng ledger `supabase_migrations.schema_migrations` với version `20261102131936`.
  - Khởi tạo `public.flyway_schema_history` với Rank 1 là BASELINE (checksum NULL) và 21 dòng V2..V22 với checksum khớp chính xác từng giá trị mà Lê yêu cầu.
  - Các bảng cha core (`users`, `workspaces`, `clip_projects`, `script_versions`, `scenes`, `status_histories`, `render_jobs`, `compositions`, `clips`, `publications`) có khóa chính `id UUID`.
  - Bảng `users` có đủ các cột Auth v2 và check constraint `users_system_role_check`.
  - Đầy đủ 6 bảng phụ trợ Auth v2 và Notification.
- **Kết quả:**
  ```text
  [INFO] Current version of schema "public": 22
  [INFO] Migrating schema "public" to version "23 - assert stem bridge prerequisites"
  [INFO] Migrating schema "public" to version "24 - create stem domain"
  [INFO] Migrating schema "public" to version "25 - create stem artifact lineage"
  [INFO] Successfully applied 3 migrations to schema "public", now at version v25 (execution time 00:00.160s)
  [INFO] BUILD SUCCESS
  ```
- **Xác minh đối tượng sau migration:**
  - Đủ **11 bảng STEM** mới (`stem_catalog`, `stem_briefs`, `stem_evidence`, `stem_settings`, `stem_assessments`, `stem_validation_runs`, `stem_validation_limits`, `stem_receipts`, `stem_audit`, `stem_p5_snapshots`, `stem_p5_compatibility`).
  - Đủ **4 append-only trigger** trên 2 bảng lineage:
    - `stem_p5_snapshots_immutable` (BEFORE UPDATE/DELETE)
    - `stem_p5_proofs_immutable` (BEFORE UPDATE/DELETE)
    - `stem_p5_snapshots_no_truncate` (BEFORE TRUNCATE)
    - `stem_p5_proofs_no_truncate` (BEFORE TRUNCATE)
  - Quyền sở hữu đối tượng thuộc về `postgres`, RLS đã được kích hoạt.

### 7c) Kết quả chạy kiểm thử toàn diện (`mvn -q verify`)
- **Tổng số test thực thi:** **573 test**.
- **Kết quả thống kê:**
  - **Passed:** **507 test**.
  - **Errors:** **49 test**.
  - **Skipped:** **17 test**.
  - **Failures:** **0 test**.
- **Phân tích nguyên nhân 49 lỗi:**
  - 100% (49/49) lỗi xuất hiện ở 7 test class tích hợp cơ sở dữ liệu (`StemPostgresTest` [29], `PostgresConcurrencyTest` [8], `RolesAndNotificationsPostgresTest` [7], `AuthMigrationRehearsalTest` [2], `StemArtifactP5Test` [1], `StemFullContextRegressionTest` [1], `StemP6HttpTest` [1]).
  - **Nguyên nhân cốt lõi:** Khi 3 file `V23`–`V25` được đặt vào `src/main/resources/db/migration/`, cấu hình Flyway trong testrunner tự động quét `classpath:db/migration`. Khi chạy trên Zonky EmbeddedPostgres, Flyway gặp `V23` và văng lỗi ngay dòng 7: `ERROR: Bridge requires PostgreSQL 17` (do EmbeddedPostgres là PG 16.2 và không có snapshot production).
  - Riêng `StemP6HttpTest` phát sinh thêm lỗi thiếu môi trường Python cục bộ (`.audit/stem-p6/exact-lock/venv/Scripts/python.exe`), hoàn toàn độc lập với code Spring Boot.
- **So sánh với con số của Lê:**
  - Lê ghi nhận: `BE 360 pass` ở P6 (chỉ chạy một suite chọn lọc 360 test unit/service khi chưa copy bridge SQL vào `db/migration`) và `422 pass / 17 skip` ở P3.
  - Con số **507 test pass** ở lần chạy tích hợp này khẳng định toàn bộ nghiệp vụ STEM, DTO, Validator, Service, Fingerprint P5, AI Grounding P6, và API Controllers đều hoạt động hoàn hảo và không bị hồi quy.

---

## 6. Các phát hiện và Đề xuất kỹ thuật cho Bridge SQL

1. **Cảnh báo Transaction Block trong V24 và V25:**
   - Dòng 4 trong `V24` và `V25` chứa lệnh: `SET LOCAL search_path = pg_catalog;`.
   - Khi chạy qua các công cụ không bọc transaction tường minh, PostgreSQL cảnh báo: `WARNING: SET LOCAL can only be used in transaction blocks`.
   - *Đề xuất:* Đảm bảo runner triển khai bắt buộc cờ `--single-transaction` hoặc bọc toàn bộ khối trong `BEGIN; ... COMMIT;`.
2. **Ràng buộc cứng Role `postgres`:**
   - `V23` và `V24` kiểm tra `IF current_user <> 'postgres' THEN RAISE EXCEPTION...`.
   - Nếu production Supabase hoặc RDS dùng role quản trị mang tên khác (ví dụ `supabase_admin`), migration sẽ bị chặn đứng dù user đó có toàn quyền.
   - *Đề xuất:* Xem chi tiết tại [PROPOSED_SQL_FIXES_RF10.md](file:///D:/CapstoneFall2026/STEMotion_BE/docs/stem/rf-10/PROPOSED_SQL_FIXES_RF10.md).
3. **Phân ly giữa Dev Flyway và Prod Bridge Flyway:**
   - Cần có migration chính thức để migrate schema dev cục bộ từ `VARCHAR(36)` sang `UUID` để đồng bộ hoàn toàn với production trước khi đưa V23–V25 thành migration mặc định của repository.

---

## 7. Rủi ro còn mở (Theo hồ sơ của Lê)

| Mã rủi ro | Trạng thái | Đánh giá & Khuyến nghị |
|---|---|---|
| **Conflict.C01** (Auth v2 writer & epoch) | **OPEN** | Snapshot và mã nguồn chưa chứng minh toàn bộ external/legacy writers đã ngừng ghi vào `public.users.role`. Cần rà soát và đóng băng mọi writer cũ trước giờ G. |
| **Conflict.C02** (Migration Engine & Ledger) | **OPEN** | Cần quyết định chính thức về runner chạy bridge SQL trên Production: chạy qua Flyway standalone CLI riêng hay kích hoạt qua Spring Boot startup, và phải bảo đảm V23–V25 chạy trong một single transaction atomic. |
| **Bridge.BR01** (Rollback & Restore Proof) | **OPEN (R3 BLOCKED)** | Chưa có bằng chứng thử nghiệm khôi phục dữ liệu (`Restore proof NOT_RECEIVED`). Cần hoàn thiện diễn tập rollback transaction abort hoặc point-in-time recovery trước khi đóng Gate 2 và mở Gate 3. |

---

## 8. Các bước Lê Trần và Owner cần Review trước R3 Rehearsal

1. **Review tính toàn vẹn của nhánh tích hợp:** Kiểm tra commit `5e66889` trên nhánh `integrate/stem-rf10-20261112`.
2. **Review 3 file bridge SQL trong `src/main/resources/db/migration/`:** Xác nhận mã băm SHA-256 và vị trí đặt file.
3. **Xác nhận cấu hình role thực thi:** Xác nhận user kết nối DB production trong buổi rehearsal có đúng là `postgres` hay là `supabase_admin`.
4. **Phê duyệt phương án chạy kiểm thử:** Quyết định giải pháp cô lập `V23–V25` khỏi test profile EmbeddedPostgres (hoặc nâng cấp engine test lên Testcontainers PostgreSQL 17 có snapshot) để đưa `mvn test` đạt 556/556 pass.
5. **Thực hiện Rehearsal trên Staging (R3):** Diễn tập rollback và forward-fix theo đúng `ROLLBACK_PLAN_R2.md`.

---

## 9. Hướng dẫn cho Owner

Nhánh tích hợp đã được commit cục bộ trên máy với commit message chuẩn chỉnh. **Chưa có bất kỳ lệnh push hay thay đổi nào trên remote/main.**

Khi Owner và nhóm sẵn sàng đưa lên remote để mở Pull Request review, chạy các lệnh sau từ terminal:

```bash
# 1. Đẩy nhánh tích hợp lên origin
git push origin integrate/stem-rf10-20261112

# 2. Tạo Draft PR vào main bằng GitHub CLI (gh)
gh pr create --base main --head integrate/stem-rf10-20261112 --title "draft: RF-10 STEM domain Flyway migration & isolated API integration" --body-file docs/stem/rf-10/RF10_INTEGRATION_REPORT.md --draft
```
