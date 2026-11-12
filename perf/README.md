# Hướng dẫn Load Test Baseline (RF-02)

Thư mục này chứa toàn bộ công cụ, kịch bản k6, và dữ liệu mẫu để kiểm thử tải và thiết lập baseline hiệu năng cho các tiêu chí phi chức năng (NFR-01 đến NFR-04) của STEMotion Backend.

## 1. Công cụ sử dụng

- **k6 Docker (`grafana/k6:0.49.0`)**: Chạy k6 qua Docker với phiên bản cố định `0.49.0`. Không yêu cầu cài đặt Go hay npm package toàn cục, đảm bảo tính nhất quán trên mọi máy trạm và CI runner.
- **Embedded PostgreSQL + Mock Integrations (`LoadTestServer`)**: Khởi động backend trên cổng `18080` (lắng nghe `0.0.0.0`) sử dụng cơ sở dữ liệu nhúng PostgreSQL cô lập hoàn toàn, không phụ thuộc vào hạ tầng bên ngoài, không gọi API có phí (Gemini, VBEE, YouTube).

## 2. Dữ liệu cố định (Seed Data)

Dữ liệu kiểm thử được sinh tự động bởi `LoadTestSeeder` khi khởi động `LoadTestServer` hoặc chạy kiểm thử đơn vị `LoadTestSeederTest`:
- **Users**: 200 tài khoản hoạt động (`user001` đến `user200`), mật khẩu mặc định `LoadTest-fixture-123`.
- **Workspaces**: 3 workspace bộ môn (`Math Department`, `Physics Department`, `Chemistry Department`).
- **Phân quyền**: Tất cả 200 người dùng đều có tư cách thành viên hoạt động trên cả 3 workspace (Workspace 0 vai trò `WRITER`, Workspace 1 vai trò `REVIEWER`, Workspace 2 vai trò `PRODUCER`).
- **Projects**: 150 dự án clip (50 dự án/workspace) với trạng thái phân bổ cân bằng (`DRAFT`, `IN_REVIEW`, `APPROVED`).
- **Scenes**: 1,500 cảnh kịch bản (10 cảnh/dự án) bao gồm các loại cảnh mẫu STEM (`TITLE_HERO`, `MATH_FORMULA`, `DIAGRAM_EXPLAINER`, v.v.).

Để kiểm tra lại dữ liệu seed mà không cần chạy server:
```bash
mvn test -Dtest=LoadTestSeederTest
```

## 3. Khởi động Server Đo Tải

Chạy lệnh sau từ thư mục gốc của repository (`STEMotion_BE`):

```bash
E2E_LOADTEST=1 mvn test-compile spring-boot:test-run   # (PowerShell: $env:E2E_LOADTEST='1'; mvn test-compile spring-boot:test-run)
```

Server sẽ tự động:
1. Tạo database PostgreSQL nhúng sạch trên cổng ngẫu nhiên nội bộ.
2. Áp dụng toàn bộ Flyway migrations.
3. Nạp dữ liệu mẫu (200 users, 3 workspaces, 150 projects, 1500 scenes).
4. Lắng nghe HTTP request tại `http://0.0.0.0:18080/api/v1`.

Khi màn hình xuất hiện thông báo sau, server đã sẵn sàng nhận tải:
```text
[LoadTestServer] READY FOR K6 LOAD TESTING
[LoadTestServer] Target URL: http://host.docker.internal:18080/api/v1
```

## 4. Chạy các kịch bản k6

Các kịch bản nằm trong thư mục `perf/k6/`. Dùng Docker để chạy k6 kết nối tới máy host qua `host.docker.internal:18080`:

### NFR-03: Danh sách và Dashboard (200 VUs)
Kiểm thử 4 endpoint danh sách chính (`/workspaces/mine`, `/scripts/mine`, `/scripts/review-queue`, `/library/clips`).
- Ngưỡng đạt: `p(95) < 3000ms`, tỷ lệ lỗi `< 1%`.
- Thời lượng tiêu chuẩn: 5 phút.

```bash
docker run --rm -i --add-host=host.docker.internal:host-gateway \
  -v "${PWD}/perf/k6:/scripts" \
  -e BASE_URL=http://host.docker.internal:18080/api/v1 \
  -e DURATION=5m \
  grafana/k6:0.49.0 run /scripts/nfr03-lists.js
```

### NFR-01: Phân tích Kịch bản bằng AI (Embedded Mode)
Kiểm thử endpoint phân tích kịch bản bằng AI (`POST /api/v1/ai/script/action` với hành động `terms` trên văn bản STEM).
- Ngưỡng đạt: `p(95) < 15000ms`, tỷ lệ lỗi `< 1%`.
- Lưu ý: Chạy ở chế độ `embedded` mô phỏng phản hồi AI nội bộ để đo chính xác chi phí xử lý (overhead) của backend.

```bash
docker run --rm -i --add-host=host.docker.internal:host-gateway \
  -v "${PWD}/perf/k6:/scripts" \
  -e BASE_URL=http://host.docker.internal:18080/api/v1 \
  -e DURATION=3m \
  grafana/k6:0.49.0 run /scripts/nfr01-ai.js
```

### NFR-04: Luồng Người dùng Web Đồng thời (200 Concurrent Users)
Mô phỏng 200 người dùng đồng thời thực hiện luồng làm việc thực tế: Đăng nhập -> Mở danh sách Workspace -> Mở danh sách Kịch bản -> Xem chi tiết Kịch bản.
- Ngưỡng đạt: `p(95) < 3000ms`, tỷ lệ lỗi `< 1%`.
- Thời lượng tiêu chuẩn: 10 phút.

```bash
docker run --rm -i --add-host=host.docker.internal:host-gateway \
  -v "${PWD}/perf/k6:/scripts" \
  -e BASE_URL=http://host.docker.internal:18080/api/v1 \
  -e DURATION=10m \
  grafana/k6:0.49.0 run /scripts/nfr04-web-users.js
```

## 5. Cách đọc kết quả k6

Khi k6 hoàn thành, bảng tóm tắt sẽ hiển thị:
- `http_req_duration`: Thống kê thời gian phản hồi gồm `avg`, `min`, `med` (p50), `max`, `p(90)`, `p(95)`, `p(99)`.
- `http_req_failed`: Tỷ lệ request thất bại (HTTP status >= 400).
- `thresholds`: Dấu check xanh nếu tất cả ngưỡng đều thỏa mãn (`[✓] p(95) < 3000ms`, `[✓] rate < 0.01`).
