# Kế hoạch sửa hiệu năng DB — 19/10/2026

Dựa trên báo cáo DB audit của LeTran (SHA `74a1bf2`) và lượt kiểm tra chỉ đọc trên Supabase prod ngày 19/10 (V20; 20 project, 24 version, 101 scene, 22 membership; ~10/60 kết nối; `pg_stat_statements` 1.11).

## Trạng thái (20/10/2026)

| Bước | Trạng thái | Commit |
|---|---|---|
| 1. Đếm query mỗi request (log `api METHOD PATH STATUS ms q=N`) | ✅ Xong | `0b8904b` |
| 2.1 Phân quyền đọc user / membership / project một lần mỗi request | ✅ Xong | `0b8904b` |
| 2.2 Danh sách project, `mine`, review-queue = 1 query | ✅ Xong | `0b8904b` |
| 2.3–2.4 Danh sách tổ (đếm gộp, sửa `getOrDefault`), members `join fetch` | ✅ Xong | `0b8904b` |
| 2.5–2.12 Review-history, thư viện, comments, versions, render history, admin, templates, theme | ✅ Xong | `11ce7b4` |
| 2.13 Transaction | ✅ Xong: rà mọi service; sửa `requestChanges` bị mất `@Transactional` (ghi nhiều bảng rời rạc); `WriteTransactionsTest` chặn method ghi thiếu transaction | `2015cac` |
| Test số query (H2, `QueryShapeJpaTest`): mọi `@Query` khởi động được, kết quả đúng phạm vi, số statement không đổi khi số dòng tăng | ✅ Xong | `11ce7b4` |
| 3. V21 index | ✅ Đã chạy lên DB prod 20/10/2026 (V20 → V21, 5,5 s, Hibernate validate OK); 13/13 index có mặt | `dc66814` |
| 4. Phân trang (BE) | ✅ Xong, tương thích ngược; FE dùng dần theo `FE_ACCOMPLISHMENTS_TODO.md` mục 9.6 | xem log |
| Follow-up (23/10): comments phân trang note gốc trong SQL + reply theo lô; thư viện lọc môn/khối + phân trang trong SQL, câu đếm loại publication không có clip; bản xuất bản mới nhất 1 dòng/project (trùng giờ → id lớn hơn); bộ lọc thư viện bằng `DISTINCT` | ✅ Xong | xem log |
| 6. Hạ tầng (cùng region, gói Render) | Chờ team quyết định | — |

## 0. Tóm tắt

- **Nguyên nhân chính: số round trip DB quá lớn**, mỗi round trip đi Render Singapore ↔ Supabase Sydney. SQL bên trong Postgres chỉ 0,04–0,35 ms/câu.
- Bằng chứng (`pg_stat_statements`, cộng dồn): `workspace_members` 2800 lần, `clip_projects` 2639, `script_versions` 1910, `scenes` 1577, hai câu `COUNT` theo tổ 884/884.
- `spring.jpa.open-in-view: false` ⇒ mỗi method `@Transactional` / mỗi lần gọi repository ngoài transaction là **một transaction riêng** (query + COMMIT), không dùng chung persistence context. Kiểm tra quyền lặp lại vì thế luôn chạm DB.
- **Thiếu 11 index của V1** trên prod (prod được Flyway baseline ở V1 nên file V1 chưa từng chạy). Dữ liệu còn nhỏ nên đây chưa phải nguyên nhân chậm, nhưng cần đồng bộ trước khi dữ liệu tăng.
- Thứ tự: **đo → giảm round trip (code) → index (V21) → phân trang → hạ tầng (cùng region)**. Phần hạ tầng cần người có quyền Supabase/Render thực hiện (mục 6).

## 1. Đo trước khi sửa (baseline) — 0,5 ngày

Mục tiêu: có số "trước/sau" cho từng API, không đoán.

1. **Đếm query theo request** (code, không đổi config DB): `StatementInspector` của Hibernate tăng một bộ đếm `ThreadLocal`; `OncePerRequestFilter` ghi một dòng log `method path status durationMs queryCount` ở mức INFO. Không log nội dung SQL hay tham số. Tắt được bằng property `app.metrics.query-count=false`.
2. Chạy bộ test API trên **Postgres local** (Docker, schema giống prod, dữ liệu giả lập ~20 project / 8 tổ / 100 scene, không copy dữ liệu thật) và trên bản deploy qua phiên đăng nhập, ghi bảng baseline cho các API ở mục 2.
3. (Tuỳ chọn, cần chủ Supabase đồng ý) `select pg_stat_statements_reset();` trước một đợt đo để số liệu chỉ gồm đợt đó.

