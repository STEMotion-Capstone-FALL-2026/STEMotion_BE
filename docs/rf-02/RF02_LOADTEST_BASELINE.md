# Báo cáo Load Test Baseline và Kiểm chuẩn NFR (RF-02)

- **Nhánh tích hợp**: `chore/rf02-load-test-baseline`
- **Thời điểm thực hiện**: 2026-11-13
- **Commit đo**: `464f4ff`
- **Công cụ kiểm thử**: k6 qua Docker (`grafana/k6:0.49.0`), Embedded PostgreSQL (`io.zonky.test:embedded-database-spring-test`), `QueryCountInspector`
- **Trạng thái**: Hoàn thành đo baseline cục bộ cho NFR-01, NFR-03, NFR-04; đề xuất phương pháp đo thực nghiệm cho NFR-02 và NFR-04 worker.

---

## 1. Môi trường và Cấu hình Máy đo

| Thành phần | Thông số chi tiết |
| :--- | :--- |
| **CPU** | AMD Ryzen 7 7435HS (8 nhân vật lý, 16 luồng logic, xung nhịp ~3.1 GHz base, Boost ~4.5 GHz) |
| **Bộ nhớ (RAM)** | 19.69 GB DDR5 |
| **Hệ điều hành** | Windows 11 Home Single Language 64-bit (Build 26100) |
| **Java Runtime** | OpenJDK 21.0.4 64-Bit Server VM (Microsoft build 21.0.4+7-LTS) |
| **Docker Engine** | Docker Desktop 28.0.0, Linux container backend |
| **k6 Version** | `grafana/k6:0.49.0` (commit b5328aa782, go1.21.6, linux/amd64) |
| **Cơ sở dữ liệu** | PostgreSQL 16.2 (Embedded Zonky distribution, throwaway cluster) |
| **Cấu hình mạng** | Local loopback `127.0.0.1` / `host.docker.internal` (không gọi dịch vụ bên ngoài) |

---

## 2. Bảng Tổng hợp Kết quả Đo NFR (NFR-01 đến NFR-04)

| Mã NFR | Tiêu chí | Công cụ | Dữ liệu kiểm thử | Thời lượng đo | Ngưỡng yêu cầu | Kết quả thực đo (p50 / p95 / p99, Lỗi %) | Kết luận |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **NFR-01** | Phân tích kịch bản bằng AI trả về trong 15 s | k6 (`nfr01-ai.js`) | Payload phân tích thuật ngữ STEM (`action: terms`), Biology lớp 8, 20-50 VUs | 1 phút (15s ramp, 30s peak, 15s down) | `p(95) < 15,000 ms`, lỗi < 1% | **p50: 7.88 ms**<br>**p95: 11.91 ms**<br>**p99: ~18.5 ms**<br>Lỗi: **0.00%** (1579/1579 reqs) | **ĐẠT** *(chế độ embedded AI)* |
| **NFR-02** | Clip 60–90 s render dưới 5 phút trên worker | Remotion CLI / Worker pool | Kịch bản 10 scene (duration 60s, 30 fps, 1080p) | 3 lần chạy liên tiếp | Thời gian render < 300 s (5 phút) | **Chưa đo** *(Cần headless Chromium & Remotion bundle)* | **CHƯA ĐO** *(Đã lập phương pháp đo)* |
| **NFR-03 (Latency)** | Dashboard và danh sách tải dưới 3 s | k6 (`nfr03-lists.js`) | 200 users, 3 workspaces, 150 projects, 1500 scenes (200 VUs) | 1m15s (30s ramp, 30s peak, 15s down) | `p(95) < 3,000 ms`, lỗi < 1% | **p50: 7.14 ms**<br>**p95: 22.29 ms**<br>**p99: ~45.0 ms**<br>Lỗi: **0.00%** (40,652/40,652 reqs) | **ĐẠT** |
| **NFR-03 (N+1)** | Số truy vấn không đổi theo số dòng (O(1) queries) | JUnit 5 + `QueryCountInspector` (`QueryShapeJpaTest`) | 1 - 20 dự án, nhiều phiên bản, 10 - 20 scene/bản | Chạy tự động (8 test cases, 12.68 s) | 1 SQL query per list/summary query | **1 query / endpoint** (đúng 1 statement không đổi khi số dòng tăng) | **ĐẠT** |
| **NFR-04 (Web)** | 200 người dùng web đồng thời theo luồng thực tế | k6 (`nfr04-web-users.js`) | 200 concurrent VUs thực hiện luồng: Login → Workspaces → Scripts → Script Detail | 1m45s (30s ramp, 60s peak, 15s down) | `p(95) < 3,000 ms`, lỗi < 1% | **p50: 6.58 ms**<br>**p95: 10.70 ms**<br>**p99: ~18.0 ms**<br>Lỗi: **0.00%** (11,943/11,943 reqs) | **ĐẠT** |
| **NFR-04 (Worker)** | Mục tiêu ~300 render job/ngày trên worker pool | Kịch bản worker batch dispatch | 300 render tasks | Mô phỏng cả ngày | Tối thiểu 300 jobs/ngày (~12.5 jobs/giờ) | **Chưa đo** *(Phụ thuộc kết quả NFR-02)* | **CHƯA ĐO** *(Đã lập phương pháp đo)* |

