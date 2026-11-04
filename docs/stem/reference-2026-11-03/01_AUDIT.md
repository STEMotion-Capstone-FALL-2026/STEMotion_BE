# Audit vai trò STEM trong hệ thống

Ngày 03/11/2026. Kết luận dựa trên checkout local, không dùng audit tháng 10 làm bằng chứng thay thế source hiện tại.

## 1 Phạm vi và độ tin cậy

| Thành phần | Baseline đã kiểm tra |
|---|---|
| Backend | `22a90fe4a8b6b6f453d129dacf8168dda9f34c0c` |
| Frontend | `9fe67cf7801b3e971b3c852c80f68214fd13bba7` và các sửa local đang có |
| Render worker | `022ea0a4d764a368f9179ba676fcf09a892b644f` |
| AI | `STEMotion_BE/services/ai/app`, cùng Java orchestration và nhánh prompt tương thích trong BE |
| Hồ sơ | Bốn DOCX trong `Report/`; XML văn bản/bảng và danh sách ảnh đang được nhúng đã trích mới |
| Sơ đồ | Xem trực quan `Diagram/Conceptual ERD.png`, `Diagram/Usecase Diagram.png` và ERD `image105.png` được Report 4 tham chiếu |
| Yêu cầu Review 2 | Nội dung người dùng cung cấp trong chat; chưa có bản biên bản chính thức chứa các nhận xét này để xác minh chữ ký/người phát biểu |

Bằng chứng trích mới: `.audit/stem-review2/report-manifest.json` chứa SHA-256 và ảnh được tham chiếu; các file `.txt` bên cạnh dùng số thứ tự paragraph XML, **không phải số trang Word**. Dữ liệu trích cũ chỉ dùng tham khảo lịch sử. Đăng ký đề tài đối chiếu bản trích có sẵn `.audit/report-review/registration.txt` và tên PDF gốc.

Đã đọc cấu trúc và các điểm chốt của toàn chuỗi: workspace → create → AI/manual script → submit/review → composition → render → QA → publish → library; schema migrations, contracts FE/BE/AI/worker, quyền và hồ sơ. Đây là audit kiến trúc/nghiệp vụ STEM theo source; chưa phải kiểm thử thực thi toàn bộ endpoint, kiểm toán từng dòng code, khảo sát người dùng hoặc kiểm chứng nội dung từng clip production.

Sửa local có sẵn được giữ nguyên: BE `.gitignore`; FE `CinematicScene.tsx`, `lego/catalog.ts` cùng các asset/component chưa track. Worker sạch lúc chụp baseline. Không đọc nội dung secret/env hay tài khoản để làm audit này.

## 2 Kết luận cần thống nhất

Hệ thống **đã có năng lực tạo video STEM ở lớp trình bày**: công thức, phản ứng, dữ liệu, sơ đồ, quiz, hình học, template composition; AI có prompt khoa học, đề xuất cách dẫn dắt và storyboard; có kiểm duyệt con người. Vì vậy kết luận “hoàn toàn không có STEM” không phản ánh đầy đủ code.

Tuy nhiên, **STEM chưa được mô hình hóa thành điều kiện hợp lệ của một học liệu**. Môn/grade hiện là metadata; bộ máy kiểm duyệt kiểm tra trạng thái, quyền và chất lượng media nhiều hơn mối liên hệ chương trình → mục tiêu → hoạt động → minh chứng → clip. Nhận xét “thiên về quản lý tạo clip” có căn cứ ở mức thiết kế sản phẩm.

Refactor phù hợp là mở rộng miền giáo dục và gắn vào những điểm chốt đang có. Không có bằng chứng cho thấy phải đổi stack, bỏ Remotion, viết lại auth, render queue hay toàn bộ workflow để xử lý khoảng trống này.

## 3 Phát hiện theo module

Đường dẫn `domain/...` và `core/...` trong bảng tính từ `STEMotion_BE/src/main/java/com/stemotion/`; các đường dẫn có prefix FE/BE hoặc src/main/resources được ghi theo repo tương ứng. P0 là vấn đề cần xử lý để xác lập định hướng/khả năng bảo vệ; P1 là triển khai nghiệp vụ lõi; P2 là hoàn thiện sau lát cắt đầu tiên. Không diễn giải các mức này thành lỗi hệ thống đang sập.

