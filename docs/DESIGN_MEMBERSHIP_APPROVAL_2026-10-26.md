# Thiết kế giai đoạn 3: thành viên project, duyệt, khóa chỉnh sửa, đóng góp

Ngày: **26/10/2026**. Nguồn: `master_update_plan.md` (biên bản mentor 26/10), mục M10–M17, D08–D21.
Baseline: BE `9572169` + các thay đổi trong commit kèm tài liệu này.

Tài liệu gồm hai phần:

- **Phần A — đã làm** (M11, M15, D10, revision bản nháp kịch bản): đã có code và test, chờ push.
- **Phần B — thiết kế đề xuất** cho M12/M13/M14/M17: schema, ma trận quyền, luồng trạng thái, migration.
  Đây là **đề xuất để team chốt**, chưa code. Mỗi chỗ cần quyết định ghi rõ mã `Dxx` và phương án mặc định.

---

## Phần A — Đã làm

### A1. Workspace phải đủ người, đủ 3 role (M10/M11)

`POST /api/v1/workspaces` (Admin) nhận thêm `members`:

```json
{
  "name": "Tổ Tin",
  "department": "Tin học",
  "leaderUserId": "<id>",
  "leaderRole": "WRITER",
  "members": [
    { "userId": "<id>", "role": "REVIEWER", "isLeader": false },
    { "userId": "<id>", "role": "PRODUCER" }
  ]
}
```

- Leader cộng `members` phải có **≥ 3 user khác nhau**, mỗi role WRITER / REVIEWER / PRODUCER ít nhất 1 người.
  Leader có role sản xuất riêng; cờ Leader là trách nhiệm cộng thêm, không thay một role.
- Từ chối (400), **không ghi gì**:
  - thiếu người hoặc thiếu role (thông báo nêu role thiếu);
  - một user xuất hiện hai lần (kể cả trùng với leader);
  - user không ACTIVE;
  - role `ADMIN`. Trước đây BE âm thầm đổi thành WRITER.
- Tạo workspace và các seat nằm trong một transaction. Mọi kiểm tra chạy trước khi ghi workspace.
- `PUT /workspaces/{id}` (đổi tên) không dùng `members`.

### A2. Leader quản lý role, không thêm/xóa người (M15, D11 theo phương án đề xuất)

| Hành động | Admin | Leader |
|---|---|---|
| `POST /workspaces/{id}/members` (thêm người) | ✅ | ❌ 403 |
| `DELETE /workspaces/{id}/members/{memberId}` | ✅ | ❌ 403 |
| `GET /workspaces/{id}/candidates` | ✅ | ❌ 403 (không thêm người thì không cần) |
| `PATCH …/members/{memberId}` đổi `role` | ✅ | ✅ trong tổ mình |
| `PATCH …` đổi `status` (kích hoạt/vô hiệu hóa) | ✅ | ❌ 403, để Leader không dùng status thay cho xóa |
| `PATCH …` đổi `isLeader` (bổ nhiệm/thu hồi Leader) | ✅ | ❌ 403 |

Gửi lại `status`/`isLeader` **không đổi giá trị** thì không bị tính là thay đổi. FE có thể gửi cả object member khi chỉ đổi role.

### A3. Không làm mất một role đang có (D10, phương án "chặn thay đổi")

Đổi role, vô hiệu hóa hoặc xóa một member mà làm một role sản xuất **đang có người ACTIVE** trở thành không còn ai → 400:
`This change would leave the workspace without an active [REVIEWER]; give that role to another member first`.

- Workspace cũ vốn đã thiếu role vẫn chỉnh được, để Admin bổ sung người. Chỉ role đang có mới được giữ.
- Mọi thay đổi membership khóa dòng workspace (`SELECT … FOR UPDATE`). Hai request đồng thời không thể cùng qua kiểm tra rồi làm mất role.
- Vẫn giữ luật cũ: không bỏ/vô hiệu hóa Leader ACTIVE cuối cùng.

