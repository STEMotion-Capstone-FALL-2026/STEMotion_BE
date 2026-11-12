# R2.1 — Review bridge SQL trình Gate 2

12/11/2026, Asia/Saigon. **GATE_2_PENDING · STATIC REVIEW · SQL NOT APPLIED.** Người dùng duyệt Gate 1 bằng tin “duyệt Gate 1”. Gói dùng route auth `ALREADY_CUTOVER` và STEM `FRESH` theo snapshot R1.1. P0–P6 đã đóng theo xác nhận, P6 kèm disposition. Release `NOT_READY`.

| File SQL | Nội dung đã soạn | SHA-256 |
|---|---|---|
| [V23__assert_stem_bridge_prerequisites.sql](<C:/Users/LeTran/FPT University/2026_FALL/STEMotion/.audit/stem-phase-2026-11-05/rollout-gate-02/BRIDGE_SQL_R2/V23__assert_stem_bridge_prerequisites.sql>) | Guard PostgreSQL 17, owner candidate, ledger, auth shape, 10 parent UUID PK và collision 11 bảng/hàm. V23 có điều kiện dừng thực. | `d3ebb16fe9c262bf30b1c9d57f7ece38c4185fc29ad3244cd0e69d38b7d0815c` |
| [V24__create_stem_domain.sql](<C:/Users/LeTran/FPT University/2026_FALL/STEMotion/.audit/stem-phase-2026-11-05/rollout-gate-02/BRIDGE_SQL_R2/V24__create_stem_domain.sql>) | Tạo 9 bảng P3, 2 index và RLS/ACL cùng migration. | `5cfdb78bf3e8cc3af80f4beeb8ac159f20bd2d053e75b03fd553bd5a3c7bf502` |
| [V25__create_stem_artifact_lineage.sql](<C:/Users/LeTran/FPT University/2026_FALL/STEMotion/.audit/stem-phase-2026-11-05/rollout-gate-02/BRIDGE_SQL_R2/V25__create_stem_artifact_lineage.sql>) | Tạo 2 bảng lineage P5, 5 index, 4 trigger và RLS/ACL cùng migration. | `a7de399149abfb5289d37f5487b19edc6389ffda4ba0d6232adf83b17bf56cda` |

## R2.1 — Kiểu dữ liệu và ràng buộc

19 FK tới 10 bảng cha core đều dùng UUID, khớp Q01/PF05. 15 FK nội bộ dùng `varchar(36)` để giữ contract ID STEM do ứng dụng tạo. Các trường provenance như `original_scene_id` và `target` giữ dạng text. FK scene dùng `ON DELETE SET NULL` để giữ evidence gốc khi mất scene. Khóa unique cho brief theo version, assessment theo decision, receipt và rate-limit giữ theo consumer. Policy/catalog/brief còn DRAFT, assessment UNVERIFIED, workspace chỉ OFF/ADVISORY. Gói DDL để trống catalog, settings và sidecars cho tới khi có quyết định dữ liệu riêng.

V25 giữ JSON limits, unique theo job/clip/publication/decision, proof-version uniqueness và stage guards. Hai bảng append-only có trigger UPDATE/DELETE và thêm hai trigger TRUNCATE ở cấp statement. Trigger là SECURITY INVOKER với search path giới hạn. Đây là bảo vệ DML trong hoạt động thông thường. Hiệu lực dưới owner, trạng thái trigger và quyền thao tác DDL thuộc kiểm chứng R3.

Candidate chỉ lưu `UNKNOWN` hoặc `BINDING_ONLY` ở cột assurance. `TEST_ONLY_SYNTHETIC` chỉ còn trong fixture và có thể xuất hiện ở payload manifest theo contract hiện tại. Source callback ánh xạ manifest hiện hữu thành `BINDING_ONLY` và thiếu manifest thành `UNKNOWN`. Giới hạn assurance này cần được kiểm chứng qua callback/QA/publish ở R3. `BINDING_ONLY` biểu thị binding của lineage, độc lập với kiểm chứng bytes/pixel/audio.

