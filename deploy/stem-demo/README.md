# STEM API Smoke Test Script (`smoke-stem.py`)

Kịch bản kiểm thử luồng STEM end-to-end qua HTTP REST API (chỉ dùng thư viện chuẩn Python 3, không phụ thuộc thư viện ngoài).

## 1. Biến môi trường cần đặt

Trước khi chạy, cần đặt thông tin đăng nhập của 4 vai trò qua các biến môi trường sau (script **không** có mật khẩu mặc định và **không** in mật khẩu):

```bash
export STEM_SMOKE_ADMIN_USER="admin"
export STEM_SMOKE_ADMIN_PASS="<mat_khau_admin>"

export STEM_SMOKE_WRITER_USER="writer"
export STEM_SMOKE_WRITER_PASS="<mat_khau_writer>"

export STEM_SMOKE_REVIEWER_USER="reviewer"
export STEM_SMOKE_REVIEWER_PASS="<mat_khau_reviewer>"

export STEM_SMOKE_PRODUCER_USER="producer"
export STEM_SMOKE_PRODUCER_PASS="<mat_khau_producer>"
```

## 2. Lệnh chạy

Yêu cầu cờ bắt buộc `--i-understand-this-writes-demo-data` để xác nhận việc ghi dữ liệu demo vào workspace chỉ định.

```bash
python deploy/stem-demo/smoke-stem.py \
  --base-url "http://127.0.0.1:18080" \
  --workspace "<workspace_id>" \
  --level "PRIMARY" \
  --i-understand-this-writes-demo-data
```

Các cấp độ hỗ trợ cho tham số `--level`:
- `PRIMARY` (Tiểu học - Lớp 4)
- `LOWER_SECONDARY` (THCS - Lớp 8)
- `UPPER_SECONDARY` (THPT - Lớp 11)

## 3. Dữ liệu script ghi vào hệ thống

Script chỉ tác động lên workspace demo được chỉ định qua tham số `--workspace`:
1. Nạp gói catalog demo (`stem-demo-pack.v1`) qua API `POST /api/v1/stem/catalog/import` (idempotent, không trùng lặp).
2. Đổi chế độ STEM của workspace demo sang `STRICT` với chính sách (policy) tương ứng cấp học đã chọn.
3. Tạo dự án clip demo và script version trong workspace demo.
4. Ghi nhận đánh giá rubric và phê duyệt kịch bản với fingerprint tương ứng.
5. Tạo 2 dự án phụ để kiểm thử các tình huống lỗi:
   - Brief không hợp lệ bị chặn khi nộp duyệt (`400 STEM_VALIDATION_FAILED`).
   - Phê duyệt với fingerprint cũ/sai lệch bị từ chối (`409 STEM_FINGERPRINT_STALE`).
