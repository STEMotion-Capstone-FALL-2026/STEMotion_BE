# Thiết kế giai đoạn 3: thành viên project, duyệt, khóa chỉnh sửa, đóng góp

Tạo: **26/10/2026**. Cập nhật: **29/10/2026** theo `TEAMMATE_TASKS_2026-10-27` (T01–T12) và phản hồi của teammate.
Nguồn: `master_update_plan.md` (biên bản mentor 26/10), mục M10–M17, D08–D21.

| Phần | Nội dung | Trạng thái |
|---|---|---|
| **A** | Workspace đủ 3 role, quyền Leader, giữ role, revision bản nháp, sync không áp kết quả cũ, 409 có cấu trúc | A1–A3/V22 ở `c7435c7`; A4.2–A4.4 đã push trong `7f207bb`. A-8 kiểm identity phiên bản + revision bổ sung local 30/10/2026, chưa push |
| **B** | Đề xuất project, tham gia, role theo project, edit lease, contribution, dashboard Leader | **Thiết kế, chưa code.** Mọi `Dxx` ở B1 chờ người phụ trách team xác nhận |

Contract FE cho phần A (ví dụ request/response/lỗi): `docs/API_CONTRACT_PART_A_2026-10-29.md`.

Bubble Sort chỉ là ý tưởng chủ đề cho video demo mentor (đính chính 27/10). Tài liệu này không đề xuất engine/trace hay API riêng cho nó.

---

## Phần A — Đã làm

### A1. Workspace phải đủ người, đủ 3 role (M10/M11)

`POST /api/v1/workspaces` (Admin) nhận `members`. Leader cộng `members` phải có **≥ 3 user khác nhau**, mỗi role WRITER / REVIEWER / PRODUCER ít nhất 1 người. Leader có role sản xuất riêng; cờ Leader là trách nhiệm cộng thêm.

Từ chối 400 và **không ghi gì** khi:
- thiếu người hoặc thiếu role;
- trùng user (kể cả trùng leader);
- user không ACTIVE;
- role `ADMIN`.

Mọi kiểm tra chạy trước khi ghi workspace, trong một transaction.

### A2. Leader đổi role, không thêm/xóa người (M15; D11 làm theo phương án đề xuất, chờ xác nhận)

| Hành động | Admin | Leader |
|---|---|---|
| Thêm thành viên, xem ứng viên, xóa thành viên | ✅ | 403 |
| Đổi `role` | ✅ | ✅ trong tổ mình |
| Đổi `status` / `isLeader` | ✅ | 403 (gửi lại giá trị không đổi thì không tính là thay đổi) |

Request có trường bị cấm thì bị từ chối trước khi đổi bất cứ trường nào, nên không có cập nhật một phần. Member ID thuộc tổ khác → 400.

### A3. Không làm mất một role đang có (D10, phương án "chặn thay đổi")

- Đổi role, vô hiệu hóa hoặc xóa mà làm một role đang có người ACTIVE không còn ai → 400.
- Tổ cũ vốn thiếu role vẫn chỉnh được để bổ sung người.
- Vẫn giữ luật không bỏ Leader ACTIVE cuối cùng.
- Mọi thay đổi khóa dòng workspace (`SELECT … FOR UPDATE`).

**Câu hỏi mở (T01):** coverage hiện tính theo **membership ACTIVE**, không xét trạng thái **account**. Một account bị khóa nhưng membership còn ACTIVE vẫn được tính là đủ role.
- Đề xuất: chỉ tính người mà cả account lẫn membership đều ACTIVE.
- Khi khóa account làm một tổ mất role: chặn khóa, hoặc cho khóa và đánh dấu tổ "thiếu nhân sự" để chặn công việc mới. Việc này ảnh hưởng `UserService` (khóa account) và mọi tổ người đó tham gia, nên **cần chốt trước khi sửa**. Chưa đổi hành vi.

### A4. Revision bản nháp và sync thời lượng (một phần M17, T02)

**A4.1 Revision (V22, `c7435c7`).**
- `script_versions.draft_revision int NOT NULL DEFAULT 0`.
- `PUT /scripts/{id}` nhận `baseRevision` (tùy chọn). BE khóa dòng version hiện tại, so revision, khớp thì lưu và tăng 1.
- Revision tăng trong cùng transaction với dữ liệu. Transaction rollback thì revision không tăng (có test trên Postgres thật).