Java lưu ID ở dạng String. Source cloud và default cấu hình PgJDBC `stringtype: unspecified`. Library join dùng `publication_id` kiểu UUID. Source cho cơ sở đối chiếu, còn effective JDBC config, UUID bind khi insert/CAS/join và effective `search_path` cần chứng minh bằng candidate thực ở R3. DDL dùng tên schema public đầy đủ.

## R2.2 — Conflict.C01 và auth

| Hạng mục | Evidence và disposition |
|---|---|
| Ledger và shape | Q02 thấy shared auth ledger `20261102131936`. Q01/Q03 ghi kiểu/nullability của username, system_role, auth_version, version và legacy role cùng check system_role. V23 kiểm presence/type/nullability, check đã validate và các bảng auth/mail đã quan sát. |
| Authority | Source clone dùng systemRole/authVersion và workspace seat. Q07 aggregate tại R1.1 ghi 0 system role không hợp lệ và 0 auth_version null/âm. Kết quả áp dụng snapshot R1.1. |
| Legacy và hidden consumers | Q05 ghi hash của 4 public functions, 0 views/policies trong phạm vi đọc và 0 function body khớp pattern legacy. Q08 ghi dependency `public.users.role` tới enum legacy. Cần owner rà semantic, external writer, API/job/worker đang chạy và xác nhận route trước R3. |
| Epochs | Checkpoint cần bind BE/FE/AI/worker refs, authority model, phiên đang sống, auth_version và hàng đợi mail. Cần disposition cho writer cũ trước khi cho phép writes. DDL STEM additive giữ nguyên auth rows, enum, function và lịch sử cutover. |

`Conflict.C01 OPEN`. Source và snapshot chưa chứng minh đã retire mọi writer cũ.

## R2.3 — Conflict.C02 và migration engine

V23–V25 dùng định dạng migration versioned Flyway theo Q02 A. **Candidate engine là Flyway chạy riêng dưới quy trình rollout được duyệt**, sử dụng ledger hiện hữu `public.flyway_schema_history`. Cloud startup hiện cấu hình Flyway OFF. Auth shared ledger chỉ là điều kiện đầu vào và giữ provenance riêng. V23 đối chiếu baseline marker V1 và 21 cặp script/checksum V2–V22 theo snapshot. V24/V25 chỉ thêm STEM. Hai ledger và V1–V22 giữ nguyên theo scope.

R3 cần khóa phiên bản Flyway, validate history/migration locations, default schema, quyền đọc cả hai ledger và transaction grouping. Trình tự đề nghị là V23 → V24 → V25 trong một nhóm transactional trên PostgreSQL. Nếu engine không bảo đảm nhóm transaction, cần disposition riêng cho trạng thái commit một phần trước khi apply. V23 có thể đã commit trong lần chạy từng migration, vì vậy lần resume phải chạy lại preflight tương ứng và đối chiếu trạng thái từng version trước khi tiếp tục. `Conflict.C02 OPEN` tới khi owner xác nhận engine, cấu hình và provenance đúng target. SHA-256 file review tách biệt Flyway checksum do engine tạo khi chạy.

## R2.4 — Access và isolation

Mỗi file tạo bảng bật RLS và thu hồi quyền đối tượng mới trong cùng transaction. SQL thu hồi PUBLIC và mọi direct grant ngoài owner do default ACL tạo ra, rồi kiểm ACL và quyền hiệu lực của anon, authenticated, service_role và authenticator nếu tồn tại. V25 cũng thu hồi EXECUTE của hàm trigger. Không tạo policy cho client. Quyền trên sáu bảng core chưa bật RLS và default ACL cấp client là open item riêng. SQL candidate chỉ dùng quyền trên các đối tượng mới.