**Tiêu chí nghiệm thu chung sau mục 2:** số query của mỗi API **không tăng theo số dòng** (P, W, U, R); kiểm tra quyền ≤ 2 query/request.

## 2. P0 — Giảm round trip trong code (không cần migration) — 3–4 ngày

| # | API / chỗ sửa | Hiện tại | Cách sửa | Mục tiêu |
|---|---|---|---|---|
| 2.1 | **Phân quyền mỗi request** — `JwtAuthenticationFilter`, `ResourceAccessInterceptor`, `WorkspaceAccess` (`isAdmin`, `canRead`, `requireProject`, `hasProjectRole`…) | Mỗi request đọc lại `users` 2–3 lần, `workspace_members` theo user 1–3 lần, `clip_projects` 1–2 lần | Bean `@RequestScope` `AccessContext`: nạp **một lần** user hiện tại + danh sách membership ACTIVE (`workspaceId → role, leader`), cache project → workspace đã tra. Filter JWT đặt user vào context; interceptor, `@PreAuthorize` và service dùng lại. Logic quyền giữ nguyên | ≤ 2 query cho phần quyền |
| 2.2 | `GET /workspaces/{id}/scripts`, `GET /scripts/mine`, `GET /scripts/review-queue` — `ScriptService.toSummary` | `2 + 2P + U`: mỗi project lấy current version rồi **tải cả list scene để `.size()`**, owner lazy; review-queue lấy mọi project `IN_REVIEW` toàn hệ thống rồi lọc quyền từng cái | Một JPQL projection → `ScriptSummaryResponse`: `clip_projects` JOIN current `script_versions` (`is_current`) JOIN owner, `count(scenes)` bằng subquery/GROUP BY, lọc `archived=false` trong SQL. `mine`: JOIN `workspace_members` ACTIVE của user. Review-queue: JOIN `workspace_members` role `REVIEWER` ACTIVE của user | 1 query (+ phần quyền) |
| 2.3 | `GET /workspaces/mine`, `GET /workspaces` (admin) — `WorkspaceService.findMine/findVisible/toResponse` | `1 + 3W`; `findVisible` dùng `getOrDefault(id, toResponse(w))` nên **luôn** chạy 2 COUNT kể cả khi đã có seat | Hai query gom nhóm `select workspaceId, count(*) … group by` cho member và project (archived=false), map trong Java. Bỏ `getOrDefault` tính sẵn (dùng `seats.containsKey ? … : …`) | 3 query cố định |
| 2.4 | `GET /workspaces/{id}/members`, `/candidates` | `2 + U` (user lazy trong DTO) | `@Query … join fetch m.user where m.workspace.id = :id` | 1 query |
| 2.5 | `GET /scripts/{id}/review-history` — `ReviewHistoryService` | Mỗi event: 1 query membership + 1 lookup target (version/clip/composition) + actor lazy | Nạp membership của tổ **một lần** (`userId → role`); gom id theo loại target rồi `findAllById` (clip `join fetch renderJob.scriptVersion`, composition `join fetch scriptVersion`); `join fetch changedBy` khi đọc history | ~4 query cố định |
| 2.6 | `GET /library/clips`, `/library/filters`, `/library/clips/{id}` — `LibraryService` | Mỗi project: lookup publication 2 lần, tải **toàn bộ** publish history để lấy bản mới nhất, tải scene để đếm, owner/workspace lazy | Một query: project PUBLISHED, archived=false JOIN publication INTERNAL_LIBRARY (clip not null) `join fetch clip, owner, workspace`, lọc subject/grade trong SQL; publish record mới nhất bằng một query `… where id in (select max…) group by project`; scene count dùng chung query đếm của 2.2. `filters`: `select distinct subject / grade_level` | 2–3 query cố định |
| 2.7 | `GET /scripts/{id}/qa-comments` — `FeedbackService.findByProject` | Tải toàn bộ comment rồi lọc status/clip/reply ở Java; author, resolvedBy, scene lazy | Lọc `status`, `clipId` trong SQL; `join fetch author left join fetch resolvedBy left join fetch scene left join fetch parent` | 1 query |
| 2.8 | `GET /scripts/{id}/versions` | `1 + U` (người tạo lazy) | `join fetch createdBy` | 1 query |
| 2.9 | `GET /render/projects/{id}/jobs`, `GET /render/status/{id}` | Đọc cả entity có `input_props` (JSON lớn), versionNo lazy | Projection DTO không có `input_props`, `join scriptVersion` lấy `versionNo` | 1 query |
| 2.10 | `GET /admin/render-jobs`, `/admin/overview`, `/admin/metrics` — `AdminMetricsService` | `renderJobRepository.findAll()` gọi **2 lần**, sort + `limit(25)` trong Java, rồi project/user lazy; projects đọc hết để thống kê | `findTop25ByOrderByCreatedAtDesc` + `join fetch project, requestedBy`; thống kê bằng aggregate (`count … group by status`, `avg`), không `findAll` | ≤ 4 query, không tăng theo số job |
| 2.11 | `GET /templates` — `TemplateService` | Mỗi GET chạy `count()` để seed; một số lọc trong memory | Seed bằng `ApplicationRunner` lúc khởi động (một lần, idempotent); lọc `category`/`activeOnly` trong SQL | 1 query |
| 2.12 | `visualTheme` trong `ScriptResponse` | Tải cả composition (JSON scenes) chỉ để lấy theme | Query projection `select c.visualTheme from Composition c where c.scriptVersion.id = :v` | nhẹ hơn, 1 query |
| 2.13 | Transaction cho API đọc | Mỗi lời gọi repository / `WorkspaceAccess` là một transaction riêng | Sau 2.1, mỗi API đọc chạy trong **một** `@Transactional(readOnly = true)` ở service; tránh mở transaction lồng không cần thiết. Không bật lại open-in-view | bớt round trip COMMIT |

