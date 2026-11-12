# STEM Bridge Rehearsal (R3 Simulation)

Thư mục này chứa script và schema phục vụ diễn tập cục bộ bộ migration bridge `V23`–`V26` (`src/main/resources/db/bridge-prod/`) cho Lê Trần thực hiện Gate R3.

> [!WARNING]
> **Đây là môi trường mô phỏng cục bộ trên Docker.**
> Script này **KHÔNG thay thế** cho bước diễn tập R3 thực tế trên bản sao schema-only được dump từ Production (`pg_dump --schema-only`).
> **Tuyệt đối không** chạy script này hoặc kết nối tới cơ sở dữ liệu Supabase hay bất kỳ DB production nào.

## Thành phần

1. `prod-sim-schema.sql`:
   - Dựng cấu trúc mô phỏng cơ sở dữ liệu Production trước khi áp STEM.
   - Ledger Supabase: `supabase_migrations.schema_migrations` với migration `20261102131936`.
   - Ledger Flyway: `public.flyway_schema_history` với Rank 1 là BASELINE và 21 checksum V2–V22 khớp tuyệt đối với mã nguồn.
   - 10 bảng cha core có khóa chính `id uuid`: `users`, `workspaces`, `clip_projects`, `script_versions`, `scenes`, `status_histories`, `render_jobs`, `compositions`, `clips`, `publications`.
   - Cột Auth v2 trên bảng `users` (`username`, `system_role`, `auth_version`, `version`, `role`, check constraint `users_system_role_check`) và 6 bảng Auth liên quan.
   - Hoàn toàn không có dữ liệu người dùng thật.

2. `rehearse.sh`:
   - Khởi tạo container Docker `postgres:17` tạm thời trên cổng cục bộ `15433`.
   - Chờ PostgreSQL sẵn sàng và nạp `prod-sim-schema.sql`.
   - Áp dụng tuần tự 4 migration bridge với cờ `--single-transaction -v ON_ERROR_STOP=1`:
     - `V23__assert_stem_bridge_prerequisites.sql`
     - `V24__create_stem_domain.sql`
     - `V25__create_stem_artifact_lineage.sql`
     - `V26__allow_stem_strict_mode.sql`
   - Kiểm tra và in kết quả `PASS`/`FAIL` cho:
     1. Đủ 11 bảng STEM (`stem_catalog`, `stem_briefs`, `stem_evidence`, `stem_settings`, `stem_assessments`, `stem_validation_runs`, `stem_validation_limits`, `stem_receipts`, `stem_audit`, `stem_p5_snapshots`, `stem_p5_compatibility`).
     2. Đủ 4 trigger append-only bảo vệ tính bất biến.
     3. Ràng buộc `stem_settings_mode_check` chấp nhận giá trị `'STRICT'`.
     4. Ràng buộc `stem_settings_mode_check` từ chối các giá trị không hợp lệ.
     5. Kiểm tra tính idempotent/rollback: Chạy lại `V23` lần 2 sẽ văng lỗi và rollback nguyên trạng.
   - Tự động hủy và dọn dẹp container khi kết thúc.

## Yêu cầu

- Docker Engine / Docker Desktop (chạy image `postgres:17`).
- Client CLI `psql` (tương thích PostgreSQL 17).
- Bash shell (Linux/macOS hoặc Git Bash trên Windows).

## Cách chạy

```bash
cd D:/CapstoneFall2026/STEMotion_BE
bash deploy/stem-bridge-rehearsal/rehearse.sh
```

## Lưu ý bảo mật

- Script chỉ giao tiếp với container Docker cục bộ trên cổng `15433` do chính nó tạo ra.
- Không nhận tham số host từ ngoài; không đọc biến môi trường kết nối DB ngoài.
- Tuyệt đối không chứa chuỗi kết nối hay secret thật.