### A4. Chống ghi đè bản nháp kịch bản (một phần M17, lớp "Revision")

Trước đây `PUT /scripts/{id}` là ai lưu sau người đó thắng: hai tab hoặc hai Writer ghi đè nhau mà không báo.

- **V22** thêm `script_versions.draft_revision int NOT NULL DEFAULT 0` (CHECK ≥ 0). Rollback ở `docs/rollback/V22__script_draft_revision.down.sql`. Test PGlite ở `src/test/database/draft-revision-migration-test.mjs`.
- `ScriptResponse` có thêm `draftRevision`. `UpdateScriptRequest` nhận `baseRevision` (tùy chọn).
- Khi lưu, BE khóa dòng version hiện tại rồi so `baseRevision`:
  - khác revision hiện tại → **409**, bản nháp giữ nguyên;
  - khớp → lưu, `draftRevision + 1`.
- Không gửi `baseRevision` (FE cũ) vẫn lưu như trước, vẫn tăng revision. Nhờ vậy deploy BE trước FE không làm hỏng gì.
- `sync-durations` trên bản nháp cũng tăng revision và trả revision mới trong `revision` (`target: "SCRIPT"`). Tab đang mở bản cũ phải tải lại thay vì đè thời lượng vừa đo.

> ⚠️ **Thứ tự deploy:** Render chạy `ddl-auto: validate`, Flyway tắt. Phải chạy V22 lên Supabase **trước** khi push BE,
> nếu không BE không khởi động được (đã xảy ra với V20). Cách chạy giống V21: chạy jar một lần với `SPRING_FLYWAY_ENABLED=true`.

### A5. Việc FE cần làm cho phần A

Đã ghi vào FE `docs/FE_ACCOMPLISHMENTS_TODO.md` mục 9.7:

- `CreateWorkspaceForm`: chọn thêm ≥ 2 người và role của họ; hiện lỗi 400 của BE.
- `ManageMembersModal`: Leader chỉ còn đổi role. Ẩn thêm/xóa/kích hoạt/bổ nhiệm Leader; Admin vẫn đủ. Hiện lỗi 400 khi mất role.
- Lưu bản nháp (`projectService.update`, `sceneSaveQueue`): gửi `baseRevision = script.draftRevision`, cập nhật revision từ response; gặp 409 thì tải lại và giữ bản local giống composition.

---

## Phần B — Thiết kế đề xuất (chưa code)

### B1. Quyết định cần chốt và phương án mặc định

| Mã | Câu hỏi | Mặc định đề xuất | Lý do |
|---|---|---|---|
| D08 | Role cố định ở đâu | Workspace có role **mặc định**; project có role **chốt khi được duyệt tham gia**, không đổi trong vòng đời project | Biên bản M14: "trong một tổ hoặc ít nhất project phải role cố định" |
| D09 | Khác role giữa các project cùng tổ | **Cho phép**, mặc định lấy role workspace; khác role thì cần Leader duyệt | Tổ nhỏ hay thiếu người một role |
| D12 | Tự duyệt, nhiều Leader | Writer là Leader tự thông qua **đề xuất khởi tạo**; yêu cầu tham gia của chính Leader do **Leader khác hoặc Admin** duyệt; không ai duyệt nội dung/video do chính mình làm | Tách quản lý với độc lập duyệt |
| D13 | Non-participant xem được gì | Thành viên tổ **xem** (read-only) mọi project của tổ; chỉ participant ACTIVE mới **ghi** theo role | Giữ dashboard/thư viện như hiện tại |
| D14 | Khi nào cần đủ 3 role trong project | **Trước khi gửi review kịch bản** cần có Reviewer; **trước khi dựng** cần có Producer. Khởi tạo chỉ cần Writer | Không chặn bắt đầu viết |
| D15 | Thay người, nghỉ việc, Admin override | Thu hồi quyền (`REVOKED`) nhưng giữ dòng và lịch sử; thay người = thu hồi + duyệt người mới, có audit. Admin vận hành được, **không** được duyệt nội dung thay Reviewer | Không sửa âm thầm role cũ |
| D16 | Lease | Một lease cho mỗi `(project, resource)`; resource = `SCRIPT` hoặc `COMPOSITION`; TTL 90 giây, gia hạn mỗi 30 giây; Leader/Admin được thu hồi | Writer và Producer không sửa cùng tài nguyên cùng lúc |
| D17 | Comment khi người khác đang sửa | Comment/reply **không cần** lease; review luôn trên version/revision đã nộp | Comment không đổi nội dung |
| D18 | Contribution để làm gì | Credit + truy vết; **không** tính phần trăm hay xếp hạng | Biên bản chỉ yêu cầu "ghi contribution" |
| D21 | Dữ liệu cũ | Backfill chỉ những gì chứng minh được, phần còn lại đánh dấu `LEGACY` | Không đoán role |

