# API contract — Phần A (FE 9.7)

Ngày: **29/10/2026**.
- `c7435c7` (đã push): workspace, member, `draftRevision`/`baseRevision`.
- `7f207bb` (đã push): `STALE_REVISION` có `details`, sync kiểm revision nguyên tử.
- Bổ sung local 30/10/2026 (chưa push): sync kiểm cả scriptVersionId và revision ở bước ghi (A-8).
- Thiết kế và lý do: `docs/DESIGN_MEMBERSHIP_APPROVAL_2026-10-26.md` phần A.

Tất cả endpoint nằm dưới `/api/v1`, header `Authorization: Bearer <jwt>`.

## 0. Error envelope

Mọi lỗi có cùng shape. `details` **chỉ có** ở lỗi mang dữ liệu máy đọc được (hiện chỉ `STALE_REVISION`):

```json
{
  "status": 409,
  "code": "STALE_REVISION",
  "message": "The draft is now at revision 7 (your save started from 6); reload it before trying again",
  "timestamp": "2026-10-29T10:15:00",
  "details": { "resource": "SCRIPT_DRAFT", "currentRevision": 7, "baseRevision": 6 }
}
```

| HTTP | `code` | Khi nào |
|---|---|---|
| 400 | `BAD_REQUEST` | Vi phạm luật nghiệp vụ (thiếu role, trùng user, mất role…). `message` đọc được, hiện thẳng cho người dùng |
| 400 | `VALIDATION_FAILED` | Thiếu trường bắt buộc (`name`, `leaderUserId`, `userId`, `role`…) hoặc `baseRevision < 0` |
| 403 | `ACCESS_DENIED` | Bị chặn ở `@PreAuthorize` (vd. Leader gọi thêm/xóa thành viên) |
| 403 | `FORBIDDEN` | Bị chặn trong service (vd. Leader đổi `status`/`isLeader`) |
| 404 | `RESOURCE_NOT_FOUND` | Workspace/user/member không tồn tại |
| 409 | `CONFLICT` | User đã là thành viên; project bị khóa nghiệp vụ… |
| 409 | `STALE_REVISION` | Lưu/sync dựa trên revision cũ. **Không tự retry**: giữ bản local, cho người dùng tải lại/đối chiếu |

`details.resource`:
- `SCRIPT_DRAFT`: bản nháp kịch bản, đọc lại bằng `GET /scripts/{id}`;
- `COMPOSITION`: bản dựng, đọc lại bằng `GET /scripts/{id}/composition`.

`details.currentRevision` có thể là `null` (hai lần lưu đầu tiên đua nhau). Khi đó tải lại để lấy số mới.

## 1. Tạo workspace — `POST /workspaces` (Admin)

```json
{
  "name": "Tổ Tin học",
  "department": "Tin học",
  "leaderUserId": "8f0c…",
  "leaderRole": "WRITER",
  "members": [
    { "userId": "1a2b…", "role": "REVIEWER", "isLeader": false },
    { "userId": "3c4d…", "role": "PRODUCER" }
  ]
}
```

| Trường | Bắt buộc | Ghi chú |
|---|---|---|
| `name`, `leaderUserId` | ✅ | |
| `leaderRole` | | Mặc định `WRITER`. Chỉ nhận `WRITER`/`REVIEWER`/`PRODUCER` |
| `members[].userId`, `members[].role` | ✅ (trong mỗi phần tử) | `isLeader` tùy chọn, có thể có nhiều Leader |

**201** `WorkspaceResponse`:

```json
{ "id": "…", "name": "Tổ Tin học", "department": "Tin học", "membersCount": 3, "activeProjects": 0, "myRole": null, "isLeader": null }
```

**400**, không có gì được ghi:

| Trường hợp | `message` (bắt đầu bằng) |
|---|---|
| Tổng < 3 người | `A workspace needs at least 3 different members …` |
| Thiếu role | `A workspace needs at least one WRITER, REVIEWER and PRODUCER; missing [PRODUCER]` |
| Trùng user (kể cả trùng leader) | `User <id> is listed more than once` |
| User không ACTIVE | `User <tên> is not active and cannot join a workspace` |
| Role `ADMIN` | `A workspace role must be WRITER, REVIEWER or PRODUCER, not ADMIN` |

`PUT /workspaces/{id}` (đổi tên) dùng cùng body nhưng **bỏ qua** `members`.

## 2. Thành viên

| Endpoint | Admin | Leader | Thành viên khác |
|---|---|---|---|
| `GET /workspaces/{id}/members` | ✅ | ✅ | ✅ (chỉ đọc) |
| `GET /workspaces/{id}/candidates` | ✅ | 403 `ACCESS_DENIED` | 403 |
| `POST /workspaces/{id}/members` `{userId, role, isLeader?}` | ✅ 201 | 403 `ACCESS_DENIED` | 403 |
| `DELETE /workspaces/{id}/members/{memberId}` | ✅ 204 | 403 `ACCESS_DENIED` | 403 |
| `PATCH /workspaces/{id}/members/{memberId}` `{role?, status?, isLeader?}` | ✅ | chỉ đổi `role` | 403 |