**A4.2 Sync không áp kết quả đo của lời đọc cũ.** *(mới)*
- Trước đây `sync-durations` đọc scene → chờ worker TTS/Whisper (vài giây đến vài phút) → ghi thời lượng mà không kiểm tra lại. Writer sửa lời đọc trong lúc chờ thì thời lượng của lời cũ đè lên bản mới.
- Giờ BE **chụp revision đang được đo** trước khi gọi worker: `draftRevision` cho bản nháp, `composition.revision` cho composition.
- Khi ghi kết quả, BE kiểm tra lại revision đó **dưới cùng row lock** với lệnh ghi. Khác thì trả 409 và không ghi gì.
- Client gửi `baseRevision` cũ thì bị trả 409 **trước khi gọi worker**, không tốn tiền TTS.
- `draftRevision` và `composition.revision` là hai bộ đếm riêng. Kết quả luôn ghi vào đúng tài nguyên đã đo, và lỗi nêu rõ `resource`.

**A4.3 409 có cấu trúc.** *(mới)* Mọi xung đột revision trả `code: "STALE_REVISION"` kèm:

```json
"details": { "resource": "SCRIPT_DRAFT" | "COMPOSITION", "currentRevision": 7, "baseRevision": 6 }
```

`currentRevision` là `null` khi hai lần lưu đầu tiên đua nhau và chưa biết số. Các lỗi khác giữ nguyên shape cũ, không có `details`.

**A4.4 Bằng chứng trên DB tách biệt.** *(mới)* `PostgresConcurrencyTest` dựng một Postgres 16 embedded dùng một lần (không phải DB chung), chạy Flyway V1–V22 và để Hibernate `validate` entity với schema. Các case:
- hai admin đồng thời xóa 2 reviewer cuối → đúng một lệnh thành công, còn đúng 1 reviewer;
- đồng thời vô hiệu hóa reviewer này và đổi role reviewer kia → vẫn còn 1 reviewer;
- hai lần lưu đồng thời từ cùng `baseRevision` → một thành công, một `STALE_REVISION`, revision tăng đúng 1;
- sync đo revision cũ → 409, không ghi; lưu bị rollback → revision không tăng.

Kiểm chứng ngược: tạm bỏ khóa trong code thì 3 case đầu fail ở cả 3 lần chạy. Trả khóa lại thì pass.

**A4.5 Kết thúc giai đoạn `baseRevision` tùy chọn.** Hiện tại không gửi `baseRevision` vẫn lưu được, tức là **chưa được bảo vệ**.

| Bước | Điều kiện | Hành động |
|---|---|---|
| 1 (hiện tại) | FE `7d43fb8` đã gửi `baseRevision` khi autosave | BE log mỗi lần lưu không có `baseRevision` (cần thêm, ticket A-6) |
| 2 | Log sạch ≥ 1 tuần trên production, FE cũ đã hết cache | Cờ `app.draft.require-base-revision=true` → thiếu `baseRevision` trả 400 |
| 3 | Ổn định | Bỏ cờ, bắt buộc vĩnh viễn |

Bản nháp sync hiện không bắt buộc `baseRevision`, vì revision đã được chụp phía server. FE nên gửi `script.draftRevision` để biết sớm khi đang ở bản cũ (9.7 bổ sung).

### A5. Tình trạng FE

FE `7d43fb8` (longtri) đã làm 9.7, đối chiếu ngày 29/10: 188 test pass, `tsc` sạch.
- Form tạo tổ gửi `members`.
- `ManageMembersModal` tách quyền Admin/Leader.
- `sceneSaveQueue` gửi `baseRevision`, nhận `draftRevision`, gặp 409 thì giữ bản local và cho tải lại.
- Writer sync cập nhật revision từ `revision`.
- Chỉ `sceneSaveQueue` ghi bản nháp, không có đường lưu nào bỏ sót `baseRevision`.

Còn lại cho FE (không chặn):
- gửi `baseRevision` khi Writer sync bản nháp;
- đọc `details.currentRevision` thay vì chỉ dựa vào status 409.

---

## Phần B — Thiết kế đề xuất (chưa code)

### B1. Decision log

Trạng thái: **Đề xuất** = chưa ai xác nhận; **Đã làm theo đề xuất** = có code nhưng vẫn cần xác nhận; **Cần sửa** = teammate đã phản biện, đề xuất đã đổi.

