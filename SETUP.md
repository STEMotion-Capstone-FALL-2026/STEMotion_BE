# STEMotion Backend — Hướng dẫn chạy (Docker)

Tài liệu cho thành viên mới clone repo về và muốn chạy backend trên máy mình.

> **Không cần cài PostgreSQL.** Database chạy trong Docker. Nếu máy bạn đã có
> Postgres sẵn cũng không sao — container dùng port **5433** nên không đụng nhau.

---

## 1. Cần cài sẵn

| Phần mềm | Phiên bản | Kiểm tra |
|---|---|---|
| Docker Desktop | bất kỳ bản mới | `docker --version` |
| JDK | **17** trở lên | `java -version` |
| Maven | 3.9+ | `mvn -v` |

> Nếu `docker compose` báo `cannot find the path ... dockerDesktopLinuxEngine`
> nghĩa là Docker Desktop **chưa chạy**. Mở app Docker Desktop lên, đợi icon
> chuyển xanh rồi thử lại.

---

## 2. Chạy lần đầu (3 bước)

### Bước 1 — Tạo file `.env`

```bash
cp .env.example .env
```

Windows PowerShell:

```powershell
Copy-Item .env.example .env
```

Mở `.env` vừa tạo, **đổi `JWT_SECRET`** thành chuỗi bất kỳ của riêng bạn (tối
thiểu 32 ký tự). Các giá trị database cứ để nguyên — chúng khớp sẵn với Docker.