`MemberResponse`:

```json
{ "id": "…", "userId": "…", "name": "Trần B", "email": "b@…", "role": "REVIEWER", "isLeader": false, "status": "ACTIVE" }
```

PATCH:
- Leader gửi `status`/`isLeader` **khác giá trị hiện tại** → 403 `FORBIDDEN`, không trường nào bị đổi. Gửi lại đúng giá trị cũ thì hợp lệ.
- Làm một role đang có người ACTIVE không còn ai → 400 `This change would leave the workspace without an active [REVIEWER]; give that role to another member first`. Áp dụng cho cả DELETE và PATCH `status=INACTIVE`.
- Bỏ/vô hiệu hóa Leader ACTIVE cuối cùng → 400 `…last active leader…`.
- `memberId` thuộc tổ khác → 400.

FE dùng hai capability riêng: Admin (đủ thao tác) và Leader (chỉ đổi role). **Không** dùng một cờ chung kiểu `canManageMembers`.

## 3. Bản nháp kịch bản

`GET /scripts/{id}` và `PUT /scripts/{id}` trả `ScriptResponse` có thêm:

| Trường | Kiểu | Ghi chú |
|---|---|---|
| `draftRevision` | integer, luôn có, ≥ 0 | Revision của bản nháp hiện tại. Version mới (sau "yêu cầu sửa") bắt đầu từ 0 |

`PUT /scripts/{id}`:

```json
{ "scenes": [ … ], "baseRevision": 6 }
```

- `baseRevision` = `draftRevision` lần đọc/lưu gần nhất. **Hiện tùy chọn**, sẽ bắt buộc theo lộ trình A4.5 của tài liệu thiết kế. Không gửi thì không được bảo vệ khỏi ghi đè.
- Khớp → 200, `draftRevision` = cũ + 1. Lần lưu sau dùng số mới.
- Không khớp → 409 `STALE_REVISION`, `details.resource = "SCRIPT_DRAFT"`, bản nháp trên BE giữ nguyên.
- Chỉ sửa được khi `scriptStatus` là `DRAFT` hoặc `CHANGE_REQUESTED` (ngoài ra 400).

`draftRevision` (bản nháp) và `revision` của composition là **hai bộ đếm khác nhau**. Không dùng lẫn.

## 4. Đồng bộ thời lượng — `POST /scripts/{id}/sync-durations`

```json
{ "baseRevision": 6 }
```

| Trạng thái kịch bản | Quyền | `baseRevision` | Kết quả |
|---|---|---|---|
| `DRAFT` / `CHANGE_REQUESTED` | WRITER | Tùy chọn, so với `draftRevision`. Nên gửi | `target: "SCRIPT"`, `revision` = **`draftRevision` mới** |
| `APPROVED` | PRODUCER | **Bắt buộc**, so với composition `revision` | `target: "COMPOSITION"`, `revision` = composition revision mới |
| `IN_REVIEW` | — | — | 400 |

```json
{
  "target": "SCRIPT",
  "revision": 8,
  "totalDurationSeconds": 74,
  "scenes": [ { "id": "…", "durationInFrames": 96, "captionWords": [ … ] } ],
  "captionSync": { "status": "ALIGNED" }
}
```

409 `STALE_REVISION` khi:
- `baseRevision` cũ ngay lúc gửi → BE từ chối **trước khi gọi TTS**;
- bản nháp/bản dựng bị lưu trong lúc worker đang đo (vài giây đến vài phút) → kết quả đo **không được ghi**.
- phiên bản kịch bản đổi trong lúc worker đo, kể cả version mới có cùng revision → kết quả đo **không được ghi**.

Trường hợp đổi version: `details` thêm `baseVersionId` và `currentVersionId` bên cạnh resource/revisions.
Đây là ID nội bộ của ScriptVersion, không phải số versionNo. Request FE không cần thêm trường:
BE chụp ID trước khi gọi worker và kiểm lại dưới row lock trong transaction ghi ngắn.
Nếu không còn dòng current sau khi chờ lock, trả 409 CONFLICT và yêu cầu reload,
không fallback sang version mới nhất không có lock. Không giữ transaction/row lock trong lúc gọi TTS.
Trạng thái của version đã khóa cũng được kiểm lại: draft vừa chuyển IN_REVIEW hoặc version
composition không còn APPROVED bị từ chối 409 CONFLICT, kể cả project được đọc trước đó còn trạng thái cũ.

Khi gặp: tải lại, rồi sync lại nếu vẫn cần.

## 5. Tương thích và rollout

- Mọi thay đổi chỉ **thêm trường**; client cũ đọc được.
- Client cũ không gửi `baseRevision` vẫn lưu được cho tới khi bật `app.draft.require-base-revision` (A4.5).
- Local 30/10 có `DRAFT_REQUIRE_BASE_REVISION` mặc định false cho rollout A4.5.
  Bảo vệ A-8 ở bước ghi sync luôn áp dụng, không có cờ tắt. Phần B dùng cờ theo thiết kế B6.
- Migration: V22 (đã có trên production). Phần này không có migration mới.
