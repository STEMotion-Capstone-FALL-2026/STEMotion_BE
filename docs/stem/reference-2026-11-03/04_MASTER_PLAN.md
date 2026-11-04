# Master plan refactor sau Review 2

Mục tiêu: làm cho vai trò STEM nhìn thấy được và kiểm chứng được từ input → hoạt động học → kịch bản → clip → thẻ học liệu, giữ hệ thống sản xuất video hiện có. Đây là kế hoạch đề xuất, chưa phải lịch/nhân sự đã được team hoặc mentor cam kết.

## 1 Quyết định kiến trúc và phạm vi

| Quyết định | Đề xuất chốt | Lý do |
|---|---|---|
| Định vị | Công cụ hỗ trợ thiết kế/sản xuất/kiểm duyệt học liệu video cho giáo dục STEM | Nối giá trị sư phạm với năng lực tạo clip đã có |
| Trục demo | Năng lượng trong đời sống, ba gói tiểu học/THCS/THPT | Có trọng tâm xuyên cấp, không hứa bao phủ mọi môn/lớp ngay |
| Sản phẩm xuất | MP4 và thẻ STEM có mục tiêu, hoạt động, tiêu chí, nguồn, phạm vi dùng | Một video ngắn có thể là một phần của hoạt động lớn hơn |
| Cơ chế bảo đảm | Quy tắc cấu trúc server + rubric người duyệt + fingerprint/snapshot | Đếm chữ STEM, số môn hoặc điểm AI không chứng minh nội dung đúng |
| Kiến trúc | Thêm domain/stem trong BE, metadata sidecar FE/scene, worker ít đổi | Giữ đầu tư vào studio/Remotion/TTS/queue |
| Dữ liệu cũ | Giữ đọc/xuất bản cũ có nhãn legacy; hoàn thiện brief khi xuất bản bản mới | Không bịa grade và không làm mất video |
| Phạm vi ngoài MVP | Không xây LMS, quản lí học sinh, chấm bài, robot runtime, simulation engine tổng quát | Các chức năng này không cần để chứng minh nền tảng hỗ trợ tạo clip STEM |

Không đổi đề tài, bỏ yêu cầu đã đăng ký hoặc thay cam kết YouTube chỉ vì bổ sung STEM. Những thay đổi scope thực sự cần được ghi nhận theo quy trình đồ án; không tự gán sự chấp thuận cho mentor.

## 2 Chuỗi triển khai và điểm dừng có bằng chứng

```text
P0 thống nhất problem/scope + xác minh văn bản
 → thiết kế profile/outcome mẫu + UC/conceptual/DB nhất quán
 → P1 một lát cắt script thủ công + STEM brief + submit/review gate
 → gắn composition/render/publish snapshot + stale protection
 → P2 AI/context + library + ba demo xuyên cấp
 → đồng bộ Report/BM và acceptance evidence
```

Tạo một lát cắt chạy được trước khi mở rộng UI/admin. Không dành toàn bộ đợt đầu cho thêm bảng, cũng không trì hoãn gate vì muốn làm đẹp video.

## 3 Backlog triển khai

Đơn vị công sức là **ngày công tập trung**, ước lượng sơ bộ theo mức thay đổi source; không phải thời gian lịch. Owner là vai trò đề xuất, không tự phân công tên thành viên.