Muốn dùng tính năng AI thì tự lấy key riêng tại
[aistudio.google.com/apikey](https://aistudio.google.com/apikey) và điền vào
`GEMINI_API_KEY`. Để trống cũng được, app vẫn chạy bình thường, chỉ các endpoint
`/api/v1/ai/**` trả về lỗi 503.

> ⚠️ **Không bao giờ commit file `.env`** và không dán API key vào
> `application.yml`. File `.env` đã nằm trong `.gitignore`.

### Bước 2 — Bật database

```bash
docker compose up -d --wait
```

Cờ `--wait` khiến lệnh chỉ kết thúc khi Postgres thật sự sẵn sàng nhận kết nối,
nên bước 3 chạy ngay sau đó sẽ không bị lỗi "connection refused".

Kiểm tra:

```bash
docker compose ps
```

Phải thấy `stemotion-db   Up ... (healthy)`.

### Bước 3 — Chạy backend

```bash
mvn spring-boot:run
```

Lần chạy đầu tiên Flyway sẽ tự tạo toàn bộ 11 bảng. Trong log sẽ thấy:

```
Migrating schema "public" to version "1 - initial schema"
Successfully applied 1 migration to schema "public", now at version v1
Started StemotionApplication in ... seconds
```

Xong. API chạy ở `http://localhost:8080`.

---

## 3. Kiểm tra chạy được chưa

Mở Swagger UI: **http://localhost:8080/swagger-ui.html**

Hoặc test nhanh bằng dòng lệnh — tạo một tài khoản và lấy token:

```bash
curl -X POST http://localhost:8080/api/v1/auth/register -H "Content-Type: application/json" -d "{\"fullName\":\"Ten Ban\",\"email\":\"ban@stemotion.vn\",\"password\":\"password123\",\"role\":\"WRITER\"}"
```

Trả về `accessToken` là thành công.

---

## 4. Về dữ liệu (quan trọng)

### Schema sinh tự động, KHÔNG cần ai gửi file SQL

Cấu trúc bảng do **Flyway** quản lý, nằm trong
`src/main/resources/db/migration/`. Ai pull code về, chạy app lên là Flyway tự
tạo bảng khớp đúng với code. Không cần xin file dump của người khác để có schema.

`ddl-auto` đang để `validate`, nghĩa là Hibernate **chỉ kiểm tra** chứ không tự
sửa bảng. Nếu entity lệch với schema, app sẽ báo lỗi khi khởi động thay vì âm
thầm chạy sai.

### Muốn đổi cấu trúc bảng → viết migration mới

**Đừng sửa file `V1__initial_schema.sql`** đã có (Flyway kiểm tra checksum, sửa
là app không khởi động được nữa). Tạo file mới, đánh số tăng dần:

```
src/main/resources/db/migration/V2__them_cot_thumbnail.sql
```

```sql
ALTER TABLE clip_projects ADD COLUMN thumbnail_url VARCHAR(500);
```

> **Lưu ý riêng cho cột enum:** các cột enum có `CHECK` constraint liệt kê sẵn
> giá trị hợp lệ. Thêm giá trị mới vào enum trong Java thì **bắt buộc** phải
> viết migration cập nhật constraint, nếu không DB sẽ từ chối dữ liệu mới.
> Ví dụ khi thêm một loại scene:
>
> ```sql
> ALTER TABLE scenes DROP CONSTRAINT scenes_scene_type_check;
> ALTER TABLE scenes ADD CONSTRAINT scenes_scene_type_check CHECK (scene_type IN (
>   'TITLE_HERO', 'MATH_FORMULA', ..., 'LOAI_MOI'
> ));
> ```

### Dữ liệu là của riêng từng máy

Mỗi người có database riêng trong Docker volume trên máy mình. Dữ liệu bạn tạo
**không tự đồng bộ** sang máy người khác, và ngược lại. Đây là điều bình thường
khi dev — mỗi người tự tạo dữ liệu test của mình.

### Nếu thật sự cần copy dữ liệu giữa 2 máy

Máy nguồn xuất ra file:

```bash
docker exec stemotion-db pg_dump -U postgres -d stemotion_db --data-only --column-inserts --exclude-table=flyway_schema_history > stemotion_data.sql
```

> `--exclude-table=flyway_schema_history` là bắt buộc. Bảng đó là sổ ghi chép
> riêng của Flyway trên từng máy; nếu đem theo, lúc restore sẽ báo
> `duplicate key value violates unique constraint "flyway_schema_history_pk"`.

Gửi file `stemotion_data.sql` cho người kia. Máy đích nạp vào (chạy app ít nhất
một lần trước để Flyway tạo bảng đã):

```bash
docker exec -i stemotion-db psql -U postgres -d stemotion_db < stemotion_data.sql
```

Nếu máy đích đã có sẵn dữ liệu, xoá trước rồi nạp để tránh trùng khoá:

```bash
docker exec stemotion-db psql -U postgres -d stemotion_db -c "TRUNCATE users, workspaces, workspace_members, clip_projects, script_versions, scenes, feedback_comments, render_jobs, publish_records, status_histories, remotion_templates CASCADE;"
```

> Chỉ nên làm khi cần chia sẻ bộ dữ liệu mẫu. **Không commit file dump lên git**
> — trong đó có email và mật khẩu đã hash của tài khoản test.

---

## 5. Lệnh Docker hay dùng

```bash
docker compose up -d --wait
```

```bash
docker compose stop
```

```bash
docker compose logs -f postgres
```

Vào thẳng psql để xem dữ liệu:

```bash
docker exec -it stemotion-db psql -U postgres -d stemotion_db
```

Trong psql: `\dt` xem danh sách bảng, `\d scenes` xem cấu trúc bảng, `\q` để thoát.

### Xoá sạch, làm lại từ đầu

Khi database rối và muốn reset hoàn toàn:

```bash
docker compose down -v
```

> ⚠️ `-v` xoá luôn volume, **mất toàn bộ dữ liệu** trong database. Sau đó chạy
> lại `docker compose up -d --wait` và `mvn spring-boot:run`, Flyway sẽ dựng lại
> schema trống.

---

## 6. Lỗi thường gặp

| Lỗi | Nguyên nhân & cách xử lý |
|---|---|
| `failed to connect to the docker API` | Docker Desktop chưa chạy. Mở app lên, đợi icon xanh. |
| `Connection to localhost:5433 refused` | Container chưa lên. Chạy `docker compose up -d --wait`. |
| `password authentication failed` | File `.env` sai hoặc chưa tạo. Copy lại từ `.env.example`. |
| `port 5433 already allocated` | Có container cũ đang chiếm port. Chạy `docker compose down` rồi `docker ps` kiểm tra. |
| `Validate failed: schema ... is not up to date` | Code có entity mới mà thiếu migration. Viết file `V*.sql` tương ứng. |
| `Migration checksum mismatch` | Có người sửa file migration cũ. Khôi phục file đó về nguyên trạng. |
| `violates check constraint ..._check` | Thêm giá trị enum mà quên migration. Xem mục 4. |
| `JWT signature does not match` | `JWT_SECRET` vừa bị đổi. Đăng nhập lại để lấy token mới. |

---

## 7. Chạy full hệ thống (tuỳ chọn)

Backend chạy độc lập được. Muốn dùng thêm các phần khác:

**Frontend** (`STEMotion_FE`) — port 3000:

```bash
cp .env.example .env && npm install && npm run dev
```

Trong `.env` của FE, đổi `VITE_USE_MOCK=false` để gọi backend thật.

**Render service** (`STEMotion_RENDER`) — port 4000, cần cho tính năng xuất video MP4:

```bash
cp .env.example .env && npm install && npm run dev
```

Không chạy service này thì mọi thứ khác vẫn hoạt động, chỉ riêng nút render báo
lỗi `RENDER_ENQUEUE_FAILED`.

---

## 8. Chạy test

```bash
mvn test
```

54 test, không cần database — chạy được kể cả khi chưa bật Docker.
