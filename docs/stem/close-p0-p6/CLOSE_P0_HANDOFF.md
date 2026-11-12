# P0 — Baseline môi trường

Ngày 12/11/2026, Asia/Saigon. Người dùng duyệt Gate 2 và đồng ý disposition C.1 bằng tin “duyet gate 2, dong y disposition”. Quyết định đóng từng phase thuộc người dùng sau khi đọc handoff theo C-Q10 A.

**Đề nghị đóng với giới hạn.** Trạng thái phase tại bàn giao là `PARTIAL`.

| Mục | Nội dung |
|---|---|
| Phạm vi đã chọn | Khóa snapshot BE/FE/worker và đối chiếu baseline đã chạy. Xác định bằng chứng P6 có bao phủ gap dependency AI contract của P0 theo C-Q01 A. |
| Bằng chứng đã xác minh | P0 ghi BE 384 pass và 17 skip, FE 184 pass, worker 34 pass, E2E 27 pass và AI contract 69 pass trong môi trường lệch 41/77 mục. Snapshot đã test là BE `f6c6f0b`, FE `9fe67cf` và worker `3344a29`. P6 dùng lockfile trùng P0 với đủ 77/77 pin, 0 mismatch và có lượt baseline riêng 69 pass trên phần AI trước delta P6. |
| Phần chuyển tiếp hoặc còn mở | Dùng lượt P6 để bao phủ gap dependency của bộ AI contract local. Báo cáo P0 vẫn ghi exact-lock `NOT RUN` cho lượt ban đầu. Lượt P6 dùng fake/offline. P0 có log tổng hợp 69 pass, thiếu XML riêng để đối chiếu danh tính từng ca. |
| Giới hạn chấp nhận | Đối chiếu local baseline đủ để trình quyết định đóng P0 ở mức kỹ thuật. |

C.2 xét điều kiện đóng phase theo evidence local. Merge thuộc R4 và release vẫn `NOT_READY`. RF09/10 đến hạn 17/11/2026, RF11/12/13 đến hạn 24/11/2026 và RF08 YouTube đến hạn 29/11/2026. Các quyết định RF và hạn BM04 có quy trình riêng.

| Evidence | SHA-256 |
|---|---|
| [VERIFY_P0.md](<C:/Users/LeTran/FPT University/2026_FALL/STEMotion/.audit/stem-phase-2026-11-05/close-p0-p6/VERIFY_P0.md>) | `59475455e8c1d7432d85b1aa4bb4e5b516cf80d54b4bceb8442312c51888eca3` |
| [Manifest chứa hash của evidence gốc](<C:/Users/LeTran/FPT University/2026_FALL/STEMotion/.audit/stem-phase-2026-11-05/close-p0-p6/VERIFICATION_EVIDENCE_CLOSE.json>) | `2f7a958264ef2ac2923e740508008ab45890388946667d3933af48f342b91fa5` |

Quay lui tài liệu C.2 chỉ áp dụng cho các file C.2 trong thư mục này. Evidence gốc, kể cả các lần FAIL, thuộc hồ sơ đối chiếu.