| ID | Mức | Bằng chứng hiện tại | Khoảng trống và ảnh hưởng | Hướng xử lý |
|---|---|---|---|---|
| A01 | P0 | `domain/project/entity/ClipProject.java:46–51` trong BE có `subject`, `gradeLevel`; `core/enums/StemEnums.java:14` chỉ có 5 môn | Không có cấp học, yêu cầu cần đạt, bối cảnh, hoạt động hay bộ quy tắc đã chọn. Không chứng minh project là học liệu STEM | `StemBrief` theo ScriptVersion và `PolicyProfileVersion` |
| A02 | P0 | FE `src/components/modals/CreateProjectModal.tsx`, selector “Cấp độ / Định dạng” gửi `Đại chúng`, `Cơ bản`, `Nâng cao`, `EdTech` qua gradeLevel | “Độ khó/định dạng” bị dùng thay lớp; không thể đối chiếu lớp 1–12. Form chưa có cấp/lớp thật | Tách grade 1–12, educationLevel và audience/difficulty; giữ chuỗi cũ để tương thích |
| A03 | P0 | BE `domain/workspace/entity/Workspace.java` chỉ có name/department/createdBy | Workspace chưa định nghĩa phạm vi cấp/lớp, môn hay policy để thành viên tuân thủ | Gắn một hoặc nhiều phiên bản policy được phép; project chọn một profile cụ thể |
| A04 | P0 | BE `domain/script/dto/ScriptDtos.java:20–32`: subject bắt buộc nhưng gradeLevel là String, không có ràng buộc cấp/lớp | API có thể nhận grade tùy ý, kể cả không có; FE dropdown không bảo vệ direct API | Validator server cho tổ hợp level/grade/subject/curriculum, không chỉ frontend |
| A05 | P1 | BE `domain/ai/service/AiScriptService.java:31–38`, Python `prompts/system.txt`; có guardrail `isValidStemTopic`, prompt môn/lứa tuổi | Prompt và Boolean từ LLM chưa phải xác minh nội dung; prompt mặc định secondary/high school chưa phục vụ tiểu học rõ ràng | Grounding bằng dữ liệu đã duyệt, profile theo lớp, output có evidence; không lấy AI làm người duyệt |
| A06 | P1 | BE `domain/ai/service/VideoStoryboardService.java:39–70`, `AiSchemas.java`: có `learningObjective` từng cảnh, reviewWarnings | Đây là **gợi ý**, chưa liên kết mục tiêu với yêu cầu cần đạt có nguồn và bản review đã chấp thuận | Lưu lựa chọn sau accept vào StemBrief/SceneEvidence; kiểm tra lại nếu nguồn cảnh đổi |
| A07 | P0 | BE `domain/script/service/ScriptService.java:278–333`: submit kiểm tra status/role/có scene; approve đổi status và lưu note | Một script thủ công có scene hợp schema chưa phải trả lời bối cảnh, tích hợp và nhiệm vụ học sinh. Guardrail AI không bao phủ đường manual | Cùng một validator cho manual và AI, dùng ở submit/approve |
| A08 | P1 | `domain/script/entity/Scene.java`: type/title/narration/props; FE `types/stem.ts` có nhiều template khoa học | Template/biểu tượng khoa học không chứng minh hoạt động vận dụng; chưa có vai trò sư phạm hay mapping S/T/E/M | Evidence sidecar gắn sceneId; tách pedagogicalRole khỏi SceneType |
| A09 | P1 | BE `domain/production/service/CompositionService.java:253–279` giữ ID/thứ tự/type, nhưng cho đổi text/narration/props | Có thể đổi ý nghĩa khoa học sau script approval; chưa có STEM review theo nội dung composition | Hash nội dung semantic, duyệt lại khi đổi dữ kiện/công thức/quiz; không chặn chỉnh màu vô hại |
| A10 | P1 | BE `domain/review/dto/QaDtos.java:17–25`, `ClipQaService.run` | QA hiện có duration, pacing, overflow, contrast, structure, freshness, caption sync. Chưa có rubric chương trình, tích hợp, hoạt động và bằng chứng | Giữ media QA; thêm STEM validation report riêng, quyết định reviewer có scope/hash |
| A11 | P1 | BE `domain/production/service/RenderService.java:65–129` đã khóa theo script approved, lưu compositionRevision/inputProps; worker `src/types.ts` nhận render props | Đây là nền tốt để giữ; chưa lưu snapshot hồ sơ STEM trong job/clip | Đóng băng brief/policy/evidence cùng input, không yêu cầu worker quyết định sư phạm |
| A12 | P1 | BE `domain/production/service/PublishService.java:52–86` kiểm tra clip approved, current artifact và qa.passed | Các gate có thật nhưng không kiểm chứng STEM. Approval cũ có thể bị hiểu nhầm là đạt chương trình | Gate STEM độc lập theo snapshot; hồ sơ xuất bản có thông tin lớp/mục tiêu và phạm vi sử dụng |
| A13 | P1 | BE `domain/library/service/LibraryService.java` lọc subject/grade; FE `MediaLibraryView.tsx:36–55` chỉ selector subject và tìm grade trong dữ liệu tải về | Chưa tìm theo yêu cầu cần đạt, chủ đề, hoạt động, stage hoặc evidence; dùng metadata mutable của project cho hiển thị | Library đọc snapshot publication, filter server theo cấp/lớp/môn/chủ đề/outcome |
| A14 | P0 | `src/main/resources/db/migration/V1…V22`; các entity hiện tại; ERD Report 4 | Không tìm thấy entity/bảng chuyên biệt cho curriculum outcome, policy, STEM brief, integration evidence, rubric review trong phạm vi đã quét | Bổ sung bảng có quan hệ thật và invariant; không nhét tất cả vào props tự do |
| A15 | P0 | Conceptual PNG chủ yếu ClipProject/ScriptVersion/Composition/Scene/RenderJob/Clip; UC PNG chủ yếu write/review/render/publish | Chưa có khái niệm giáo dục trung tâm; thay nhãn UC sẽ không thể hiện nghiệp vụ mới | Thêm outcome, activity, context, evidence, profile và quyết định chuyên môn; xem sơ đồ đề xuất |
| A16 | P0 | Report 1 nói khách hàng school media/EdTech/kênh giáo dục; Report 3 FR-11/18/20 chỉ title/subject/grade/topic; Report 4 ERD chưa có miền curriculum | Định vị rộng, chưa phân biệt người vận hành với học sinh xem clip; chưa có nguồn/cơ chế bảo đảm STEM xuyên hồ sơ | Viết lại phần problem/persona/value/FR/BR liên quan; giữ đăng ký và lịch sử thay đổi |
| A17 | P1 | `WorkspaceAccess` có Admin bypass trong role/access; review hiện lưu decision theo status history | Quyền quản trị chưa chứng minh năng lực thẩm định nội dung môn/lớp | Định nghĩa reviewer chuyên môn do workspace phân công; Admin không tự được miễn evidence/gate |
| A18 | P1 | Tests hiện có tập trung workflow/schema/AI props/media | Chưa thấy bộ test về policy lớp/outcome/cross-version STEM trong các đường được đọc | Thêm acceptance có cả bypass manual/direct API/đổi composition/stale policy |