| Mã | Câu hỏi | Đề xuất | Lý do / ảnh hưởng | Trạng thái |
|---|---|---|---|---|
| D08 | Role cố định ở đâu | Workspace có role **mặc định**; project giữ role **được duyệt khi tham gia**, bất biến trong vòng đời project. Đổi role workspace không đổi role project cũ | M14; tránh đổi role tổ rồi thành reviewer phần mình viết. Ảnh hưởng mọi kiểm tra quyền ghi | Đề xuất (LeTran nghiêng về phương án này) |
| D09 | Khác role giữa các project cùng tổ | Cho phép; mặc định role workspace, khác thì Leader duyệt khi duyệt tham gia | Tổ nhỏ thiếu người | Đề xuất |
| D10 | Mất role sau khi tổ hoạt động | Chặn thay đổi | Đơn giản, không cần trạng thái "thiếu nhân sự" | **Đã làm theo đề xuất** (A3). Còn mở phần account bị khóa |
| D11 | Quyền Leader | Đổi role; Admin-only thêm/xóa/status/bổ nhiệm Leader | M15 | **Đã làm theo đề xuất** (A2) |
| D12 | Tự duyệt, nhiều Leader | Writer-Leader tự thông qua **đề xuất khởi tạo** (audit `source=SELF_APPROVED_LEADER`). Yêu cầu tham gia của một Leader do **Leader khác hoặc Admin** duyệt. Nhiều Leader: **bất kỳ một Leader** khác người gửi đều duyệt được, ai duyệt trước thắng (khóa dòng) | Biên bản M12 | Đề xuất |
| D13 | Non-participant đọc gì | W-member đọc project, version, comment, review history, clip của tổ; **không** ghi. Không đọc draft composition chưa gửi duyệt | Dashboard/thư viện giữ như cũ | Đề xuất |
| D14 | Khi nào cần đủ role | Gửi review kịch bản cần ≥ 1 P-REVIEWER ACTIVE; dựng (sửa composition) cần ≥ 1 P-PRODUCER. Mất người giữa chừng: chặn **chuyển stage tiếp theo**, không khóa dữ liệu đã có | Không chặn bắt đầu viết | Đề xuất |
| D15 | Thu hồi, tham gia lại, Admin override | Thu hồi = `REVOKED`, giữ dòng và lịch sử. **Tham gia lại cùng role**: join request mới, khi duyệt thì kích hoạt lại **chính dòng cũ** (giữ `UNIQUE(project,user)`), lịch sử nằm ở join requests. **Tham gia lại với role khác**: không cho trong cùng project (role bất biến, D08); muốn đổi người thì thêm người khác. Admin chỉ override việc vận hành, không duyệt nội dung | Không lách invariant bằng dòng thứ hai | **Cần sửa** → đã sửa theo phản biện T06 |
| D16 | Lease | `(project, resource)` với resource `SCRIPT` / `COMPOSITION`; TTL 90 s, gia hạn mỗi 30 s; Leader/Admin force-release có lý do | Writer và Producer không sửa cùng tài nguyên | Đề xuất |
| D17 | Comment khi đang có người sửa | Comment/reply/resolve không cần lease; review gắn với version/revision đã nộp | Comment không đổi nội dung | Đề xuất |
| D18 | Contribution để làm gì | Credit + truy vết; không tính %, không xếp hạng | Biên bản chỉ yêu cầu "ghi contribution" | Đề xuất (LeTran nghiêng về) |
| D20 | QC artifact thật | Sau render, Reviewer xem MP4 thật trước publish; AI/vision chỉ hỗ trợ | Web preview đúng không bảo đảm MP4 đúng | Đề xuất |
| D21 | Dữ liệu cũ | Backfill chỉ phần chứng minh được; xung đột/thiếu bằng chứng vào báo cáo chờ xác nhận, **không cấp quyền** (B6) | Không đoán role | **Cần sửa** → đã sửa theo T03 |
| D-PUB | Ai publish | Tách khỏi duyệt chất lượng: publish cần video đã APPROVED, người bấm là **P-PRODUCER hoặc Leader**; Reviewer không cần tự publish. Hiện tại code cho PRODUCER, giữ nguyên tới khi chốt | Tách "đạt chất lượng" với "đưa ra ngoài" | Đề xuất (mới) |
| A-acct | Account bị khóa có tính coverage không | Không tính (A3) | Ảnh hưởng `UserService` + nhiều tổ | Mở |

### B2. Schema đề xuất (V23+, chỉ thêm)