**Kiểm thử cho mục 2**
- Test số query: test tích hợp chạy trên Postgres Docker (đã có Docker trên máy dev), dùng `SessionFactory.getStatistics()` / bộ đếm `StatementInspector`, khẳng định số query **không đổi** khi số project tăng từ 2 lên 20 (bắt N+1 quay lại).
- Giữ nguyên toàn bộ test hiện có (322 test BE) và contract JSON (FE không phải sửa).

## 3. P1 — Index (migration V21) — 0,5 ngày

`V21__restore_v1_indexes.sql`, toàn bộ `CREATE INDEX IF NOT EXISTS` (bảng nhỏ, tạo trong transaction được; không cần `CONCURRENTLY`):

| Index | Phục vụ |
|---|---|
| `clip_projects (workspace_id, created_at DESC)` | danh sách project theo tổ |
| `clip_projects (owner_id, created_at DESC)` | `/scripts/mine` |
| `clip_projects (script_status)`, `clip_projects (video_status)` | review-queue, thư viện |
| `script_versions (project_id, version_no DESC)` | version hiện tại / lịch sử |
| `scenes (version_id, order_no)` | scene của version, đếm scene |
| `feedback_comments (project_id, timestamp_sec)`, `feedback_comments (scene_id)` | comment theo project / scene |
| `render_jobs (project_id, created_at DESC)`, `render_jobs (external_job_id)` | lịch sử render, callback |
| `publish_records (project_id, created_at DESC)` | bản xuất bản mới nhất |
| `status_histories (project_id, created_at DESC)` | review-history |
| `workspace_members (user_id)` | membership của user hiện tại (2.1). Unique `(workspace_id, user_id)` hiện có không dùng được khi lọc theo `user_id` |

- Thêm test PGlite cho V21 (chạy 2 lần, cả baseline uuid và varchar) như V19/V20, và file rollback.
- **Quy trình:** chạy V21 lên DB prod **trước** (jar bật Flyway một lần, có Hibernate validate), rồi mới push/redeploy. Render đang tắt Flyway.
- Kiểm tra lại bằng `EXPLAIN` (không `ANALYZE`) sau khi tạo; với bảng nhỏ Postgres vẫn có thể chọn Seq Scan — bình thường.

## 4. P2 — Phân trang & đọc thừa — 2 ngày (phối hợp FE)

- Thêm `page`/`size` (mặc định trả hết như hiện tại, `size` tối đa 100) cho: project list, `/scripts/mine`, review-queue, comments, review-history, versions, render jobs, users, library. Response giữ dạng mảng khi không truyền `page` để FE cũ không vỡ; khi có `page` trả kèm header `X-Total-Count`.
- Ghi contract vào `docs/FE_ACCOMPLISHMENTS_TODO.md` để FE chuyển dần.
- FE: chạy song song các lời gọi khởi động (profile, workspaces, projects) thay vì tuần tự; thư viện gọi `filters` một lần, không gọi lại mỗi lần đổi bộ lọc.

