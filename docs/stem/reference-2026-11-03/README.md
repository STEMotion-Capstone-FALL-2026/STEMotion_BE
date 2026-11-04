# Audit và kế hoạch tăng cường STEM sau Review 2

Ngày lập: 03/11/2026, Asia/Saigon. Trạng thái: **audit source local và thiết kế đề xuất**, chưa triển khai refactor, chưa xác nhận runtime production hoặc được mentor nghiệm thu.

**Kết luận:** giữ pipeline Writer → Reviewer → Producer → Reviewer → Publish và engine Remotion. Bổ sung một miền nghiệp vụ giáo dục STEM có dữ liệu, quy tắc, minh chứng và quyết định chuyên môn được lưu theo phiên bản. Chỉ đổi tên màn hình, thêm chữ STEM hoặc thêm vài bảng môn học sẽ không giải quyết được nhận xét Review 2.

Định vị đề xuất: **STEMotion hỗ trợ giáo viên và nhóm học liệu thiết kế, sản xuất, kiểm duyệt clip phục vụ hoạt động giáo dục STEM theo cấp/lớp và yêu cầu cần đạt của chương trình Việt Nam.** Trục nội dung demo nên tập trung vào **năng lượng trong đời sống**; mô hình dữ liệu vẫn mở cho các chủ đề khác. Đây là đề xuất thu hẹp demo, chưa phải quyết định đổi đề tài.

| Tài liệu | Nội dung sử dụng |
|---|---|
| [01 Audit](01_AUDIT.md) | Bằng chứng hiện trạng, vấn đề theo module và đối chiếu từng nhận xét của người dùng |
| [02 Căn cứ và bộ quy tắc](02_CAN_CU_VA_RANG_BUOC.md) | Nguồn giáo dục, ba cấp học, lớp/môn, quy tắc sản phẩm, ba ví dụ đầu ra |
| [03 Thiết kế refactor](03_THIET_KE_REFACTOR.md) | Entity/bảng, invariants, API, UX, AI, versioning và migration |
| [04 Master plan](04_MASTER_PLAN.md) | Gói việc, phụ thuộc, ưu tiên, phạm vi giữ lại, nghiệm thu và kiểm thử |
| [05 Sơ đồ đề xuất](05_DIAGRAMS.md) | Context, conceptual, DB, class, activity, sequence, state và mapping use case |
| [Use case UML](diagrams/stem-usecases.puml) | Nguồn PlantUML có include/extend cho các nghiệp vụ STEM |

Thứ tự đọc ngắn: kết luận audit → bảng ràng buộc ba cấp → gói P0/P1 trong master plan. Thiết kế chi tiết dùng khi chia task FE/BE/AI và cập nhật Report 1–4.

Một phát hiện cần cập nhật vào hồ sơ: [Cổng Chính phủ ghi Thông tư 86/2026/TT-BGDĐT có hiệu lực từ 30/09/2026](https://chinhphu.vn/?docid=219902&orggroupid=4&pageid=27160). Vì vậy không nên chốt căn cứ chỉ bằng CV 909/2023 và CV 3089/2020. Chi tiết mức xác minh nguồn nằm ở tài liệu 02.

Bộ tài liệu không thay thế biên bản chính thức: các nhận xét Review 2 lấy từ tin nhắn người dùng; không gán lời nói, ý định hoặc sự đồng thuận cho mentor. Giữ nguyên code, DB, Report Word và sơ đồ gốc để team có thể review thiết kế trước khi triển khai từng phần.