```text
project_proposals
  id, workspace_id, proposer_id, title, subject, grade_level, objective, scope
  status   DRAFT | PENDING | APPROVED | REJECTED | WITHDRAWN
  decided_by, decided_at, decision_reason
  source   null | SELF_APPROVED_LEADER
  project_id UNIQUE NULL      -- set đúng một lần khi APPROVED
  revision int                -- chống sửa/duyệt chồng (baseRevision + 409 STALE_REVISION như A4)

project_members
  id, project_id, user_id, role (bất biến)
  access_status ACTIVE | REVOKED
  source  OWNER | APPROVED_REQUEST | ASSIGNED | LEGACY_BACKFILL
  approved_by, approved_at, revoked_by, revoked_at, revoke_reason
  UNIQUE (project_id, user_id)            -- rejoin kích hoạt lại dòng này (D15)

project_join_requests
  id, project_id, user_id, requested_role, kind REQUEST | ASSIGNMENT
  status PENDING | APPROVED | REJECTED | CANCELLED
  requested_by, decided_by, decided_at, reason
  UNIQUE (project_id, user_id) WHERE status = 'PENDING'

legacy_membership_findings             -- báo cáo backfill chờ xác nhận (B6), không cấp quyền
  id, project_id, user_id, evidence jsonb, inferred_roles text[]
  status PENDING_CONFIRMATION | CONFIRMED | DISMISSED, decided_by, decided_at

contribution_events
  id, project_id, actor_id, actor_role (snapshot), action
  target_type, target_id, target_revision
  idempotency_key UNIQUE, created_at

edit_leases
  id, project_id, resource SCRIPT | COMPOSITION
  holder_id NULL, session_id NULL, token uuid NULL
  generation bigint NOT NULL              -- tăng ở mọi lần cấp/đổi chủ/force; không bao giờ reset
  acquired_at, renewed_at, expires_at
  UNIQUE (project_id, resource)
```

- Không thêm trạng thái "PROPOSED" vào `clip_projects`.
- Contribution ghi **trong cùng transaction** với hành động. Autosave không ghi event.

### B3. Luồng trạng thái

**Đề xuất project (M12).** DRAFT → PENDING → APPROVED / REJECTED (bắt buộc lý do) → sửa → PENDING; người gửi có thể rút (WITHDRAWN).
- Duyệt khóa dòng: thấy `APPROVED` và `project_id` đã có thì trả lại đúng project đó (idempotent).
- Lúc duyệt phải kiểm tra lại account và membership của **cả người gửi lẫn người duyệt** vẫn ACTIVE trong tổ.
- `POST /workspaces/{id}/scripts` (tạo trực tiếp) **không được bypass**: khi bật cờ, endpoint này chỉ còn là đường tạo nhanh cho Writer-Leader, và vẫn ghi một proposal APPROVED kèm `source`.

**Tham gia.** PENDING → APPROVED (tạo dòng, hoặc kích hoạt lại dòng REVOKED **cùng role**) / REJECTED / CANCELLED.
- Duyệt idempotent.
- Role khác với dòng REVOKED cũ → 409 `ROLE_IMMUTABLE`.
- Người duyệt không được là chính người xin (D12).

**Edit lease (M17), chống ghi muộn.**

```text
POST   /scripts/{id}/leases/{resource}        cấp nếu trống/hết hạn → {token, generation, expiresAt}
PUT    /scripts/{id}/leases/{resource}        gia hạn (token + generation khớp)
DELETE /scripts/{id}/leases/{resource}        nhả: holder/token = null, generation giữ nguyên
DELETE /scripts/{id}/leases/{resource}?force  Leader/Admin, bắt buộc lý do, audit, generation + 1
Write: header X-Edit-Lease: <token>:<generation>
```

- **Nguyên tử:** lệnh ghi đọc dòng lease bằng `SELECT … FOR SHARE` **trong cùng transaction** với lệnh ghi dữ liệu, và kiểm tra token, generation, `expires_at > now()`. Cấp/nhả/force dùng `FOR UPDATE`, nên không chen vào giữa lúc kiểm tra và lúc ghi.
- Token cũ không bao giờ hợp lệ lại, vì generation chỉ tăng và không reset khi nhả rồi cấp lại.
- Lỗi: 423 `LEASE_REQUIRED` / `LEASE_LOST`, `details: {holderName, expiresAt, generation}` để FE hiện read-only và giữ draft local.
- Lease cộng thêm vào revision (A4), không thay revision.

### B4. Ma trận quyền mục tiêu (server-side)