## 5. Thứ tự làm & ước lượng

| Bước | Nội dung | Ước lượng | Cần migration prod? |
|---|---|---|---|
| 1 | Đo baseline (mục 1) | 0,5 ngày | Không |
| 2 | 2.1 phân quyền theo request + 2.3 workspace + 2.2 project list | 1,5 ngày | Không |
| 3 | 2.5 review-history, 2.6 thư viện, 2.7–2.9 | 1 ngày | Không |
| 4 | 2.10 admin, 2.11 templates, 2.12, 2.13 | 0,5–1 ngày | Không |
| 5 | Đo lại, so baseline | 0,5 ngày | Không |
| 6 | V21 index | 0,5 ngày | **Có** — chạy trước khi deploy |
| 7 | Phân trang (mục 4) | 2 ngày | Không |
| 8 | Hạ tầng (mục 6) | theo lịch team | Có — chuyển DB |

Mỗi bước là một commit/PR riêng, có test số query; deploy từng bước để dễ quay lại.

## 6. Hạ tầng — cần người có quyền Supabase/Render quyết định và thực hiện

### 6.1. Đưa BE và DB về cùng region (khuyến nghị)

- Hiện tại: **Render Singapore** ↔ **Supabase Sydney (`ap-southeast-2`)**. Mỗi round trip qua đường này ước tính ~90–100 ms (cần đo); trong cùng region chỉ ~1–2 ms. Với vài chục round trip mỗi màn hình, đây là phần lớn độ trễ hiện tại.
- **Render không có region Sydney** (Oregon, Ohio, Virginia, Frankfurt, Singapore) ⇒ phải chuyển **DB sang Singapore**. Supabase **không đổi region của project có sẵn** ⇒ tạo project mới.
- Các bước (chủ tài khoản Supabase làm, có thể làm cùng):
  1. Tạo project Supabase mới ở **Southeast Asia (Singapore) — `ap-southeast-1`**, gói Free (Free cho tối đa 2 project đang chạy).
  2. Hẹn giờ ngừng ghi (~15 phút), `pg_dump` schema `public` (cả dữ liệu và `flyway_schema_history`) từ project cũ, `pg_restore` vào project mới. Dữ liệu chỉ vài MB.
  3. Kiểm tra: `flyway_schema_history` ở V20/V21, số dòng các bảng khớp, chạy jar với `ddl-auto=validate`.
  4. Đổi biến môi trường trên Render: `DB_URL` (host pooler mới `aws-0-ap-southeast-1.pooler.supabase.com:6543`, giữ `sslmode=require&prepareThreshold=0`), `DB_USERNAME`, `DB_PASSWORD` → redeploy.
  5. Giữ project Sydney 1–2 tuần làm dự phòng rồi mới xoá.
- Rủi ro: dữ liệu ghi trong lúc chuyển (nên ngừng ghi), file video không nằm trong DB (đang ở worker trên laptop) nên không ảnh hưởng.

### 6.2. Gói Render

- Free: **0,1 CPU, 512 MB**, ngủ sau 15 phút, lần deploy gần nhất khởi động **190 s**. Cron ping chỉ chống ngủ, không làm nhanh hơn khi đang chạy.
- Lựa chọn: lên **Starter** (không ngủ, CPU mạnh hơn, có metrics) — có phí hằng tháng, team quyết định. Không bắt buộc cho đồ án nếu chấp nhận lần đầu chậm.

### 6.3. Không làm lúc này

- **Không nâng Supabase Pro**: DB không quá tải (~10/60 kết nối, SQL dưới 1 ms).
- **Không tăng pool Hikari** (đang 3) trước khi đo lại sau mục 2 — ít round trip hơn thì pool 3 đủ.
- **Không đổi hàng loạt LAZY → EAGER**: 32 quan hệ đều LAZY là đúng; N+1 đến từ vòng lặp gọi repository.

## 7. Liên quan

- Báo cáo gốc: DB audit của LeTran (19/10/2026).
- Lỗi đang mở khác: `docs/BUGS_2026-10-18.md`.
- Quy trình migration prod: `docs/BUGS_2026-10-18.md` mục 4.