| Task | Ưu tiên | Deliverable cụ thể và vị trí sửa | Phụ thuộc | Owner đề xuất | Ước lượng | Definition of Done |
|---|---|---|---|---|---|---|
| T01 | P0 | Chốt positioning/persona/trục năng lượng; ba learner contexts và brief mẫu; Report 1 problem/vision | Không | Product/SRS + người chuyên môn | 1–2 | Phân biệt người vận hành/người xem; xác định vấn đề, việc học sinh làm sau clip và phạm vi claim |
| T02 | P0 | Registry L01–L09, đối chiếu bản ký TT86 và phụ lục môn/lớp; bảng nguồn/outcome | Không | Curriculum lead + Reviewer | 2–4 | Nguồn/locator/hash/version/verification đầy đủ cho outcome của ba demo; vấn đề hiệu lực còn mở được ghi rõ |
| T03 | P0 | Chốt schema 14 bảng, JSON schema, fingerprint, API/error contract; Report 4 design | T01 | BE + FE + QA | 1–2 | UC → model → API → rule → test nối được; không xung đột lifecycle hiện có |
| T04 | P0 | Ba policy profile và ba bộ seed dự kiến lớp 4/8/11, xác nhận hoặc điều chỉnh lớp sau khi đọc chương trình | T01, T02, T03 | Curriculum + BE + QA | 2–3 | Có một bộ hợp lệ và một bộ không hợp lệ mỗi cấp; policy DRAFT/VERIFIED/ACTIVE phân biệt |
| T05 | P1 | Migration additive/catalog/StemBrief/evidence/repository/service; schema test | T03, T04 | BE | 3–5 | CRUD draft có revision; FK và invariant cross-project/grade/outcome chặn; không mất scene/script cũ |
| T06 | P1 | `StemValidationService`, rubric + gate submit/approve trong ScriptService; decision append-only | T05 | BE + QA | 2–4 | Viết tay và AI cùng rule; direct API thiếu brief không submit; stale approve không ghi status |
| T07 | P1 | FE cấp/lớp/môn/outcome, panel brief, evidence editor và lỗi theo field; sửa CreateProjectModal/types/services | T05, contract T03 | FE | 3–5 | Grade đúng lớp; lưu nháp chưa đủ được; submit chỉ rõ lỗi; không phá autosave/scene identity |
| T08 | P1 | Reviewer rubric/evidence view + transaction approval, quyền reviewer chuyên môn | T06, T07 | FE + BE + QA | 2–3 | Duyệt dựa minh chứng; đầy đủ field nhưng tích hợp giả vẫn bị trả về; không có nút bypass STEM |
| T09 | P1 | Composition semantic hash + snapshot vào RenderService/Clip/Publication và gate publish | T06 | BE + FE + worker support | 3–5 | Sửa công thức/narration làm stale; style whitelist giữ script review; artifact/snapshot sai không publish |
| T10 | P2 | AI ground theo brief/outcome, cấp tiểu học, contracts/prompts Java/Python, accept/ignore evidence | T04, T05, T07 | AI + BE + FE | 2–4 | Không sinh source/outcome VERIFIED; prompt injection từ topic không thay profile; AI fail vẫn viết tay được |
| T11 | P2 | Thẻ học liệu trong publish/library; filter server và metadata snapshot; phương án legacy | T07, T09 | FE + BE | 2–3 | Tìm cấp/lớp/môn/outcome/paging; publication không thay theo project metadata; có activity card |
| T12 | P0 rồi cập nhật | Sửa Report 1–4 và tất cả sơ đồ; BM mapping nhận xét/task/evidence | T01, T03; bản cuối sau T09/T11 | SRS/SDD + cả team review | 2–4 | Bản designed/implemented/tested/deployed không lẫn; sơ đồ nhúng đúng ảnh mới; traceability đủ |
| T13 | P1/P2 | Acceptance cả ba cấp, regression pipeline và staging migration/rollback evidence | T04–T11 theo gói | QA + BE/FE | 3–5 | Bộ ca bên dưới đạt trên commit ghi rõ; kiểm chứng legacy và direct API; chưa chạy ghi chưa chạy |
| T14 | P2 | Validate ba mẫu với người chuyên môn từng cấp, thử demo và chỉnh bối cảnh | T07, T08, T11 | Product + curriculum | 1–2 + thời gian chờ | Có nhận xét thực, thay đổi và kết luận phạm vi dùng; không coi review team là nghiên cứu hiệu quả học tập |

Tổng thô 29–51 ngày công, chưa tính chờ chuyên môn/mentor hoặc phát sinh triển khai. Các gói có thể chạy xen kẽ theo dependency, nhưng không chia tổng cho số người để hứa deadline. Nếu thời gian ngắn, ưu tiên T01–T09/T12 và một lát cắt hoàn chỉnh, giữ hai cấp còn lại ở trạng thái thiết kế cho đến khi triển khai/test thật. **Để cam kết đã hỗ trợ đủ ba cấp thì cả ba phải có seed, rule và acceptance chạy thực.**

T12 cần làm bản design ngay cho Review 2, rồi cập nhật lại theo code thực. Không chờ toàn bộ code mới sửa phạm vi, nhưng không vẽ planned thành implemented.

## 4 Những phần giữ nguyên hoặc chỉ thêm hook