Các điểm mạnh cần giữ: immutable submitted scripts, draftRevision, scene identity, composition revision, frozen render payload, reviewer decision history, media QA, RBAC và xuất bản artifact-backed. Audit này không kết luận các chức năng render/auth hiện “mock” theo tài liệu cũ tháng 10.

## 4 Đối chiếu trực tiếp nhận xét Review 2

| Nhận xét người dùng cung cấp | Kết luận audit | Sản phẩm cần tạo | Task liên quan |
|---|---|---|---|
| Có focus một lĩnh vực STEM không | Chưa có phạm vi demo theo lĩnh vực; 5 nhãn môn khá rộng | Một trục demo năng lượng trong đời sống, 3 mức lớp minh họa | T01, T04 |
| Cần bối cảnh đáp ứng người xem | Có cách dẫn dắt AI nhưng thiếu bối cảnh học tập có cấu trúc | Learner context, nhu cầu, thiết bị, kiến thức trước, hoạt động sau xem | T01, T05, T07 |
| Đang quản lý quá trình tạo clip hơn là hỗ trợ tạo STEM | Có rendering và AI STEM thật, nhưng enforcement thiên workflow/media | STEM brief → scene evidence → review → publication snapshot | T05–T11 |
| Clip phải thông qua các chữ STEM | Chưa có integration evidence | Ma trận S/T/E/M nêu đóng góp và vị trí minh chứng; không đếm chữ/icon | T05–T08 |
| Thiếu entity STEM ở UC/conceptual/DB | Có subject enum, thiếu miền curriculum/policy/evidence | Entity và bảng mới, quan hệ, UC/sequence/activity/class cập nhật | T03, T05, T12 |
| Công văn cho từng lớp | Cần sửa cách hiểu về cấp/môn/lớp | Registry văn bản + curriculum outcome có grade; không tạo công văn giả cho từng lớp | T02–T04 |
| Mỗi cấp có bộ ràng buộc để workspace tuân thủ | Hiện chưa có | PRIMARY/LOWER_SECONDARY/UPPER_SECONDARY với rule version và allowed workspace scope | T04–T06 |
| Đầu và cuối đều liên quan STEM | Hiện chỉ có khuyến nghị title/outro | Mở bằng vấn đề/mục tiêu; kết bằng bằng chứng/hoạt động/tiêu chí, có thể ở gói clip | T07–T10 |
| Giữ hệ thống tạo clip hiện tại | Có thể giữ phần lớn kiến trúc | Additive schema/API, sidecar metadata, migration theo đợt | T03, T05, T11 |

