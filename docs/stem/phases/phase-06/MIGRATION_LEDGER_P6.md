# P6 — Ledger local/test/shared

06/11/2026. **Không migration/DDL/index mới** theo Q09 A. Query đọc frozen PUBLICATION sidecar hiện hữu bằng tham số và JSONB; không materialized projection/table mới. Không tạo V93000 placeholder hoặc sử dụng candidate range.

- P3 TEST_ONLY V91000 và P5 TEST_ONLY V92000 giữ bytes; V1–V22/V9000/V9001 giữ bytes/history. Không repair/outOfOrder/alter/UPDATE immutable snapshot hoặc promote fixture.
- Các context regression P3 dùng 25 migrations; full P5/P6 và live runner dùng 26 (22 baseline + 2 auth test fixtures + P3 + P5). Không đòi schema P6 trên feature-off startup.
- Không DB shared/staging/production, dump restore, auth cutover hay kiểm cloud Flyway mới. C01/C02/BR-01/cloud Flyway OFF và V23/V24/V25+ đề xuất giữ nguyên.
- P6 không có migration cần rollback; context DB test được đóng bằng lifecycle runner. Quyết production indexing/scale/execution-plan trên dữ liệu thật chưa được thực hiện; nếu cần persistence/index phải gate riêng, không suy test small fixture thành production performance proof.