Owner-only là phương án review dựa trên role prefix và ownership `postgres` trong PF07. Role owner/BYPASSRLS có thể bỏ qua RLS. Effective runtime role, membership và Data API settings vẫn chưa xác minh. Nếu phiên app thực khác owner candidate hoặc Data API có role/đường truy cập chưa kiểm, dừng để chọn lại thiết kế. R3 phải xác nhận backend đọc/ghi đúng bằng phiên đã duyệt và thử negative bằng các role thực. [PostgreSQL 17 RLS](https://www.postgresql.org/docs/17/ddl-rowsecurity.html) · [PostgreSQL 17 privileges](https://www.postgresql.org/docs/17/ddl-priv.html) · [Supabase Data API](https://supabase.com/docs/guides/api/securing-your-api)

## R2.5–R2.7 — Vận hành, rollback và readiness

R1.1 quan sát 8 QUEUED, 6 COMPLETED, 2 FAILED, 2 outbox ACCEPTED, 6 clips và 3 publications. Kế hoạch checkpoint và phân loại theo epoch nằm trong rollback plan. Tính nhất quán callback/queue và lineage legacy là điều kiện trước khi mở lại writers. [ROLLBACK_PLAN_R2.md](<C:/Users/LeTran/FPT University/2026_FALL/STEMotion/.audit/stem-phase-2026-11-05/rollout-gate-02/ROLLBACK_PLAN_R2.md>)

`app.stem.enabled` và `app.stem.artifact.enabled` là startup flags, còn OFF/ADVISORY là workspace mode. G06 vẫn wire `baseRevision`, alias nội bộ FE `modeRevision`. Artifact bật riêng khi STEM tắt là cấu hình không tương thích theo source. Runtime flags/bean graph và AI/worker live refs vẫn `UNKNOWN` theo PF10. FE ref drift thuộc disposition trước R4 theo Q07 A. Restore proof `NOT_RECEIVED` theo PF11, cần trước apply theo Q08 A.

| Gate phụ thuộc | Trạng thái |
|---|---|
| Bridge.BR01 | OPEN. Có 3 file SQL để review. PostgreSQL/Flyway parse và thực thi, ACL thực, UUID binding, callback/lineage và restore đều chưa thử. |
| R3 | BLOCKED. Cần duyệt scope/target rehearsal, khớp role/ledger, checkpoint/epoch, window/RPO/RTO và restore proof đúng epoch trước apply. |
| R4/R5 | BLOCKED. Cần xử lý FE ref drift, BE main 837c200 quan sát C.1, working tree gốc, candidate regression và monitoring. |

## Bằng chứng và trạng thái gate

Review tĩnh đối chiếu 11 bảng, 34 FK, 7 index tường minh, 4 trigger, 21 dòng checksum và ACL cho đủ 11 bảng. Đây là kiểm tra cấu trúc trên văn bản SQL và evidence local. Các statement chưa qua PostgreSQL parser/runtime. Các snapshot R1, manifest R2.0 và hash tracked clone đã được kiểm lại. [logs/STATIC_REVIEW_R2.json](<C:/Users/LeTran/FPT University/2026_FALL/STEMotion/.audit/stem-phase-2026-11-05/rollout-gate-02/logs/STATIC_REVIEW_R2.json>) · [SOURCE_MANIFEST_R2_BRIDGE.json](<C:/Users/LeTran/FPT University/2026_FALL/STEMotion/.audit/stem-phase-2026-11-05/rollout-gate-02/SOURCE_MANIFEST_R2_BRIDGE.json>)

Mapping PF01–PF13, case và AT kế thừa nằm trong review notes. AT01–24 giữ PARTIAL, AT05/07/16 packet-only. Chuyên môn cả ba cấp còn `NOT_RECEIVED`. BM04 ghi RF09/10 **17/11/2026**, RF11/12/13 **24/11/2026**, RF08 YouTube **29/11/2026**. Các gap và quyết định RF theo hồ sơ riêng. [BRIDGE_SQL_R2/REVIEW_NOTES.md](<C:/Users/LeTran/FPT University/2026_FALL/STEMotion/.audit/stem-phase-2026-11-05/rollout-gate-02/BRIDGE_SQL_R2/REVIEW_NOTES.md>)

**Dừng tại Gate 2 để review SQL, kế hoạch và các blocker.** Handoff R2.2 thuộc chặng sau Gate 2.