### B2. Schema đề xuất (V23+, chỉ thêm, không sửa bảng cũ)

```text
project_proposals
  id, workspace_id → workspaces, proposer_id → users
  title, subject, grade_level, objective TEXT, scope TEXT
  status            DRAFT | PENDING | APPROVED | REJECTED | WITHDRAWN
  decided_by → users, decided_at, decision_reason TEXT
  project_id → clip_projects UNIQUE NULL      -- set đúng một lần khi APPROVED
  revision int                                -- chống duyệt/sửa chồng
  created_at, updated_at

project_members
  id, project_id → clip_projects, user_id → users
  role              WRITER | REVIEWER | PRODUCER     -- bất biến sau khi tạo
  access_status     ACTIVE | REVOKED
  source            OWNER | APPROVED_REQUEST | ASSIGNED | LEGACY_BACKFILL
  approved_by, approved_at, revoked_by, revoked_at, revoke_reason
  UNIQUE (project_id, user_id)

project_join_requests
  id, project_id, user_id, requested_role
  kind              REQUEST (tự xin) | ASSIGNMENT (Leader đề nghị)
  status            PENDING | APPROVED | REJECTED | CANCELLED
  requested_by, decided_by, decided_at, reason
  UNIQUE (project_id, user_id) WHERE status = 'PENDING'

contribution_events
  id, project_id, actor_id, actor_role (snapshot), action
  target_type       SCRIPT_VERSION | COMPOSITION | CLIP | COMMENT | PUBLICATION | PROPOSAL
  target_id, target_revision
  idempotency_key UNIQUE                     -- retry không nhân đôi
  created_at

edit_leases
  id, project_id, resource SCRIPT | COMPOSITION
  holder_id, session_id, token uuid, generation bigint
  acquired_at, renewed_at, expires_at
  UNIQUE (project_id, resource)
```

Ghi chú:

- Không thêm trạng thái "PROPOSED" vào `clip_projects.script_status`: đề xuất bị từ chối không nên thành project và không nên làm rối vòng đời kịch bản.
- `status_histories` tiếp tục là lịch sử review. `contribution_events` không thay nó mà tham chiếu cùng target.
- Contribution ghi **trong cùng transaction** với hành động: checkpoint kịch bản khi submit, lưu composition revision, quyết định review, resolve note, publish.
  Autosave **không** ghi event.

### B3. Luồng trạng thái

**Đề xuất project (M12)**

```text
DRAFT --gửi--> PENDING --Leader duyệt--> APPROVED (tạo clip_projects + project_members OWNER/WRITER, một lần)
                  |  \--Leader từ chối (bắt buộc lý do)--> REJECTED --sửa & gửi lại--> PENDING
                  \--người đề xuất rút--> WITHDRAWN
Writer là Leader: gửi = duyệt luôn (ghi decided_by = chính họ, source = OWNER).
```

Duyệt lặp (double click, retry) phải idempotent: duyệt khóa dòng, thấy `APPROVED` + `project_id` có sẵn thì trả lại project đó.

**Tham gia project**