P-role = role trong `project_members` ACTIVE (account và membership tổ cũng ACTIVE). W-member = thành viên ACTIVE của tổ. "Hiện tại" là code tại `c7435c7` (quyền suy từ role workspace).

| Endpoint | Hiện tại | Mục tiêu |
|---|---|---|
| `GET /scripts/{id}`, versions, diff, review-history, qa-comments, qa-report, publications, render jobs | W-member | W-member (D13) |
| `PUT /scripts/{id}` (bản nháp) | WRITER | P-WRITER + lease SCRIPT |
| `GET /scripts/{id}/tts-preview` | WRITER / PRODUCER / REVIEWER | Như cũ theo P-role, **không cần lease** (chỉ đọc, nhưng tốn phí TTS nên vẫn yêu cầu P-role) |
| `POST /scripts/{id}/sync-durations` trên **bản nháp** | WRITER | P-WRITER + lease SCRIPT |
| `POST /scripts/{id}/sync-durations` trên **composition** | PRODUCER | P-PRODUCER + lease COMPOSITION |
| `PUT /scripts/{id}/composition` | PRODUCER | P-PRODUCER + lease COMPOSITION |
| `POST /scripts/{id}/submit-review` | WRITER | P-WRITER; có ≥ 1 P-REVIEWER (D14) |
| `POST /scripts/{id}/approve`, `request-changes` | REVIEWER | P-REVIEWER, **không phải tác giả version đó** |
| `POST /scripts/{id}/submit-video-review` | PRODUCER | P-PRODUCER |
| `POST /scripts/{id}/approve-video`, `send-back` | REVIEWER | P-REVIEWER, **không phải producer của revision đó** |
| `POST /scripts/{id}/qa-comments`, replies, resolve | W/P/R qua project | P-role bất kỳ; không cần lease (D17) |
| `PUT /qa-comments/{id}/reopen` | REVIEWER | P-REVIEWER |
| `DELETE /qa-comments/{id}` | tác giả hoặc Admin | Giữ nguyên |
| `POST /render/projects/{id}/start`, retry, cancel | PRODUCER | P-PRODUCER; Admin được retry/cancel (vận hành) |
| `POST /scripts/{id}/publish` | PRODUCER | D-PUB: P-PRODUCER hoặc Leader, video đã APPROVED |
| `DELETE /scripts/{id}` (lưu trữ) | owner / Leader / Admin | Giữ nguyên |
| `POST /ai/script/*` | chỉ cần đăng nhập | Có `projectId` thì P-WRITER; tạo mới (chưa có project) thì W-member WRITER. Giới hạn ngân sách theo user |
| `POST /workspaces/{id}/scripts` | W-member WRITER | Theo B3 (không bypass proposal) |
| Proposal / join / revoke / force-release lease | — | Theo B3; Leader của **đúng tổ** đó; Admin vận hành |

- **Admin bypass:** `isLeader()` và `hasProjectRole()` hiện trả `true` cho Admin. Cần tách hai loại:
  - vận hành (archive, retry/cancel render, force-release lease, sửa membership) → cho phép;
  - duyệt nội dung (approve/request-changes/approve-video/send-back) → **không cho**, trừ khi Admin cũng là P-REVIEWER.
- **Role selector ở FE chỉ là hiển thị.** Mọi quyết định quyền nằm ở BE.

### B5. API mới (phác thảo; contract chi tiết viết sau khi chốt B1)

```text
POST/GET  /workspaces/{id}/proposals          PATCH /proposals/{id}   (baseRevision)
POST      /proposals/{id}/submit | approve | reject | withdraw
GET       /scripts/{id}/members               POST /scripts/{id}/join-requests
POST      /join-requests/{id}/approve | reject | cancel
POST      /scripts/{id}/members/{memberId}/revoke
GET       /scripts/{id}/contributions         GET /me/project-roles
GET       /workspaces/{id}/leader/inbox | board   (phân trang, filter server-side)
```

### B6. Migration, backfill, rollout (sửa theo T03, T12)

