# R2.1 — Kế hoạch drain, quarantine và rollback

12/11/2026, Asia/Saigon. **Kế hoạch để review. Rehearsal và restore proof chưa thực hiện.** Lê Trần là owner/operator/rollback authority theo R1 Q02 A. Các thao tác dưới đây thuộc gate execution riêng sau khi có target, dataset, cửa sổ và quyền tương ứng.

## Checkpoint trước apply hoặc mở writers

1. Khóa target project/schema, refs BE/FE/AI/worker, renderer/protocol/hash versions, PostgreSQL/tooling, hai ledger, effective app/migration roles và ACL/Data API settings. Đối chiếu lại metadata vì R1.1 là snapshot lịch sử. Ghi approved window, freeze epoch, RPO/RTO, retention, monitor thresholds và người có quyền dừng.
2. Lập danh sách job/attempt/callback/outbox tại checkpoint. Ghi job ID, epoch, attempt/token reference đã lọc, trạng thái, artifact/snapshot/hash refs và mốc thời gian. Phân biệt counts lịch sử (8 QUEUED/6 COMPLETED/2 FAILED, outbox 2 ACCEPTED) với counts checkpoint. Bằng chứng chia sẻ chỉ giữ metadata, hash hoặc aggregate phù hợp.
3. Dừng admission cho writer và dispatch theo quy trình owner duyệt. Phân loại các QUEUED ở checkpoint để chọn drain bằng bộ writer/worker cùng epoch hoặc hold/quarantine trước cutover. Xác nhận cả active attempts, callback có thể đến trễ và trạng thái outbox. Số job QUEUED bằng 0 chỉ là một tín hiệu. Gate còn cần owner chốt disposition từng nhóm và đường tiếp nhận callback.
4. Cô lập attempt/callback thiếu lineage, sai epoch hoặc chưa xác nhận binding để owner quyết. Callback có mismatch đã biết vẫn hard 409. Thiếu manifest giữ UNKNOWN và đi theo explicit UNVERIFIED/ack của contract khi hợp lệ, hoặc hold. COMPLETED/FAILED và outbox ACCEPTED giữ lịch sử để đối chiếu. Không có dữ liệu sidecar được suy ngược từ 6 clips hoặc 3 publications legacy. Thư viện giữ legacy raw labels. STEM mới dùng frozen publication đúng ref khi có đủ snapshot.
5. Chỉ cho phép mở writers khi kiểm tra đồng thời flags, schema, permissions, checkpoint và callback compatibility đạt. Hai flags tách khỏi workspace OFF/ADVISORY. Workspace OFF giữ hard mismatch/media guards và frozen lineage.

## Restore proof phải có trước apply

Restore proof phải đúng target, scope dữ liệu và epoch, với RPO/RTO đo được, quyền giải mã/custodian, retention và evidence đã lọc. Bao gồm hoặc có recovery method riêng cho schema/data/ACL, managed auth/storage/external assets, BE/FE/AI/worker artifacts và queue/outbox. Backup public-only 02/11 và stand-in roles chỉ là evidence lịch sử. R3 cần scope, môi trường rehearsal và dataset được duyệt trước khi tạo backup/restore hoặc chạy SQL. Báo cáo proof ghi backup ID, time, hash, quy trình tái tạo, thời gian và kết quả đối chiếu. Proof hiện `NOT_RECEIVED`.

| Oracle rehearsal cần đạt | Evidence yêu cầu |
|---|---|
| Ledger và DDL | Transaction grouping, V23 guard trên mismatch, đối chiếu V1–V22 và auth ledger, đúng 11 bảng, FK/constraint/index/trigger/ACL, 0 orphan. |
| Auth và quyền | Authority/session epoch, old-writer retirement, effective backend role đọc/ghi, client role denied, runtime search_path và Data API exposure đã xác nhận. |
| Dữ liệu và contract | UUID bindings, CAS G06, snapshot immutable, idempotency, valid callback/duplicate/mismatch, legacy labels và frozen publication lineage. |
| Restore | Restore và đối chiếu auth/schema/data/roles, side effects đã gửi và pending jobs/attempts. Đo RPO/RTO theo tiêu chí owner. |

## Điểm dừng và quay lui

| Tình trạng | Phản ứng dự kiến sau khi có gate riêng |
|---|---|
| Guard hoặc DDL lỗi trước commit | Abort transaction. Xác minh ledger, schema, ACL, locks và writers. Lưu log đã lọc. Quay lui tự động chỉ bao trùm các migration cùng transaction. |
| Một version đã commit, version sau lỗi, writers chưa mở | Giữ freeze và flags theo trạng thái được duyệt. Đối chiếu từng version, table shape và ACL. Owner quyết forward-fix hoặc quay lui bằng restore đã chứng minh. |
| Bridge đã commit và đã có writes | Dừng writer admission/callback chịu ảnh hưởng. Bảo toàn snapshots, hash, approvals, job attempts và evidence. Ưu tiên containment/forward-fix giữ reader tương thích. Nếu cần restore, owner duyệt reconciliation các write và side effects sau backup trước khi thực hiện. |
| Unauthorized access, mismatch, media FAIL, auth smoke fail hoặc monitor vượt ngưỡng | Hold rollout và giữ audit. Cô lập flow hoặc attempt theo quyền đã duyệt. Giữ hard 409/media FAIL và lineage. |

Kế hoạch chọn transaction abort hoặc quy trình restore/forward-fix theo epoch. Các hướng dẫn giữ history và các đối tượng đã ghi dữ liệu. Không có script down/drop/repair. Owner phải xác minh rằng việc dừng hoặc đổi bean không chuyển read path frozen sang mutable metadata khi đã có STEM writes.

Quay lui tài liệu R2.1 chỉ trong D-R2. Giữ inventory đã khóa, file/hash/log review và mọi FAIL phục vụ đối chiếu. R2.1 chỉ dùng thao tác file và Git read-only, không tạo process DB hoặc runner.