```text
PENDING --Leader duyệt--> APPROVED (tạo project_members role = requested_role, ACTIVE)
        --Leader từ chối--> REJECTED
        --người xin hủy--> CANCELLED
project_members ACTIVE --Leader/Admin thu hồi--> REVOKED (giữ dòng; lịch sử giữ actor_role)
```

**Edit lease (M17)**

```text
POST   /scripts/{id}/leases/{resource}            → cấp nếu trống hoặc hết hạn; trả token + generation + expiresAt
PUT    /scripts/{id}/leases/{resource}            → gia hạn (đúng token)
DELETE /scripts/{id}/leases/{resource}            → nhả (best effort khi rời trang)
DELETE /scripts/{id}/leases/{resource}?force=true → Leader/Admin thu hồi
Mọi write liên quan gửi header X-Edit-Lease: <token>:<generation>; sai token/generation hoặc hết hạn → 423 Locked.
```

- Cấp lease bằng một câu `INSERT … ON CONFLICT (project_id, resource) DO UPDATE … WHERE expires_at < now()`. Hai người cùng xin thì chỉ một thắng.
- `generation` tăng mỗi lần đổi chủ. Request đến muộn của chủ cũ bị từ chối dù token cũ chưa bị xóa.
- Lease chỉ cộng thêm, **không thay** revision: vẫn giữ `baseRevision`/409 của composition và bản nháp (A4).
- Không giữ transaction hay row lock suốt thời gian sửa.

### B4. Ma trận quyền mục tiêu (server-side)

"P-role" là role trong `project_members` ACTIVE. "W-member" là thành viên ACTIVE của tổ.

| Endpoint | Hiện tại | Mục tiêu |
|---|---|---|
| `GET /scripts/{id}`, versions, diff, review-history, qa-comments | W-member | W-member (D13) |
| `PUT /scripts/{id}` (bản nháp) | W-member role WRITER | P-role WRITER + lease SCRIPT |
| `POST /scripts/{id}/submit-review` | WRITER | P-role WRITER; project có P-role REVIEWER ACTIVE (D14) |
| `POST /scripts/{id}/approve`, `request-changes` | REVIEWER | P-role REVIEWER, không phải tác giả version |
| `PUT /scripts/{id}/composition`, `sync-durations`, `tts-preview` | PRODUCER | P-role PRODUCER + lease COMPOSITION (`tts-preview` không cần lease) |
| `POST /scripts/{id}/submit-video-review` | PRODUCER | P-role PRODUCER |
| `POST /scripts/{id}/approve-video`, `send-back` | REVIEWER | P-role REVIEWER, không phải producer của revision |
| `POST /scripts/{id}/qa-comments`, replies, resolve, reopen | theo role | P-role bất kỳ (comment không cần lease, D17) |
| `POST /render/projects/{id}/start`, retry, cancel | PRODUCER/ADMIN | P-role PRODUCER; Admin cho vận hành |
| `POST /scripts/{id}/publish` | theo role | P-role REVIEWER hoặc Leader (cần chốt) |
| `DELETE /scripts/{id}` (lưu trữ) | owner/Leader/Admin | giữ nguyên |
| `POST /ai/script/*` | đăng nhập | rà lại: nếu gắn project thì cần P-role WRITER |
| `POST /workspaces/{id}/scripts` (tạo trực tiếp) | W-member WRITER | thay bằng `POST /workspaces/{id}/proposals`; endpoint cũ chỉ còn cho Writer-Leader hoặc bỏ |

Admin bypass hiện tại (`isLeader()`/`hasProjectRole()` trả true cho Admin) phải tách hai loại: **vận hành** (archive, retry render, thu hồi lease) vẫn cho; **duyệt nội dung** thì không.

### B5. API mới (phác thảo)

