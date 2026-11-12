# R2 — Readiness cho R3 rehearsal

12/11/2026, Asia/Saigon. **R3 BLOCKED · Release NOT_READY.** Gate 2 đã duyệt bước bàn giao. Bridge V23–V25 có review tĩnh và SHA đã xác minh. SQL vẫn ở trạng thái `NOT_APPLIED`. [R2_HANDOFF.md](<C:/Users/LeTran/FPT University/2026_FALL/STEMotion/.audit/stem-phase-2026-11-05/rollout-gate-02/R2_HANDOFF.md>)

| Điều kiện | Evidence hiện có | Phần còn thiếu |
|---|---|---|
| Target và scope rehearsal | R1.1 khóa target tham chiếu, R2.1 dùng snapshot đó | Duyệt môi trường/dataset rehearsal riêng và đối chiếu lại schema/epoch phù hợp. |
| Auth / Conflict.C01 | V23 kiểm shape và sự hiện diện của cutover ledger | Xác nhận writer cũ, hidden consumers, session/role epoch và tương thích authority. `OPEN`. |
| Ledger / Conflict.C02 | V1 marker, 21 checksum V2–V22 và shared auth ledger độc lập | Xác nhận Flyway version, locations, ledger quyền đọc, checksum provenance và cách xử lý commit một phần. `OPEN`. |
| Schema / Bridge.BR01 | 11 bảng, 34 FK, 7 index, 4 trigger đã review tĩnh | PostgreSQL parse/runtime, transaction và postcondition trên target rehearsal đã duyệt. `OPEN`. |
| Quyền | DDL candidate owner-only, RLS và ACL cho đối tượng mới | Effective backend session, quyền đọc/ghi, API role negatives, membership và Data API exposure. |
| Runtime và refs | Contract G06 và startup flags đã đối chiếu source | UUID binding, search_path, bean graph và ref AI/worker đúng candidate. FE drift và BE main mới cần disposition trước R4. |
| Job/callback và lineage | Kế hoạch phân loại theo checkpoint và epoch | Xác nhận checkpoint mới, drain/quarantine signoff và callback/legacy oracles. |
| Restore và rollback | Kế hoạch abort trước commit hoặc containment/forward-fix/restore theo epoch | Restore proof `NOT_RECEIVED`. Cần proof đúng scope/epoch trước apply, cửa sổ, RPO/RTO đo được, retention và ngưỡng monitor. |

Owner/operator/rollback authority theo R1 là Lê Trần. Các bước làm trên DB thuộc scope và approval riêng. Trình kế hoạch rehearsal khi có evidence cho điều kiện đầu vào. [ROLLBACK_PLAN_R2.md](<C:/Users/LeTran/FPT University/2026_FALL/STEMotion/.audit/stem-phase-2026-11-05/rollout-gate-02/ROLLBACK_PLAN_R2.md>) · [BRIDGE_REVIEW_R2.md](<C:/Users/LeTran/FPT University/2026_FALL/STEMotion/.audit/stem-phase-2026-11-05/rollout-gate-02/BRIDGE_REVIEW_R2.md>)

Hồ sơ R2 gồm SQL đã soạn và review. Rehearsal, apply, merge và deploy bridge vẫn `NOT_RUN`. Quyết định đóng R2 thuộc người dùng. BM04 giữ các mốc **17/11**, **24/11** và **29/11/2026** cùng gap đã báo. Expert/docs blockers giữ trong phạm vi chuyên môn và hồ sơ.