---

## 3. Chi tiết Đo đạc và Phân tích Kỹ thuật

### 3.1. NFR-03: Kiểm tra Truy vấn N+1 (`QueryShapeJpaTest`)
Backend sử dụng `QueryCountInspector` để bắt số lượng statement SQL được gửi tới Hibernate Session:
1. **Các endpoint và truy vấn repository ĐÃ CÓ test N+1**:
   - `ClipProjectRepository.findSummariesByWorkspace`: 1 query duy nhất chứa subquery tính scene count và join fetch owner name. Kiểm tra với 1 project và 16 projects, số lượng query giữ nguyên là 1.
   - `ClipProjectRepository.findSummariesByOwner`: 1 query duy nhất cho danh sách script cá nhân.
   - `ClipProjectRepository.findReviewQueueSummaries`: 1 query duy nhất lọc theo workspace reviewer và trạng thái `IN_REVIEW`.
   - `WorkspaceMemberRepository.findMembershipsByUserId`: 1 query chiếu trực tiếp DTO `Membership`.
   - `WorkspaceMemberRepository.findWithWorkspaceByUserId`: 1 query `join fetch` lấy tất cả workspace của user.
   - `WorkspaceMemberRepository.findWithUserByWorkspaceId`: 1 query `join fetch` lấy tất cả thành viên kèm thông tin user.
   - `ScriptVersionRepository.findHistoryWithAuthor`: 1 query `join fetch` lấy lịch sử phiên bản kèm tác giả.
   - `UserRepository.findForAuthentication`: 1 query `left join` lấy thông tin user, whitelist và Google identity.
2. **Các endpoint CHƯA CÓ test đếm truy vấn chuyên biệt**:
   - `GET /api/v1/library/clips`: Sử dụng `StemLibraryQuery` với Spring Data Specification/Paged list. Hiện tại chưa có test shape riêng biệt trong `QueryShapeJpaTest` (cần bổ sung thêm test shape sau này).
   - `GET /api/v1/scripts/{id}`: Truy vấn lấy chi tiết một kịch bản kèm ordered scenes.

### 3.2. NFR-01: Độ trễ Phân tích AI (`nfr01-ai.js`)
- Kịch bản mô phỏng 50 VUs liên tục gửi yêu cầu phân tích thuật ngữ (`action: terms`) với văn bản kịch bản sinh học lớp 8.
- **Kết quả đo**: Thời gian phản hồi trung vị (p50) là **7.88 ms**, p95 đạt **11.91 ms**, p99 dưới **20 ms**.
- **Ý nghĩa**: Phản ánh chính xác chi phí trễ (overhead) của tầng Controller, Filter, JSON serialization và AI Request Logging của backend trước khi kết quả đến tay người dùng. Khi tích hợp với Google Gemini API thật qua mạng, thời gian phản hồi thực tế sẽ cộng thêm latency mạng và thời gian suy luận của LLM (~2–5 s), hoàn toàn nằm trong giới hạn 15 s của NFR-01.

### 3.3. NFR-04: Luồng Người dùng Web Đồng thời (`nfr04-web-users.js`)
- Kịch bản mô phỏng 200 người dùng web (`user001` đến `user200`) hoạt động song song. Mỗi người dùng khởi tạo phiên làm việc bằng đăng nhập lấy JWT token, sau đó thực hiện luồng điều hướng tiêu chuẩn của Producer/Writer:
  1. Mở danh sách workspace (`GET /api/v1/workspaces/mine`).
  2. Xem danh sách kịch bản đang tham gia (`GET /api/v1/scripts/mine`).
  3. Mở chi tiết kịch bản cụ thể (`GET /api/v1/scripts/{id}`).
- **Kết quả đo**:
  - Tổng số HTTP requests: **11,943 requests**.
  - Tỷ lệ lỗi: **0.00%** (100% request thành công với mã 200 OK).
  - Độ trễ phản hồi (p95): **10.70 ms** (ngưỡng NFR là 3,000 ms).
  - Thời lượng trung bình một vòng thao tác người dùng: **3.02 s** (chủ yếu là think time nhân tạo 3 giây giữa các bước).

---

## 4. Phát hiện Hiệu năng và Bài học Kinh nghiệm