```text
POST   /workspaces/{id}/proposals                    Writer tạo/gửi đề xuất
GET    /workspaces/{id}/proposals?status=PENDING     Leader xem inbox (phân trang)
PATCH  /proposals/{id}                               sửa khi DRAFT/REJECTED (baseRevision)
POST   /proposals/{id}/submit | approve | reject | withdraw
GET    /scripts/{id}/members                         participant + role + trạng thái
POST   /scripts/{id}/join-requests                   tự xin (REQUEST) hoặc Leader đề nghị (ASSIGNMENT)
POST   /join-requests/{id}/approve | reject | cancel
POST   /scripts/{id}/members/{memberId}/revoke       Leader/Admin, bắt buộc lý do
GET    /scripts/{id}/contributions                   roster + sự kiện (phân trang)
GET    /me/project-roles                             FE chọn đúng role khi mở project
```

### B6. Migration và rollout

1. **V23 (chỉ thêm):** 5 bảng ở B2, kèm rollback và test PGlite như V19–V22.
2. **Backfill (V24, idempotent)**, chỉ những gì chứng minh được:
   - `clip_projects.owner_id` → `project_members` role WRITER, source `OWNER`;
   - người ra quyết định trong `status_histories` (REVIEW_DECISION) → REVIEWER, `LEGACY_BACKFILL`;
   - `compositions.producer_id` → PRODUCER, `LEGACY_BACKFILL`;
   - một user có nhiều role suy ra trong cùng project → ghi role đầu tiên, liệt kê ra để Leader xác nhận. **Không** thêm cả tổ làm participant.
3. **Cờ `app.access.project-membership`** (mặc định `false`). Khi tắt, quyền ghi vẫn suy từ workspace như hiện nay, bảng mới chỉ được ghi song song.
   Bật sau khi FE xong màn tham gia/duyệt và Leader đã rà các project đang chạy.
4. **Cờ `app.edit-lease.required`** (mặc định `false`). Khi tắt, write không có header lease vẫn được nhận nhưng ghi log, để FE cũ còn dùng được. Bật khi mọi FE đã gửi lease.
5. Workspace cũ thiếu role: thêm `GET /admin/workspaces/understaffed` liệt kê để Admin bổ sung; không tự gán người.
6. Mỗi migration: chạy trên DB tách biệt trước, có rollback, chạy prod trước khi push BE (ràng buộc `ddl-auto: validate`).

### B7. Test bắt buộc trước khi bật cờ

- Quyền: negative test **từng endpoint** ở B4 (người ngoài tổ, W-member không phải participant, P-role sai, REVOKED, Admin duyệt nội dung).
- Đề xuất: duyệt hai lần chỉ tạo một project; từ chối không có lý do → 400; người tổ khác không duyệt được.
- Tham gia: hai request PENDING cùng user/project → một bị từ chối (unique partial index); duyệt idempotent.
- Role snapshot: đổi role workspace không đổi P-role; contribution giữ `actor_role` cũ.
- Lease: hai người xin cùng lúc → một thắng; hết hạn → người khác xin được; token cũ/generation cũ → 423; hai tab cùng user → tab sau không ghi được khi chưa xin lease.
- Contribution: retry cùng `idempotency_key` không nhân đôi; autosave không tạo event.

### B8. Thứ tự ticket đề xuất

| # | Ticket | Phụ thuộc |
|---|---|---|
| 1 | V23 bảng mới + rollback + test PGlite | Chốt D08, D09, D16 |
| 2 | Đề xuất project (API + service + test) | 1, D12 |
| 3 | Join request + project_members + revoke | 1, D13, D15 |
| 4 | `WorkspaceAccess` đọc P-role sau cờ; negative test toàn bộ B4 | 3 |
| 5 | Backfill V24 + báo cáo các mục cần xác nhận | 3 |
| 6 | Edit lease + header trên các write ở B4 (sau cờ) | 1, D16, D17 |
| 7 | Contribution events trong các transaction ở B2 | 3, D18 |
| 8 | API dashboard Leader (inbox đề xuất/tham gia, project theo stage, blocker) | 2, 3 |
| 9 | Bật cờ trên staging, E2E ba người, rồi prod | FE xong các màn tương ứng |
