# Step0 — Callsite repository trước sửa

Gate PASS trước production edit:38field accesses/34lines, tất cả tại src/main/java/com/stemotion/domain/stem/StemService.java. Không field caller ngoài hai class đã duyệt. StemPostgresTest có injection/method calls, không đọc public fields.

| Dòng source trước sửa | Accesses | Fields |
|---|---|---|
| 33 | 1 | db.jdbc |
| 35 | 1 | db.jdbc |
| 40 | 1 | db.jdbc |
| 41 | 1 | db.jdbc |
| 44 | 1 | db.jdbc |
| 46 | 1 | db.json |
| 50 | 1 | db.jdbc |
| 53 | 1 | db.json |
| 61 | 2 | db.jdbc, db.jdbc |
| 63 | 1 | db.jdbc |
| 65 | 1 | db.jdbc |
| 68 | 1 | db.jdbc |
| 69 | 1 | db.jdbc |
| 78 | 1 | db.jdbc |
| 83 | 1 | db.jdbc |
| 86 | 1 | db.jdbc |
| 98 | 2 | db.jdbc, db.jdbc |
| 99 | 1 | db.json |
| 106 | 1 | db.jdbc |
| 108 | 1 | db.jdbc |
| 112 | 1 | db.json |
| 114 | 1 | db.jdbc |
| 115 | 1 | db.jdbc |
| 116 | 1 | db.jdbc |
| 119 | 1 | db.jdbc |
| 121 | 2 | db.jdbc, db.jdbc |
| 127 | 1 | db.json |
| 135 | 1 | db.jdbc |
| 137 | 1 | db.jdbc |
| 138 | 1 | db.jdbc |
| 145 | 1 | db.json |
| 155 | 2 | db.jdbc, db.jdbc |
| 157 | 1 | db.jdbc |
| 158 | 1 | db.jdbc |

Full source text/tất cả type references tại CALLSITE_INVENTORY.json; byte snapshot StemService.java.before/StemRepository.java.before và INPUT_BASELINE.json. Đường dẫn được chuẩn hóa POSIX sau preflight assertion path-format Windows, không có caller ngoài scope và chưa sửa production trước gate.