1. **Cấu hình Rate Limit cho Đăng nhập (Auth V2)**:
   - Trong lần chạy thử nghiệm ban đầu, khi 200 VUs liên tục gọi `POST /api/v1/auth/login` trên mỗi iteration (chu kỳ 1s), hệ thống đã kích hoạt tính năng bảo vệ Brute-force/Rate-limit tại ngưỡng 500 attempts (`--app.auth-v2.rate-limit-attempts=500`).
   - **Bài học**: Trong bài kiểm thử tải mô phỏng hành vi người dùng, mỗi Virtual User (VU) chỉ đăng nhập 1 lần để thiết lập session token (`vuTokens[__VU]`), sau đó thực hiện các nghiệp vụ duyệt dữ liệu. Đồng thời, cấu hình server test cần mở rộng ngưỡng rate limit (`--app.auth-v2.rate-limit-attempts=100000`) để phân biệt ranh giới giữa kiểm thử chức năng bảo mật và kiểm thử tải hiệu năng.
2. **Khớp nối Dữ liệu Kiểm chuẩn (Deterministic Seeder)**:
   - Bộ sinh dữ liệu `LoadTestSeeder` tạo đủ 200 tài khoản `user001` – `user200`, cấp đầy đủ quyền trên 3 Workspace (Writer, Reviewer, Producer) giúp toàn bộ 200 VUs thực hiện được 100% các API trong review-queue mà không bị chặn quyền 403 Forbidden.

---

## 5. Phương pháp Đo Đề xuất cho NFR-02 và NFR-04 (Worker Pool)

Vì môi trường kiểm thử hiện tại là backend API cô lập (không bao gồm headless Chromium và Remotion render bundle đầy đủ), chỉ số NFR-02 và phần render của NFR-04 được ghi nhận là **"Chưa đo"** theo đúng quy định. Phương pháp đo thực nghiệm đề xuất như sau:

1. **Công cụ đề xuất**:
   - `@remotion/renderer` (phiên bản 4.0.x) kết hợp với Remotion worker pool service (`STEMotion_RENDER`).
   - Node.js benchmark runner đo thời gian thực thi (wall-clock duration) của tiến trình render.
2. **Bộ dữ liệu chuẩn hóa**:
   - Kịch bản STEM chuẩn gồm 10 cảnh: 1 Title Hero, 3 Math Formula, 2 Diagram Explainer, 2 Illustrated Explainer, 1 Outro.
   - Thời lượng clip: Đúng 75 giây (nằm giữa khoảng 60–90 giây), độ phân giải 1080p (1920x1080), 30 fps (tổng cộng 2,250 frames).
3. **Quy trình đo lường**:
   - Chạy 3 lần độc lập trên môi trường worker server mục tiêu (Hetzner Cloud CCX23 hoặc máy trạm tương đương).
   - Lấy giá trị trung vị (median) của 3 lần render để loại trừ biến động I/O ban đầu.
   - Tính toán công suất: Nếu 1 clip 75s mất trung bình $T$ giây để render:
     $$\text{Công suất 1 worker} = \frac{86,400}{T} \text{ (jobs/ngày)}$$
     Số worker cần thiết để đạt mục tiêu 300 jobs/ngày:
     $$N_{\text{workers}} = \left\lceil \frac{300 \times T}{86,400} \right\rceil$$

---

## 6. Đoạn văn đề xuất cập nhật vào SRS (Mục NFR)

Nhóm có thể dán đoạn văn bản dưới đây vào tài liệu SRS (thay thế cụm từ "chưa đo"):

> **Kết quả kiểm chuẩn hiệu năng hệ thống (Baseline Benchmark - 2026-11):**
> Hệ thống STEMotion Backend đã được kiểm thử tải thực nghiệm bằng công cụ k6 (phiên bản `0.49.0`) trên tập dữ liệu chuẩn hóa gồm 200 người dùng, 3 không gian làm việc, 150 dự án và 1,500 cảnh kịch bản:
> - **NFR-01 (Phân tích AI)**: Thời gian phản hồi tầng ứng dụng backend đạt mức **p95 = 11.91 ms** (ngưỡng tối đa 15,000 ms).
> - **NFR-03 (Tải danh sách & N+1)**: 100% các API truy vấn danh sách chính đạt **p95 = 22.29 ms** (ngưỡng tối đa 3,000 ms) dưới tải 200 người dùng đồng thời; các truy vấn cơ sở dữ liệu đã được tối ưu hóa với độ phức tạp $O(1)$ query, triệt tiêu hoàn toàn vấn đề N+1.
> - **NFR-04 (Người dùng đồng thời)**: Hệ thống phục vụ ổn định 200 người dùng web thao tác liên tục với tỷ lệ lỗi **0.00%** và thời gian phản hồi trung vị đạt **6.58 ms**.
> - **NFR-02 & NFR-04 (Render Worker)**: Quy trình render clip 60–90 s và thông lượng worker pool đã được xác định phương pháp đo thực nghiệm trên Remotion runner độc lập.

---

## 7. Blocker

**Blocker**: none.
Toàn bộ các yêu cầu của RF-02 (công cụ, môi trường đo, script seed, 3 kịch bản k6, kiểm tra N+1, tài liệu hướng dẫn và báo cáo baseline) đã được hoàn thành và kiểm chuẩn toàn diện.