1. **V23** (chỉ thêm): các bảng ở B2, kèm rollback, test PGlite và `PostgresConcurrencyTest`.
2. **Backfill** chạy dưới dạng job/CLI có **`--dry-run`** (mặc định), idempotent. Không chạy production trong task này.
   - **Chắc chắn** → ghi `project_members` (source `OWNER` / `LEGACY_BACKFILL`):
     - `clip_projects.owner_id` → WRITER;
     - người ra quyết định REVIEW_DECISION trong `status_histories` → REVIEWER;
     - `compositions.producer_id` → PRODUCER;
     - chỉ khi user đó có **đúng một** role suy ra trong project.
   - **Xung đột** (một user suy ra nhiều role) hoặc **thiếu bằng chứng** → chỉ ghi `legacy_membership_findings` với `PENDING_CONFIRMATION`, **không cấp quyền**. Leader/Admin xác nhận trên màn review.
   - Không thêm cả tổ làm participant. Không tạo approver hay contribution lịch sử giả.
   - Báo cáo dry-run gồm: số dòng chắc chắn, danh sách findings (project, user, bằng chứng), và số project không có participant nào.
3. **Cờ** (mặc định tắt):
   - `app.access.project-membership`: khi tắt, quyền ghi vẫn theo workspace và bảng mới chỉ được ghi song song. Điều kiện bật: FE xong màn tham gia/duyệt, findings đã xử lý hết, E2E pass.
   - `app.edit-lease.required`: khi tắt, write không có lease vẫn nhận nhưng ghi log. **Khi cờ còn tắt thì không được gọi là đã có bảo vệ.**
   - `app.draft.require-base-revision`: theo A4.5.
4. Mỗi migration: test DB tách biệt, có rollback. Phía LeTran chạy production **trước khi push BE**, vì production dùng `ddl-auto: validate`.

### B7. Test bắt buộc trước khi bật cờ

- **Quyền:** negative test **từng dòng B4** cho người ngoài tổ, W-member không tham gia, sai P-role, REVOKED, account bị khóa, Leader, Admin duyệt nội dung, và tự duyệt phần mình làm.
- **Đề xuất:** duyệt 2 lần → 1 project; từ chối không lý do → 400; người tổ khác → 403; người gửi bị khóa account lúc duyệt → 409.
- **Tham gia:** 2 request PENDING cùng lúc → một bị chặn; tham gia lại cùng role → kích hoạt dòng cũ; role khác → `ROLE_IMMUTABLE`.
- **Lease** (trên Postgres thật):
  - hai phiên xin cùng lúc → một thắng; hai tab cùng user là hai session;
  - tab crash → hết TTL thì người khác xin được;
  - token/generation cũ sau force hoặc đổi chủ → 423;
  - request ghi đến sau lúc mất lease → 423 và không ghi.
- **Backfill:** fixture có xung đột, thiếu bằng chứng, REVOKED; chạy 2 lần cho cùng kết quả; dry-run không ghi gì.
- **Contribution:** retry cùng `idempotency_key` → 1 event; rollback hành động → 0 event; đổi role/thu hồi không sửa event cũ.

### B8. Ticket (owner đề xuất; mọi ticket B cần các Dxx liên quan được xác nhận)

| # | Ticket | Owner | Phụ thuộc | Acceptance |
|---|---|---|---|---|
| A-6 | Log lưu bản nháp không có `baseRevision`; cờ bắt buộc (A4.5) | BE (Nemo) | — | Log có project/user; cờ bật → 400; test |
| A-7 | Coverage tính trạng thái account | BE | Chốt A-acct | Test khóa account làm mất role |
| B-1 | V23 + rollback + test PGlite/Postgres | BE | D08, D09, D15, D16 | `validate` pass, rollback chạy được |
| B-2 | Proposal (API, service, test) | BE | B-1, D12 | B7 phần đề xuất |
| B-3 | Join + project_members + revoke + rejoin | BE | B-1, D13, D15 | B7 phần tham gia |
| B-4 | `WorkspaceAccess` đọc P-role sau cờ; tách Admin vận hành/nội dung | BE | B-3, D-PUB | Negative test toàn bộ B4 |
| B-5 | Backfill dry-run + findings + màn xác nhận (FE) | BE + FE | B-3, D21 | B7 phần backfill |
| B-6 | Edit lease + header trên các write ở B4 | BE + FE | B-1, D16, D17 | B7 phần lease |
| B-7 | Contribution events | BE | B-3, D18 | B7 phần contribution |
| B-8 | API dashboard Leader | BE + FE | B-2, B-3 | Phân trang/filter/scope; số liệu cùng nguồn trạng thái thật |
| B-9 | Bật cờ trên staging, E2E 3 vai + Leader + non-participant, rồi production | LeTran (vận hành) | FE xong | 400/403/409/423, double-click, reload, mất mạng, request ghi muộn |
