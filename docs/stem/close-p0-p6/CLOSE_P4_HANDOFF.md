# P4 — Frontend

Ngày 12/11/2026, Asia/Saigon. Người dùng duyệt Gate 2 và đồng ý disposition C.1 bằng tin “duyet gate 2, dong y disposition”. Quyết định đóng từng phase thuộc người dùng sau khi đọc handoff theo C-Q10 A.

**Đề nghị đóng implementation cô lập.** Trạng thái phase tại bàn giao là `PARTIAL`.

| Mục | Nội dung |
|---|---|
| Phạm vi đã chọn | Đối chiếu implementation FE, ca live sau khi xử lý lỗi BE 500 và adapter G06 theo C-Q04 A. |
| Bằng chứng đã xác minh | Evidence takeover ghi 216 FE pass. Hai ca live ban đầu lỗi 500. Fix A của BE có 41 pass và lượt live P4/G06 sau fix có 2 pass. Audit ghi HTTP 200/409/200/200. FE dùng `modeRevision` nội bộ và gửi `baseRevision` trên wire cho cùng counter workspace. |
| Phần chuyển tiếp hoặc còn mở | Dời tích hợp sang R4. Tại C.1, hai commit backup FE nằm ngoài ancestry của FE remote `main` `88570f2`. Tích hợp với BE phụ thuộc đối chiếu ref BE mới. Create flow thuộc phạm vi riêng. |
| Giới hạn chấp nhận | Chấp nhận implementation và kiểm thử trên clone. Bằng chứng ancestry chỉ áp dụng cho các commit được kiểm tra. Trạng thái merge remote BE vẫn `UNKNOWN`. |

C.2 xét điều kiện đóng phase theo evidence local. Merge thuộc R4 và release vẫn `NOT_READY`. RF09/10 đến hạn 17/11/2026, RF11/12/13 đến hạn 24/11/2026 và RF08 YouTube đến hạn 29/11/2026. Các quyết định RF và hạn BM04 có quy trình riêng.

| Evidence | SHA-256 |
|---|---|
| [VERIFY_P4.md](<C:/Users/LeTran/FPT University/2026_FALL/STEMotion/.audit/stem-phase-2026-11-05/close-p0-p6/VERIFY_P4.md>) | `89cbecf6519ab1e89c58ccc63f6228bb153affb25735a7675eb152441a819f47` |
| [Manifest chứa hash của evidence gốc](<C:/Users/LeTran/FPT University/2026_FALL/STEMotion/.audit/stem-phase-2026-11-05/close-p0-p6/VERIFICATION_EVIDENCE_CLOSE.json>) | `2f7a958264ef2ac2923e740508008ab45890388946667d3933af48f342b91fa5` |

Quay lui tài liệu C.2 chỉ áp dụng cho các file C.2 trong thư mục này. Evidence gốc, kể cả các lần FAIL, thuộc hồ sơ đối chiếu.
