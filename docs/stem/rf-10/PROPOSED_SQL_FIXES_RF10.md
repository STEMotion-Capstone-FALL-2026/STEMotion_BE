# ĐỀ XUẤT HIỆU CHỈNH KỸ THUẬT CHO BRIDGE SQL (RF-10)

Tài liệu này ghi nhận các phân tích lỗi và đề xuất bản sửa kỹ thuật cho 3 file bridge SQL của Lê Trần (`V23`, `V24`, `V25`). 
Nội dung 3 file gốc trong `src/main/resources/db/migration/` được **giữ nguyên 100% không chỉnh sửa** theo đúng quy định.

---

## 1. Vấn đề 1: Cảnh báo `SET LOCAL search_path` ngoài Transaction Block

### Hiện tượng:
Trong `V24__create_stem_domain.sql` và `V25__create_stem_artifact_lineage.sql`, câu lệnh:
```sql
SET LOCAL search_path = pg_catalog;
```
khi chạy qua một số runner tự động (không bật transaction tường minh) sẽ phát sinh cảnh báo:
```text
WARNING: SET LOCAL can only be used in transaction blocks
```
Đồng thời, thiết lập `search_path` sẽ không có hiệu lực bảo vệ phạm vi tìm kiếm của các câu lệnh tiếp theo trong session đó.

### Đề xuất:
1. Khi chạy thủ công hoặc diễn tập R3 qua CLI, bắt buộc bổ sung cờ `--single-transaction` (với `psql` là `-1` hoặc `--single-transaction`).
2. Hoặc trong script migration, bọc toàn bộ nội dung trong khối transaction:
   ```sql
   BEGIN;
   SET LOCAL search_path = pg_catalog;
   ...
   COMMIT;
   ```

---

## 2. Vấn đề 2: Ràng buộc cố định role `postgres`

### Hiện tượng:
Trong `V23__assert_stem_bridge_prerequisites.sql` và `V24__create_stem_domain.sql`:
```sql
IF current_user <> 'postgres' THEN
    RAISE EXCEPTION 'Bridge owner differs from the reviewed postgres candidate';
END IF;
```
Trên các hệ quản trị đám mây (ví dụ Supabase Managed Cloud, AWS RDS, GCP Cloud SQL), role quản trị thực tế kết nối có thể là `supabase_admin`, `cloudsqladmin`, hoặc một role được chỉ định trong CI/CD. Việc so sánh tuyệt đối với chuỗi `'postgres'` sẽ làm gián đoạn tiến trình dù user kết nối có đầy đủ quyền hạn (SUPERUSER hoặc BYPASSRLS).

### Đề xuất hiệu chỉnh linh hoạt:
Cho phép kiểm tra quyền thay vì tên người dùng cố định:
```sql
IF current_user <> 'postgres' AND NOT EXISTS (
    SELECT 1 FROM pg_roles WHERE rolname = current_user AND (rolsuper OR rolbypassrls)
) THEN
    RAISE EXCEPTION 'Bridge owner (%) lacks superuser or bypassrls privilege', current_user;
END IF;
```

---

## 3. Vấn đề 3: Phân ly kiểu dữ liệu giữa Schema Dev/Test (`VARCHAR(36)`) và Prod (`UUID`)

### Hiện tượng:
- File `V1__initial_schema.sql` khởi tạo bảng `script_versions`, `scenes`, `workspaces`, `users` với khóa chính kiểu `VARCHAR(36)`.
- Suốt từ `V1` đến `V22`, không có migration Flyway nào chuyển đổi các cột này sang `UUID`.
- Trên Production, Auth v2 và hệ thống bảng core đã được cutover sang `UUID` thông qua ledger Supabase.
- Vì vậy, `V24` khai báo:
  ```sql
  version_id uuid NOT NULL UNIQUE REFERENCES public.script_versions(id)
  ```
  sẽ báo lỗi incompatible types khi chạy trên bất kỳ môi trường Dev/Test/CI nào khởi tạo từ Flyway V1.

### Đề xuất giải pháp dài hạn:
Bổ sung một migration chuẩn hóa (ví dụ `V22_1__convert_core_ids_to_uuid.sql`) dành cho môi trường Dev/Staging để đồng bộ hoàn toàn kiểu dữ liệu với Production trước khi đưa dự án vào pha kiểm thử tích hợp cuối cùng (R4/R5).