| Phần | Hành động |
|---|---|
| Auth/Google/username/quản trị account | Giữ code và lịch sử hiện có, chỉ dùng quyền cho catalog/brief/review |
| Workspace membership | Giữ, thêm policy scope; không gộp với refactor project membership/lease chưa thuộc yêu cầu này |
| Script editor/save queue/draftRevision/version | Giữ, thêm brief revision và atomic submit |
| Template công thức/sơ đồ/chart/chemistry/quiz | Tái sử dụng làm phương tiện thể hiện evidence; không bắt clip dùng một loại template để được gọi STEM |
| Composition/Remotion/preview | Giữ; thêm semantic classification và context panel |
| Queue/TTS/caption/render callback | Giữ; truyền snapshot optional trong rollout, không đổi callback lifecycle |
| Media QA | Giữ checks hiện có; trình bày cạnh STEM validation |
| Publication/artifact/library | Giữ; thêm snapshot metadata, query và gate STEM |
| Asset work đang sửa local | Giữ riêng; chỉ tích hợp khi có contract/commit đã review |

## 5 Acceptance và kiểm thử có ý nghĩa

Các ca dưới đây là **kế hoạch**, chưa chạy trong lượt audit này.

| Test | Input/hành động | Kết quả kỳ vọng |
|---|---|---|
| AT01 | Chọn PRIMARY, grade 8 hoặc grade 4 + môn Vật lí chính thức không hợp catalog | Server từ chối với field/rule, không chỉ ẩn dropdown |
| AT02 | Grade cũ `Đại chúng`/null | Giữ legacy, không suy lớp; đọc video cũ được; publish mới đòi mapping |
| AT03 | Writer viết script thủ công hợp scene schema nhưng không có brief | Lưu draft được, submit 422; không có đường bỏ qua AI guardrail |
| AT04 | AI trả Boolean hợp STEM nhưng outcome giả/UNVERIFIED | Không được submit/approve; không tự tạo nguồn verified |
| AT05 | Hoạt động có đủ bốn chữ nhưng T là “dùng Remotion”, E là “thêm icon” | Integrity có thể pass, Reviewer rubric phải CHANGES_REQUIRED và ghi lý do |
| AT06 | Brief trỏ scene/outcome khác project hoặc sai grade | Từ chối, không lộ dữ liệu ngoài quyền |
| AT07 | Có một cảnh giải thích, hoạt động STEM nằm trong thẻ/task liên kết | Được xét theo coverage scope; không bắt clip nhét toàn bộ bài học; reviewer kiểm tra link |
| AT08 | Submitted script/brief bị PUT chỉnh trực tiếp | Từ chối; request changes tạo version và evidence mapping mới, decision cũ không kế thừa |
| AT09 | Reviewer đang mở revision N, Writer/context chuyển N+1 trước approve | 409, không ghi approval sai hash; UI giữ ghi chú người duyệt |
| AT10 | Producer đổi đáp án, công thức, dữ liệu, narration hoặc màu legend có nghĩa | STEM composition review STALE; publish bị chặn đến khi duyệt đúng bản |
| AT11 | Producer đổi spacing/style whitelist không tác động khoa học | Script STEM review còn hiệu lực; clip/render/media review theo revision mới vẫn bắt buộc |
| AT12 | Render callback từ job cũ về sau khi project đổi version | Không gắn snapshot/approval cũ vào artifact hiện tại; giữ freshness guard |
| AT13 | Policy mặc định workspace đổi từ v1 sang v2 | Không viết lại hồ sơ đã duyệt; project mới dùng v2; trường hợp revocation/critical update đòi revalidation theo policy rõ |
| AT14 | Admin gọi thẳng publish, giả report PASS client hoặc dùng validationRunId project khác | Không bypass nội dung/quyền; status/publication không đổi |
| AT15 | Sửa title/grade project sau khi bản cũ được công bố | Thẻ publication cũ giữ snapshot đúng artifact; library không trộn nguồn mới vào clip cũ |
| AT16 | Từng profile TH/THCS/THPT chạy một hồ sơ hợp lệ và một hồ sơ thiếu mục tiêu/hoạt động/rủi ro | Nhánh hợp lệ hoàn tất; nhánh không hợp lệ dừng đúng gate; ba bộ có bằng chứng riêng |
| AT17 | AI timeout hoặc từ chối gợi ý | Writer vẫn dùng manual flow; draft giữ nguyên; không tốn token cho mỗi autosave |
| AT18 | Migrate data có grade số, grade label, null; rollback app sau dữ liệu STEM mới | Không mất dự án/video; legacy label rõ; không phát hành sai nhãn verified khi strict bị tắt |
| AT19 | Filter library cấp/lớp/outcome kết hợp paging | Tổng/page/query đúng; không lọc grade chỉ trên trang tải về; snapshot hiển thị nhất quán |
| AT20 | Safety thiếu hoặc NOT_APPLICABLE không có lý do; dữ liệu mô phỏng ghi như số đo thật | Gate cấu trúc/rubric từ chối; mô phỏng có nhãn và phương án an toàn mới được xét |