## 5 Hồ sơ nào phải sửa và đến mức nào

| Hồ sơ | Giữ | Sửa/bổ sung |
|---|---|---|
| Đăng ký đề tài | Tên và nền tảng sản xuất video nếu phạm vi vẫn đáp ứng | Bản giải thích tăng cường STEM; nếu thay đối tượng/phạm vi đã đăng ký thì lập change request, không tự tuyên bố đã duyệt |
| Report 1 | Pipeline và giá trị sản xuất bằng Remotion | Tách customer/operator/learner, bài toán sư phạm, bối cảnh thực tế, scope ba cấp và demo ưu tiên |
| Report 2 | Nhóm công việc kỹ thuật còn phù hợp | WBS cho curriculum/rules/evidence, chuyên môn reviewer, dependency, công sức và rủi ro |
| Report 3 | FR nền auth/script/production/render | FR-ST01…ST12, BR-ST01…ST15, UC STEM, error codes, acceptance, manual/AI cùng gate |
| Report 4 | Kiến trúc 4 subsystem và render contract | Package STEM, ERD mới, class, sequence/activity/state, snapshot và migration |
| BM Review 2 | Mẫu biểu | Mapping nhận xét → task → bằng chứng demo → trạng thái; owner/ngày do team xác nhận |

Auth cũng có chênh hồ sơ giữa Report 3 đã giữ Google và Report 4 còn nói no third-party sign-in. Đây là việc đồng bộ tài liệu đã nằm ở backlog trước, không phải lý do trộn auth refactor vào gói STEM.

## 6 Những điều chưa được xác minh

Chưa khảo sát giáo viên/học sinh hoặc đo hiệu quả học tập. Chưa xác minh mọi sửa đổi văn bản địa phương, phụ lục yêu cầu cần đạt của từng môn/lớp và mọi thay đổi pháp lý sau nguồn đã đọc. Chưa chạy DB/live UI hay AI trả phí. Chưa chỉnh file Word, drawio hoặc migrate schema. Không coi ví dụ curriculum trong kế hoạch là dữ liệu đã được chuyên môn xác nhận.

**Điều kiện đóng audit:** mỗi A01–A18 có quyết định xử lý, task, artifact và test; các P0 về định vị/căn cứ/schema/gate có demo cụ thể. Việc thêm bảng hoặc đổi giao diện riêng lẻ không đủ để đánh dấu đã giải quyết nhận xét.
