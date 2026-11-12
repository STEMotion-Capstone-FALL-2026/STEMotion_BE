# P6 — AI, library và exact-lock

Ngày 12/11/2026, Asia/Saigon. Người dùng duyệt Gate 2 và đồng ý disposition C.1 bằng tin “duyet gate 2, dong y disposition”. Quyết định đóng từng phase thuộc người dùng sau khi đọc handoff theo C-Q10 A.

**Đề nghị giữ mở · BLOCKER.** Trạng thái phase tại bàn giao là `PARTIAL`.

| Mục | Nội dung |
|---|---|
| Phạm vi đã chọn | Đối chiếu AI grounded, thư viện, exact-lock, regression, backup và evidence local theo C-Q06 B. |
| Bằng chứng đã xác minh | Evidence ghi 360 BE, 258 FE, 85 AI exact-lock 77/77, 2 live P4/G06 và 2 live P6 theo từng suite. Hai ca live P6 nằm trong ca orchestration BE nên ghi riêng. GitHub backup khớp BE `9c7fdf5` và FE `4ddba1d`. P7.1 ghi Maven/JUnit 360 pass với lỗi tổng hợp cuối runner `FileNotFoundError`. |
| Phần chuyển tiếp hoặc còn mở | Cần đối chiếu thẻ P6 local `5dfc852e…` với bản Bond `b03138f2…` hoặc có quyết định dùng phiên bản cụ thể. Tích hợp thuộc R4 và phụ thuộc review ref BE. Log và runtime audit giữ local-only theo C-Q06 B. |
| Giới hạn chấp nhận | Evidence đã đối chiếu nằm trong `.audit/stem-p6` của clone BE, gồm `be-final-complete/maven.log`, `fe-handoff-final/unit-results.json`, `ai-final-complete/results.xml`, `p4-live-final-complete/DB_AUDIT.json`, `p4-live-final-complete/http-audit.json` và `fe-live-be-final-complete/results.json`. Báo cáo C.1 liệt kê đường dẫn đầy đủ và SHA. Phạm vi technical evidence đã xác nhận, phần scope signoff vẫn bị chặn. |

C.2 xét điều kiện đóng phase theo evidence local. Merge thuộc R4 và release vẫn `NOT_READY`. RF09/10 đến hạn 17/11/2026, RF11/12/13 đến hạn 24/11/2026 và RF08 YouTube đến hạn 29/11/2026. Các quyết định RF và hạn BM04 có quy trình riêng.

| Evidence | SHA-256 |
|---|---|
| [VERIFY_P6.md](<C:/Users/LeTran/FPT University/2026_FALL/STEMotion/.audit/stem-phase-2026-11-05/close-p0-p6/VERIFY_P6.md>) | `ab375bd591ade141ac076731b7ca6b8241f7a7035551ccc18e5337873353f313` |
| [Manifest chứa hash của evidence gốc](<C:/Users/LeTran/FPT University/2026_FALL/STEMotion/.audit/stem-phase-2026-11-05/close-p0-p6/VERIFICATION_EVIDENCE_CLOSE.json>) | `2f7a958264ef2ac2923e740508008ab45890388946667d3933af48f342b91fa5` |

Quay lui tài liệu C.2 chỉ áp dụng cho các file C.2 trong thư mục này. Evidence gốc, kể cả các lần FAIL, thuộc hồ sơ đối chiếu.