Backend: unit cho rule/fingerprint, integration Postgres cho FK/transaction/migration và race approve; contract cho schema Java/Python/FE; FE component/E2E cho cấp/lớp/evidence và đường sửa lỗi; regression E2E hiện có script → production → QA → publication. Worker chỉ cần test metadata optional/snapshot association, không thêm test lặp logic sư phạm trong worker.

Tập trung metric: không có publication strict thiếu decision/hash đúng; cả manual và AI bị chặn khi thiếu brief; ba cấp có ca hợp lệ/không hợp lệ; độ trễ deterministic validation đo trên script đại diện và không gọi LLM. Ngưỡng thời gian cụ thể cần đo baseline rồi chốt với team, chưa có kết quả hiệu năng trong audit.

## 6 Rủi ro, quyết định còn mở và cách xử lý

| Rủi ro/quyết định | Hướng đề xuất | Thời điểm cần chốt |
|---|---|---|
| Không có chuyên môn để xác minh mọi môn/lớp | Chỉ seed outcome ba mẫu được review; phần khác chưa hỗ trợ hoặc DRAFT | Trước T04 ACTIVE |
| Bản ký TT86 chưa đọc trọn | Đối chiếu nguồn chính thức và ghi evidence; không suy bãi bỏ CV cũ | Trước policy ACTIVE |
| Ép mọi video 90s thành bài học hoàn chỉnh | Coverage scope rõ, hoạt động ngoài clip và gói nhiều clip | T01/T03 |
| Hình thức hóa S/T/E/M | Rubric yêu cầu đóng góp có tác dụng giải bài toán; nguồn khoa học và nhiệm vụ thật | T04/T08 |
| Scope ba cấp quá rộng | Schema ba cấp, catalog có giới hạn minh bạch, demo ba mẫu thay vì bao phủ toàn chương trình | T01 |
| Ngưỡng “hai môn” và đủ S/T/E/M | Quy tắc sản phẩm đề xuất; kiểm tra với chuyên môn, có thể điều chỉnh theo pathway; không giả là câu chữ công văn | T04 |
| Publication/review lệch revision | Fingerprint/transaction/frozen snapshot và tests AT09–AT15 | T09 |
| Đổi diagram mà thiếu code | Ghi rõ planned/implemented/tested/deployed cho từng task | Mọi đợt T12 |
| Quyền Admin bypass nội dung | Giữ quản trị access nhưng gate STEM áp cho mọi actor | T06/T09 |

Các quyết định cần thảo luận không ngăn việc triển khai bản nháp/fixture/test trong workspace. Chỉ ACTIVE policy và claim đã đối chiếu curriculum mới đòi bằng chứng chuyên môn thật. Không yêu cầu mentor duyệt lại từng thay đổi kỹ thuật thường lệ.

## 7 Bằng chứng cần mang vào lần review tiếp theo

1. Một slide/đoạn problem statement giải thích người xem, bối cảnh và STEMotion giúp họ ở đâu.
2. Ma trận nguồn → cấp/lớp/môn/outcome → rule → field → UC/API → test.
3. Conceptual và DB cùng có policy, curriculum outcome, brief/evidence/review; actor vẫn là người sử dụng, không biến “STEM” thành actor.
4. Demo project viết tay không đủ STEM bị chặn; demo đủ brief/evidence được duyệt; Producer sửa số liệu làm stale và chưa thể publish.
5. Ba thẻ học liệu theo ba cấp, chỉ rõ phần nào ở MP4 và phần nào ở nhiệm vụ ngoài clip.
6. Bảng hoàn thành có các cột Designed / Implemented / Tested / Deployed / Accepted và link evidence; không điền “Accepted” thay mentor.

Đề xuất câu giải thích với mentor: “STEMotion giữ pipeline tạo video hiện tại, bổ sung hồ sơ STEM gắn chương trình, lớp, mục tiêu, hoạt động và minh chứng theo phiên bản. Video là học liệu hỗ trợ hoạt động; hệ thống kiểm tra cấu trúc và nguồn, reviewer đánh giá chuyên môn, và publication chỉ dùng đúng bản đã được duyệt.”
