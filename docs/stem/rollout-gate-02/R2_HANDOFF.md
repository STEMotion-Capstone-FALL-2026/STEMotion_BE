# R2.2 — Bàn giao bridge SQL

12/11/2026, Asia/Saigon. **Đã bàn giao hồ sơ · R2 chờ quyết định đóng · R3 BLOCKED.** Người dùng duyệt Gate 2 bằng tin “duyệt Gate 2”. Quyết định này mở bước bàn giao R2.2. Owner giữ quyền quyết định trạng thái R2 theo Q10 A.

R2 dùng baseline P0–P6 đã đóng, P6 kèm disposition. P7 còn PARTIAL. P8 đã đóng phần design/package và R1 đã đóng theo hồ sơ. Route R2 là auth `ALREADY_CUTOVER` và STEM `FRESH` theo snapshot R1.1. Release `NOT_READY`.

| Mục | Expected | Actual và giới hạn |
|---|---|---|
| R2.1 Bridge DDL | 11 bảng mới tương thích parent production | V24 tạo 9 bảng P3. V25 tạo 2 bảng P5. Đối chiếu tĩnh 19 FK UUID tới 10 bảng cha, 15 FK nội bộ, 7 index và 4 trigger. |
| R2.2 Auth | Đối chiếu authority, legacy, writer và epoch | V23 kiểm ledger và auth shape. Old writers, hidden consumers và epoch chưa xác nhận đầy đủ. `Conflict.C01 OPEN`. |
| R2.3 Ledger | Đánh số tuần tự và giữ provenance | V23–V25 là candidate Flyway. V23 kiểm baseline marker V1, 21 checksum V2–V22 và shared ledger 20261102131936 riêng. Cần xác nhận engine, config và transaction grouping. `Conflict.C02 OPEN`. |
| R2.4 Access | Backend-only cho đối tượng mới | V24/V25 thiết kế RLS và ACL cùng transaction, có guard quyền hiệu lực với API roles. `postgres` là owner candidate theo PF07. Effective backend role, membership và Data API còn thiếu evidence. |
| R2.5 Jobs và lineage | Kế hoạch checkpoint, drain/quarantine và legacy | Có quy trình đề nghị. 8 QUEUED, 6 COMPLETED, 2 FAILED, 2 outbox ACCEPTED, 6 clips và 3 publications là snapshot R1.1. Checkpoint cho rehearsal còn chờ. |
| R2.6 Runtime | Kiểm tương thích flags, workspace mode và G06 | Giữ contract OFF/ADVISORY và wire `baseRevision`. Effective JDBC UUID binding, search_path, bean graph và AI/worker refs còn `UNKNOWN`. |
| R2.7 Restore/rollback | Plan, oracle và quyết định readiness | Có kế hoạch transaction abort, containment/forward-fix hoặc restore theo epoch. Restore proof `NOT_RECEIVED`. `Bridge.BR01 OPEN`, R3 `BLOCKED`. |

| Migration | SHA-256 |
|---|---|
| [V23__assert_stem_bridge_prerequisites.sql](<C:/Users/LeTran/FPT University/2026_FALL/STEMotion/.audit/stem-phase-2026-11-05/rollout-gate-02/BRIDGE_SQL_R2/V23__assert_stem_bridge_prerequisites.sql>) | `d3ebb16fe9c262bf30b1c9d57f7ece38c4185fc29ad3244cd0e69d38b7d0815c` |
| [V24__create_stem_domain.sql](<C:/Users/LeTran/FPT University/2026_FALL/STEMotion/.audit/stem-phase-2026-11-05/rollout-gate-02/BRIDGE_SQL_R2/V24__create_stem_domain.sql>) | `5cfdb78bf3e8cc3af80f4beeb8ac159f20bd2d053e75b03fd553bd5a3c7bf502` |
| [V25__create_stem_artifact_lineage.sql](<C:/Users/LeTran/FPT University/2026_FALL/STEMotion/.audit/stem-phase-2026-11-05/rollout-gate-02/BRIDGE_SQL_R2/V25__create_stem_artifact_lineage.sql>) | `a7de399149abfb5289d37f5487b19edc6389ffda4ba0d6232adf83b17bf56cda` |

Review ở mức đối chiếu văn bản SQL và source. PostgreSQL parser/runtime, Flyway execution, ACL/UUID bindings trên candidate và rehearsal đều `NOT_RUN`. R2 mới soạn và review SQL, trạng thái triển khai là **chưa apply, chưa rehearsal, chưa merge, chưa deploy STEM bridge lên production**. [BRIDGE_REVIEW_R2.md](<C:/Users/LeTran/FPT University/2026_FALL/STEMotion/.audit/stem-phase-2026-11-05/rollout-gate-02/BRIDGE_REVIEW_R2.md>) · [logs/STATIC_REVIEW_R2.json](<C:/Users/LeTran/FPT University/2026_FALL/STEMotion/.audit/stem-phase-2026-11-05/rollout-gate-02/logs/STATIC_REVIEW_R2.json>) · [ROLLBACK_PLAN_R2.md](<C:/Users/LeTran/FPT University/2026_FALL/STEMotion/.audit/stem-phase-2026-11-05/rollout-gate-02/ROLLBACK_PLAN_R2.md>)

Đã kiểm lại SHA của 7 artifact trong manifest R2.1, inventory và toàn bộ input có file theo khóa Gate 1. 22/23 input §9 khớp, prompt R1 giữ `ALIAS_UNCONFIRMED` theo ngoại lệ đã duyệt. SHA-256 manifest R2.1 là `0d071c38e9849f260f9ff192ea62bc467230d08e6d659d84395413f4888190f0`. [SOURCE_MANIFEST_R2_BRIDGE.json](<C:/Users/LeTran/FPT University/2026_FALL/STEMotion/.audit/stem-phase-2026-11-05/rollout-gate-02/SOURCE_MANIFEST_R2_BRIDGE.json>)

BE/FE backup `9c7fdf5`/`4ddba1d` và parent `4fd7089`/`4dfa65e` là mốc đã xác minh ở R2.1. FE live drift và BE remote main `837c200` là quan sát lịch sử thuộc xử lý trước R4. Gói handoff gồm hai tài liệu trong D-R2. Quay lui tài liệu chỉ áp dụng cho delta R2 trong D-R2, với hồ sơ review và evidence FAIL được bảo toàn. R2 không có runner DB/PID cần cleanup. Nếu rollback bridge ở gate sau, ưu tiên abort trước commit hoặc containment/forward-fix và restore đúng epoch theo quyết định owner.

BM04 ghi gap RF09/10 đã báo trước hạn **17/11/2026**, RF11/12/13 **24/11/2026** và RF08 YouTube **29/11/2026**. Chuyên môn ba cấp `NOT_RECEIVED` và brief `DRAFT/UNVERIFIED`. Report4 corrupt, BM02/BM04 clipping, TOC và registration/native authority tiếp tục theo phạm vi hồ sơ riêng.

Bàn giao tại R2.2. Đề nghị trình scope R3 rehearsal khi các điều kiện đầu vào được xác nhận và có quyết định riêng. [BRIDGE_READINESS_R2.md](<C:/Users/LeTran/FPT University/2026_FALL/STEMotion/.audit/stem-phase-2026-11-05/rollout-gate-02/BRIDGE_READINESS_R2.md>)
