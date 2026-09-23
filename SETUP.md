# STEMotion Backend — Hướng dẫn chạy (Docker)

Tài liệu cho thành viên mới clone repo về và muốn chạy backend trên máy mình.

> **Toàn bộ hệ thống chạy bằng Docker (PostgreSQL, MinIO, Backend Spring Boot).**
> Bạn không cần cài JDK, Maven hay PostgreSQL trên máy nếu chạy qua Docker.

---

## 1. Cần cài sẵn

| Phần mềm | Phiên bản | Kiểm tra |
|---|---|---|
| Docker Desktop | bất kỳ bản mới | `docker --version` |

> Nếu `docker compose` báo `cannot find the path ... dockerDesktopLinuxEngine`
> nghĩa là Docker Desktop **chưa chạy**. Mở app Docker Desktop lên, đợi icon
> chuyển xanh rồi thử lại.

---

## 2. Cách 1: Chạy toàn bộ bằng Docker (Khuyên dùng - 1 lệnh duy nhất)

### Bước 1 — Tạo file `.env`

Windows PowerShell:
```powershell
Copy-Item .env.example .env
```

Linux / macOS:
```bash
cp .env.example .env
```

Mở `.env` vừa tạo:
- **Đổi `JWT_SECRET`** thành chuỗi bất kỳ của riêng bạn (tối thiểu 32 ký tự).
- Điền `GEMINI_API_KEY` nếu muốn dùng tính năng AI (lấy tại [aistudio.google.com/apikey](https://aistudio.google.com/apikey)). Để trống thì app vẫn chạy bình thường, chỉ các API AI trả 503.

### Bước 2 — Khởi động toàn bộ (Database + Storage + Backend)

```bash
docker compose up -d --build
```

Lệnh này sẽ tự động:
1. Tạo và khởi động PostgreSQL Database (`stemotion-db` trên port 5433).
2. Tạo và khởi động MinIO Object Storage (`stemotion-minio` trên port 9000/9001).
3. Build và khởi động Backend Spring Boot (`stemotion-backend` trên port 8080).
4. Flyway sẽ tự động chạy migration tạo toàn bộ 11 bảng trong Database.

Kiểm tra trạng thái:
```bash
docker compose ps
```

Xem log Backend:
```bash
docker compose logs -f backend
```

---

## 3. Cách 2: Chạy Backend trực tiếp trên máy (Dành cho dev Java/IDE)

Nếu bạn muốn debug trực tiếp qua IntelliJ IDEA hoặc VSCode:

1. **Bật Database & MinIO qua Docker:**
   ```bash
   docker compose up -d postgres minio
   ```
2. **Chạy Backend bằng Maven:**
   ```bash
   mvn spring-boot:run
   ```
   *(hoặc trên Windows: `.\mvnw.cmd spring-boot:run`)*

---

## 4. Kiểm tra chạy được chưa

Mở Swagger UI trên trình duyệt: **http://localhost:8080/swagger-ui.html**

Hoặc test nhanh tạo tài khoản test qua terminal:
```bash
curl -X POST http://localhost:8080/api/v1/auth/register -H "Content-Type: application/json" -d "{\"fullName\":\"Ten Ban\",\"email\":\"ban@stemotion.vn\",\"password\":\"password123\",\"role\":\"WRITER\"}"
```
Trực tiếp trả về `accessToken` là thành công.

---

## 5. Về dữ liệu (quan trọng)

### Schema sinh tự động, KHÔNG cần ai gửi file SQL

Cấu trúc bảng do **Flyway** quản lý, nằm trong `src/main/resources/db/migration/`. Khi khởi động app, Flyway tự tạo bảng khớp đúng với code.

### Dữ liệu là của riêng từng máy

Mỗi người có database riêng trong Docker volume (`stemotion_pgdata`).

---

## 6. Các lệnh Docker hay dùng

| Nhu cầu | Lệnh |
|---|---|
| Bật toàn bộ | `docker compose up -d` (thêm `--build` nếu có sửa code Java) |
| Tắt toàn bộ | `docker compose stop` |
| Xem log backend | `docker compose logs -f backend` |
| Xoá sạch làm lại từ đầu | `docker compose down -v` |
| Vào psql xem DB | `docker exec -it stemotion-db psql -U postgres -d stemotion_db` |

---

## 7. Chạy full hệ thống FE + RENDER (Tuỳ chọn)

- **Frontend** (`STEMotion_FE`) — port 3000:
  ```bash
  cd ../STEMotion_FE && npm install && npm run dev
  ```
- **Render service** (`STEMotion_RENDER`) — port 4000:
  ```bash
  cd ../STEMotion_RENDER && npm install && npm run dev
  ```
