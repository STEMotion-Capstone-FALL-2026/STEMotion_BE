# STEMotion — Master Update Plan sau buổi họp mentor

Ngày tổng hợp: **26/10/2026 — Asia/Saigon**
Trạng thái: **DRAFT — gửi teammate và AI hỗ trợ đọc, phản biện, duyệt**
Phạm vi của lần tổng hợp: phân tích yêu cầu và thiết kế hướng phát triển; **chưa phải lệnh triển khai**.

**Cập nhật 30/10/2026:** người dùng đã giao lập plan và triển khai batch BE/config cho Vbee + AI.
Xem [plan hoàn thiện BE/video](docs/BE_COMPLETION_VIDEO_PLAN_2026-10-30.md) và
[hướng dẫn setup provider](docs/VIDEO_PROVIDER_SETUP_2026-10-30.md).
Ủy quyền này dành cho batch tích hợp đã mô tả, không tự chốt các policy Phần B còn mở,
không tự gọi API trả phí hay deploy production. Kết quả code/tests/giới hạn ghi trong báo cáo batch.

**Cập nhật bàn giao 27/10/2026:** V22 production đã được phía LeTran xử lý thành công qua Flyway;
history V22 success, 0 failed migrations, schema hợp lệ và BE health UP tại thời điểm kiểm tra.
Xem [báo cáo V22](docs/V22_PRODUCTION_MIGRATION_2026-10-27.md).
Danh sách việc teammate cập nhật tại [TEAMMATE_TASKS_2026-10-27.md](docs/TEAMMATE_TASKS_2026-10-27.md):
teammate phụ trách thiết kế/code BE/tests/contract/migration artifacts;
**verify và fix production do phía LeTran phụ trách, không giao teammate**.
Các baseline V21 và điểm “chưa có” trong phần đối chiếu ngày 26/10 bên dưới là lịch sử;
BE commit `c7435c7` đã bổ sung Phần A, FE `0f3d0ab` mới cập nhật checklist 9.7.
Các đề xuất Phần B và quyết định sản phẩm vẫn chờ duyệt, không được tự xem là đã chốt.

**Đính chính phạm vi theo xác nhận của người dùng ngày 27/10/2026:** Bubble Sort chỉ là ý tưởng
chủ đề cho một video demo mentor. STEMotion vẫn là hệ thống hỗ trợ sản xuất video STEM;
không giới hạn sản phẩm vào Tin học/thuật toán. Ý tưởng này không yêu cầu xây engine mô phỏng,
API/schema riêng, không làm chuẩn nghiệm thu bắt buộc cho toàn hệ thống và không chặn refactor.
Các đề xuất trước đây về SimulationTrace và bắt buộc sinh bước Bubble Sort bằng code được rút
khỏi phạm vi bàn giao. Nếu sau này cần cơ chế kiểm chứng cho một loại nội dung, phải có task riêng.

## 1. Mục đích và cách sử dụng

Tài liệu gom toàn bộ biên bản mentor, đối chiếu source hiện tại, đánh giá tính khả thi,
giá trị, ảnh hưởng kiến trúc và đề xuất thứ tự thực hiện. Mục tiêu là thống nhất cách hiểu
trước khi refactor, tránh mỗi người hoặc AI triển khai một phiên bản nghiệp vụ khác nhau.

Phân loại thông tin:

- **Yêu cầu từ biên bản:** nội dung mentor/team đã ghi lại; câu chữ có thể còn sơ sài.
- **Hiện trạng đã đối chiếu:** điều quan sát được trong source tại các SHA bên dưới.
- **Đề xuất:** phương án kỹ thuật/nghiệp vụ để team phản biện, chưa mặc nhiên được duyệt.
- **Cần chốt:** có nhiều cách hiểu hoặc cần quyết định sản phẩm, chi phí, quyền, vận hành.

Đối với teammate và AI đọc file này:

1. Đọc toàn bộ, đặc biệt mục 8 về quyết định còn mở và mục 11 về phụ thuộc triển khai.
2. Phản biện yêu cầu, tác động và acceptance criteria trước khi viết code.
3. Không suy diễn các đề xuất thành yêu cầu đã được mentor xác nhận.
4. Không coi việc được chia sẻ file là ủy quyền sửa code, chạy migration, mua dịch vụ,
   cấu hình API, push hoặc deploy. Các hành động đó cần task triển khai riêng từ người phụ trách.
5. Khi được giao triển khai, kiểm tra lại main mới nhất, schema và runtime; SHA trong file là baseline,
   không phải cam kết hệ thống sẽ luôn giữ nguyên.
6. Không đưa secret, token hoặc credential thật vào tài liệu, log, source hay phản hồi của AI.

## 2. Kết luận định hướng

Biên bản tạo ra hai trục phát triển chính:

1. **Chất lượng video học thuật:** nội dung chính xác, kịch bản tốt, âm thanh dễ nghe,
   hoạt ảnh giải thích kiến thức, hình ảnh tiết chế. Bubble Sort là một ý tưởng video demo độc lập.
2. **Quản trị nhóm sản xuất:** workspace đủ nhân sự, tách quyền Admin/Leader,
   duyệt khởi tạo và tham gia project, chốt role theo project, ghi nhận đóng góp, khóa phiên chỉnh sửa.

Không có đủ bằng chứng để kết luận cần viết lại toàn bộ hệ thống. Có thể giữ phần lớn nền tảng
auth, workspace, version/review, composition, Remotion và render queue. Thay đổi sâu nhất nằm ở
**project membership, authorization, approval workflow và quyền chỉnh sửa đồng thời**.

Mở rộng kho asset chưa phải ưu tiên đầu tiên sau biên bản này. Cần xác định ngôn ngữ video,
vai trò Manim và hợp đồng dữ liệu trước, rồi mới quyết định tổ chức asset tương ứng.

## 3. Baseline và giới hạn bằng chứng

### 3.1. Source đã đối chiếu

| Repo | Nhánh/ref dùng để đối chiếu | SHA | Ghi chú |
|---|---|---|---|
| STEMotion_FE | main | `2be8fff` | Đã push batch UX: guard thao tác, bảo vệ autosave/draft, modal, responsive, regression tests |
| STEMotion_BE | origin/main | `9572169` | Đọc remote ref sau fetch; checkout local vẫn là `74a1bf2`, không checkout/pull đè thay đổi local |
| STEMotion_RENDER | origin/main | `7e5aeea` | Đọc remote ref sau fetch; checkout local vẫn là `3d3e163` |

Chưa xác nhận lại SHA đang chạy thực tế của FE/BE/worker trong lần phân tích này.
Không gọi AI trả phí, không render production, không thử ghi DB production.

### 3.2. Thông tin đã biết từ các lượt trước

- FE batch UX có **181 tests / 35 files pass**, TypeScript và production build pass tại thời điểm push `2be8fff`.
- Build còn cảnh báo bundle lớn. Tests FE không chứng minh chất lượng âm thanh/video thật hoặc toàn bộ hành vi đa người dùng.
- DB đã được người dùng chuyển sang Supabase Singapore; ảnh kiểm tra trước đó cho thấy 20 public tables,
  Flyway V21, không có migration thất bại. Đây là bằng chứng lịch sử, chưa truy vấn lại DB hiện tại.
- Backend từng deploy thành công sau chuyển DB; không suy ra mọi cấu hình runtime vẫn giống lần đó.

### 3.3. Thay đổi asset thử nghiệm chưa được yêu cầu

Trong trao đổi “có thể cải thiện quản lý asset không”, AI đã hiểu sai thành yêu cầu triển khai.
Người dùng đã yêu cầu dừng và chuyển sang phân tích biên bản. Các thay đổi thử nghiệm vẫn ở FE local,
**chưa commit, chưa push, chưa nối hoàn chỉnh vào luồng Producer và chưa nghiệm thu**:

```text
M  src/remotion/director/CinematicScene.tsx
M  src/remotion/lego/catalog.ts
?? src/components/assets/AssetPreview.tsx
?? src/components/modals/AssetCatalogModal.tsx
?? src/remotion/assets/catalog.ts
?? src/remotion/director/assetComponents.ts
?? src/remotion/lego/presets.ts
```

Không đưa phần này vào release/refactor mới một cách vô tình. Chủ sở hữu cần quyết định giữ,
điều chỉnh hoặc loại bỏ sau khi duyệt hướng sản phẩm. Tài liệu này không ủy quyền xóa các file đó.
BE còn có thay đổi local `.gitignore` có sẵn từ trước; không thuộc task này.

## 4. Biên bản gốc được chuẩn hóa thành danh sách yêu cầu

| ID | Nội dung ghi lại | Cách hiểu để review |
|---|---|---|
| M01 | Âm thanh quá dở; xin tool âm thanh, Vbee | Cần cải thiện chất lượng audio; Vbee là ứng viên cần thử, chưa chứng minh là lựa chọn cuối |
| M02 | Chọn một ngành cụ thể; làm video Bubble Sort | Ý tưởng chọn chủ đề cụ thể cho video demo mentor; không thu hẹp phạm vi sản phẩm hay phát sinh module thuật toán |
| M03 | Clip chưa đủ chất lượng; không quá màu mè; học thuật nên trắng đen | Xây ngôn ngữ hình ảnh học thuật, tiết chế trang trí; mức monochrome cần xác nhận |
| M04 | Muốn dùng AI nào cũng được để hỗ trợ tạo video | Không ràng buộc một provider; lựa chọn theo chất lượng và tính phù hợp |
| M05 | Quản lý chất lượng, dùng “claude ops”, “gpt6”, chấp nhận bỏ chi phí | Có thể hiểu “claude ops” là Claude Opus; cần model/công cụ tốt và quy trình QC, không chỉ thay tên model |
| M06 | Thêm config API | Cần cấu hình tích hợp có quản trị, quyền, hạn mức và truy vết |
| M07 | Cần kịch bản chỉn chu | Có tiêu chí nội dung/giảng giải trước khi dựng và duyệt video |
| M08 | Thử Claude + Manim + Remotion | Đánh giá pipeline kết hợp AI, hoạt ảnh lập trình và dựng video |
| M09 | Producer mới chỉ có monitor | Cần làm rõ Producer phải thực sự chỉnh và cải thiện sản phẩm ở mức nào |
| M10 | Admin quản lý số lượng role; nhiều người cùng role | Hỗ trợ nhiều Writer/Reviewer/Producer; phân công cụ thể theo project |
| M11 | Tạo workspace phải có ít nhất 3 người, đủ 3 role khác nhau | Ít nhất 3 user khác nhau, bao phủ Writer/Reviewer/Producer; Leader là trách nhiệm bổ sung — đề xuất cách hiểu |
| M12 | Member muốn tham gia project cần Leader approve; Writer tạo project cũng cần approve trừ Writer là Leader | Hai quy trình: đề xuất khởi tạo và yêu cầu tham gia |
| M13 | Ghi contribution đối với video | Ghi nhận người tham gia và công việc thực hiện, gắn phiên bản và role |
| M14 | User có nhiều tổ/project/role nhưng trong một tổ hoặc ít nhất project phải role cố định | Cần chốt phạm vi cố định; đề xuất snapshot role theo project |
| M15 | Leader quản lý role, không thêm/xóa member | Tách quản lý nhân sự workspace khỏi phân công công việc |
| M16 | Dashboard riêng Leader quản lý tiến độ | Màn hình ra quyết định, theo dõi phụ trách và điểm nghẽn |
| M17 | Lock project khi có người edit | Cần quyền giữ phiên chỉnh sửa do server xác thực, không chỉ disable nút |

## 5. Đánh giá chi tiết từng ý

### M01 — Cải thiện audio và thử Vbee

**Hiện trạng:** `STEMotion_RENDER/src/tts.ts` có các nhánh FPT, Google Cloud và các helper giọng đọc khác.
`src/config.ts` cấu hình provider/voice/speed qua môi trường. Chưa có Vbee.
Preview, đo thời lượng và render chưa có hợp đồng cấu hình giọng hoàn chỉnh, thống nhất xuyên suốt.
Chưa nghe lại đúng video mentor chê nên chưa thể quy toàn bộ nguyên nhân cho provider.

**Tính khả thi: cao. Giá trị: rất cao.** Vbee cung cấp TTS API; tài liệu chính thức mô tả cả
đồng bộ và bất đồng bộ. Chi tiết API, voice, quota, giá và điều khoản phải xác minh ở thời điểm tích hợp.

**Đề xuất:**

- Nghe so sánh cùng một đoạn script STEM bằng giọng hiện tại, Vbee và nếu có thể một bản người đọc.
- Kiểm tra thuật ngữ tiếng Anh, tên biến, công thức, dấu câu, tốc độ, khoảng lặng và âm lượng.
- Producer chọn giọng/cấu hình, nghe thử và duyệt audio; sync và render tái sử dụng audio đã duyệt.
- Lưu artifact âm thanh, duration đo thực, provider, voice, speed và text hash;
  thay text/voice/speed phải làm audio cũ hết hiệu lực một cách rõ ràng.
- Không im lặng đổi provider khi lỗi, vì làm thay giọng và timing mà người dùng không biết.
- Nếu dùng tác vụ bất đồng bộ: cần job state, polling/callback, timeout và retry có kiểm soát.
- Đánh giá clipping, lệch âm lượng giữa cảnh, nhạc nền nếu có và độ khớp phụ đề.

**Acceptance đề xuất:** phát âm đúng thuật ngữ của nội dung đang làm; audio đã duyệt là audio vào MP4;
thay cấu hình buộc đo lại; lỗi provider giữ trạng thái chưa hoàn tất; caption không vượt khỏi cảnh.
Ngưỡng âm lượng/timing cụ thể cần team chốt trước khi gọi đây là “đạt”.

**Cần chốt:** giọng/vùng miền, tốc độ, ngân sách thử, quyền chọn của Producer,
có cho upload voiceover người đọc không và chính sách lưu audio.

### M02 — Ý tưởng video demo mentor: Bubble Sort

**Cách hiểu đã xác nhận:** Bubble Sort là ý tưởng nội dung cho một clip demo, không phải
chức năng mới của hệ thống. Câu “chọn một ngành cụ thể” áp dụng cho nội dung demo;
không chuyển STEMotion thành sản phẩm chuyên làm video thuật toán.

**Hiện trạng liên quan:** FE có `AlgorithmWalkthrough.tsx`, `codeSnippet`, `steps`, highlight
dòng code, `variableState` và note; có thể đánh giá khả năng tận dụng khi thực sự làm clip này.
Chuyển bước hiện theo nhịp cố định 2.5 giây; cần xem độ phù hợp với lời đọc ở clip cụ thể.
Việc không có engine Bubble Sort không tự tạo ra technical debt hay task phải bổ sung engine.

**Tính khả thi:** có thể chuẩn bị kịch bản, storyboard và ví dụ đã được người phụ trách kiểm tra,
rồi dùng pipeline hiện có để dựng demo. Công sức và chất lượng cần đánh giá qua brief/preview thực tế.
**Giá trị:** giúp mentor xem một sản phẩm có nội dung cụ thể và phản hồi chất lượng đầu ra.

**Đề xuất khi team chọn làm demo:** chốt đối tượng học, mục tiêu, thời lượng, ví dụ minh họa,
nguồn tham khảo và người duyệt. Kiểm tra lời giải thích, hình ảnh và lời đọc thống nhất,
các bước trong ví dụ đúng và chữ dễ đọc. Có thể dùng chương trình tham chiếu để kiểm tra ví dụ
nếu hữu ích; đây là lựa chọn hỗ trợ sản xuất nội dung, không phải yêu cầu xây module trong sản phẩm.

**Giới hạn phạm vi:** demo có brief và nghiệm thu riêng; không bắt buộc có API, bảng dữ liệu,
bộ sinh trace hay test suite Bubble Sort. Các task workspace/role/approval/UX và refactor tiếp tục
theo phụ thuộc của chúng, không phải đợi clip này. Tiêu chí chất lượng chung áp dụng theo chủ đề
video; không dùng Bubble Sort làm chuẩn bắt buộc cho mọi thay đổi của hệ thống.

### M03 — Phong cách học thuật tiết chế

**Hiện trạng:** `flat/theme.ts` dùng bảng màu bão hòa, gradient, font Baloo2;
nhiều template có glow, màu và class trực tiếp trong component. Theme hiện tại chưa bảo đảm
chuyển toàn bộ video sang academic/monochrome chỉ bằng một dropdown.

**Tính khả thi: cao. Giá trị: cao.** Cần audit từng renderer thay vì sửa riêng palette.

**Đề xuất:** một bộ quy tắc academic: nền sáng, chữ đậm rõ, font phù hợp, layout ổn định,
bỏ chuyển động nền và glow không phục vụ bài học. Nếu được phép, dùng tối đa một màu nhấn có ý nghĩa.
Nếu monochrome tuyệt đối, phân biệt trạng thái bằng viền, nhãn, độ đậm và ký hiệu.

**Acceptance đề xuất:** đọc rõ code/array/caption trên video; không chỉ dựa màu để hiểu state;
preview và MP4 cùng phong cách; không còn template hoặc caption dùng màu/theme cũ ngoài ý muốn.

**Cần chốt:** trắng đen tuyệt đối hay nền trung tính + màu nhấn; font, kích thước chữ,
background, guideline motion và có giữ phong cách cũ cho project lịch sử không.

### M04/M05 — AI linh hoạt và kiểm soát chất lượng

**Hiện trạng:** BE có embedded Gemini gateway và remote AI mode. `services/ai/app/providers.py`
đã có OpenAI/Gemini; chưa thấy Claude adapter. Có structured output/schema validation,
telemetry và QA kỹ thuật. Chưa có quy trình phản biện học thuật đầy đủ bằng AI + người duyệt.

**Tính khả thi: cao. Giá trị: cao nếu gắn với tiêu chí chất lượng.**

“Claude Opus” là diễn giải từ chữ “claude ops”, không phải tên model đã được xác nhận.
“GPT6” trong biên bản không được dùng như bằng chứng availability/API/model ID.
Provider/model/version thực tế phải được kiểm tra với tài khoản và API khi chọn.

**Đề xuất:** chia tác vụ viết kịch bản, phản biện kiến thức, storyboard và kiểm tra sản phẩm.
Chạy cùng bộ bài thử để so sánh chất lượng, latency, chi phí và độ ổn định structured output.
Model mạnh hơn không thay thế kiểm chứng kiến thức/ví dụ theo chủ đề, nghe audio, xem frame
và quyết định Reviewer.
AI QC nên đưa findings kèm lý do và vị trí, không chỉ một điểm số hoặc nhãn “PASS”.

**Acceptance đề xuất:** malformed/refused/timeout không tạo kết quả giả; tiêu chí lỗi có thể kiểm tra;
lưu model thực dùng và config/prompt version; người duyệt vẫn chịu trách nhiệm chấp nhận sản phẩm.

**Cần chốt:** AI chỉ hỗ trợ hay có quyền block; mức ngân sách; task nào dùng model nào;
độ tin cậy/nguồn tham khảo; phương án khi provider không sẵn sàng.

### M06 — Màn cấu hình API

**Hiện trạng:** cấu hình qua environment/properties; chưa có trang quản trị tích hợp đầy đủ.

**Tính khả thi: cao. Giá trị: cao.** Độ khó tăng nếu cho đổi credential và endpoint động.

**Đề xuất:**

| Phạm vi | Cấu hình |
|---|---|
| Admin hệ thống | Provider/model cho phép, credential reference, hạn mức, test kết nối, bật/tắt tích hợp |
| Project | Chọn model/voice/speed trong tập cho phép; lưu snapshot cấu hình đã duyệt |
| Job/artifact | Ghi cấu hình thực dùng để retry và truy vết |

Key ở server; UI chỉ hiển thị giá trị che/trạng thái đã cấu hình. API đọc không trả secret thật.
Nếu cấu hình lưu trong DB, cần quản lý mã hóa/key ngoài DB hoặc dùng secret reference.
Không cho nhập URL tùy ý từ quyền thấp; endpoint override cần kiểm soát đích kết nối.
Test kết nối phải nói rõ có tiêu phí hay chỉ kiểm tra cấu hình.

**Acceptance đề xuất:** thay default không đổi job đang chạy; retry dùng config snapshot;
không lộ secret trong response/log/export; thay đổi có actor/time; quyền project không đổi cấu hình hệ thống.

**Cần chốt:** cấu hình global hay workspace, BYOK có cần không, quyền sửa, budget,
effective time và cách rotate credential. Không mua gói hay đặt key chỉ dựa vào file này.

### M07 — Kịch bản chỉn chu và duyệt nội dung

**Hiện trạng:** có ScriptVersion, review/reject/comments; `ClipQaService` kiểm tra cấu trúc,
thời lượng, pacing, text, contrast, freshness và caption. Đây chưa phải bằng chứng nội dung học thuật đúng.

**Tính khả thi: cao. Giá trị: rất cao.**

**Đề xuất:** script có audience/prerequisite, learning objectives, lời đọc, hình ảnh từng đoạn,
thuật ngữ/cách đọc, ví dụ, nguồn tham khảo và lỗi hiểu nhầm cần tránh.
Reviewer duyệt nội dung lẫn storyboard trước production; thay nội dung đã duyệt cần version/re-review.

**Acceptance đề xuất:** mục tiêu bài học đo được; mọi scene phục vụ mục tiêu;
ví dụ và code nhất quán; giải thích không bỏ bước quan trọng; nguồn có thể truy lại.
Thời lượng video phải phù hợp chính sách mới: QA hiện có giới hạn clip, không mặc nhiên coi bài dài là hợp lệ.

**Cần chốt:** script schema tối thiểu, người duyệt chuyên môn, thời lượng mục tiêu và rubric.

### M08 — Thử Claude + Manim + Remotion

**Hiện trạng:** Remotion đã là renderer; chưa thấy pipeline Manim trong source đã kiểm tra.

**Tính khả thi: có. Giá trị: tùy bài. Độ phức tạp: trung bình–cao.**

| Thành phần | Vai trò đề xuất |
|---|---|
| Claude/model khác | Hỗ trợ script/storyboard/mã; người và test xác minh kết quả |
| Manim | Sinh đoạn minh họa thuật toán/toán học cần điều khiển chính xác |
| Remotion | Ghép scene, video nguồn, narration, caption và xuất MP4 cuối |

Manim có thể xuất video; Remotion có thể đưa video nguồn vào composition.
Đây là kết luận khả thi về mặt kỹ thuật từ tài liệu, chưa có proof-of-concept của STEMotion.

**Đánh đổi:**

- Đoạn Manim đã rasterize thành video không còn là các khối Lego chỉnh riêng trong browser.
- Muốn sửa dữ liệu/hoạt ảnh cần sửa tham số hoặc scene specification và render lại.
- Thêm Python/runtime, dependencies, job lifecycle, cache/artifact và bước chờ preview.
- AI-generated Python nếu được chạy phải cách ly, giới hạn thời gian/CPU/memory và quyền truy cập;
  không chạy trong backend chính hoặc cùng credential production.

**Đề xuất:** làm hai prototype nhỏ trên cùng một cảnh minh họa đại diện: Remotion thuần và Manim + Remotion.
Có thể dùng cảnh từ video demo nếu phù hợp; không bắt buộc chọn Bubble Sort hoặc chờ hoàn tất demo.
So sánh tính đúng, chất lượng, khả năng chỉnh, thời gian render và vận hành rồi quyết định.
Ưu tiên specification/parameters và template Manim được kiểm soát hơn sinh/chạy code tự do.

**Acceptance đề xuất:** output đạt fps/resolution/duration chuẩn; ghép không lệch audio;
tham số thay đổi tái tạo được; render lỗi không publish artifact cũ; video nguồn có provenance.

### M09 — Producer cần kiểm soát sản phẩm thực sự

**Hiện trạng:** Producer đã chỉnh theme, thuộc tính scene, Lego, timing, sync và gửi duyệt;
không chỉ monitor. Tuy nhiên công cụ hiện tại có thể chưa đủ kiểm soát chất lượng/nội dung chuyển động.
Producer đang chỉnh composition dựa trên script đã duyệt; cấu trúc scene/template có ràng buộc.

**Tính khả thi: cao theo lát nhỏ. Giá trị: cao.**

**Đề xuất:** storyboard, audio preview/approval, timing của hành động chính,
đối chiếu preview/render, chọn artifact minh họa, lịch sử bản dựng và phản hồi QA.
Không mặc nhiên mở quyền sửa script đã duyệt chỉ để Producer làm được nhiều việc hơn.

**Cần chốt:** Producer được sửa audio/text/scene structure đến đâu, thay đổi nào cần Reviewer duyệt lại,
và mức timeline editor cần có trong phạm vi đồ án.

### M10/M11 — Workspace đủ người, đủ ba role, nhiều người cùng role

**Hiện trạng:** `WorkspaceMember` unique theo `(workspace_id, user_id)` và có một `role`, `isLeader`, `status`.
Nhiều user cùng role đã được mô hình cho phép. Tạo workspace hiện chỉ gán một Leader;
chưa validate ba người/ba role.

**Tính khả thi: cao. Giá trị: cao.**

**Đề xuất cách hiểu:** Admin chọn ít nhất ba user ACTIVE khác nhau; ít nhất một Writer,
một Reviewer, một Producer; trong đó một hoặc nhiều người được đánh dấu Leader theo chính sách đã chốt.
Leader không thay thế một trong ba role sản xuất. Tạo workspace + memberships nguyên tử trong một transaction.

**Ràng buộc cần duy trì:** nếu xóa/vô hiệu hóa/đổi role làm mất người cuối cùng của một role,
phải chặn hoặc chuyển workspace sang trạng thái chưa đủ điều kiện và chặn khởi tạo công việc mới.
Phương án nào áp dụng cần quyết định; không chỉ kiểm tra đúng lúc create rồi bỏ qua về sau.

**Acceptance đề xuất:** thiếu role, trùng user, user inactive, role ADMIN thay role sản xuất đều bị từ chối;
lỗi giữa chừng không để workspace mồ côi; hai yêu cầu quản lý đồng thời không phá invariant.
Workspace cũ thiếu nhân sự cần remediation, không tự gán người hoặc tạo tài khoản giả.

### M12 — Leader duyệt khởi tạo và tham gia project

**Hiện trạng:** Writer trong workspace được tạo project trực tiếp. Chưa có project membership/approval
workflow riêng; phần lớn quyền suy từ workspace.

**Tính khả thi: cao. Giá trị: cao. Độ thay đổi: lớn.**

**Đề xuất:**

1. Writer tạo đề xuất nháp: chủ đề, mục tiêu, phạm vi, người phụ trách; chưa là project sản xuất chính thức.
2. Gửi Leader duyệt; reject có lý do, được sửa và gửi lại theo quy tắc cần chốt.
3. Approve tạo/activate project đúng một lần; Writer khởi tạo là participant được ghi nhận.
4. Thành viên workspace xin tham gia hoặc được đề nghị phân công vào project với role xác định.
5. Leader duyệt; chỉ participant được duyệt mới có quyền ghi theo role.

Ngoại lệ từ biên bản: Writer đồng thời là Leader có thể tự thông qua bước khởi tạo.
Điều này không đồng nghĩa tự duyệt chất lượng nội dung/video của chính mình.

**Acceptance đề xuất:** không bypass bằng API; approve lặp không tạo hai project/membership;
người ngoài workspace không được xin/duyệt sai phạm vi; reject có audit; dữ liệu đề xuất không mất khi lỗi.

**Cần chốt:** approver khi có nhiều Leader; cần đủ ba role participant để bắt đầu hay để gửi duyệt;
Leader tự approve yêu cầu tham gia của mình thế nào; thu hồi/xử lý participant nghỉ việc;
thành viên không tham gia được xem project đến mức nào.

### M13 — Contribution cho từng video

**Hiện trạng:** đã có owner, createdBy của version, producer của composition và review/status history.
Chưa có một mô hình đóng góp thống nhất. Membership được duyệt không chứng minh đã đóng góp.

**Tính khả thi: cao. Giá trị: cao.**

**Đề xuất:** participant roster + lịch sử sự kiện có ý nghĩa: checkpoint script, revision composition,
review decision, xử lý góp ý, duyệt audio, xuất bản. Event gồm actor, role snapshot, project,
artifact/version, action, server time; giữ lịch sử khi thành viên rời nhóm.
Nên ghi nhận event cùng transaction của hành động hoặc có cơ chế bền để tránh dữ liệu lệch.

Không dùng số click/autosave làm điểm đóng góp. Có thể gom autosave thành activity/checkpoint.
Chưa tự tính phần trăm đóng góp, xếp hạng hoặc credit mặc định từ số lượng event.

**Acceptance đề xuất:** retry không nhân đôi event; người xem truy được phiên bản đóng góp;
role hiển thị đúng lúc thực hiện; dữ liệu lịch sử không đổi khi membership đổi role.

**Cần chốt:** contribution dùng để credit, theo dõi hay chấm điểm; ai xem được;
cho nhập đóng góp thủ công không; có cần participant xác nhận credit cuối video không.

### M14 — Nhiều workspace/project, role cố định và tính khách quan

**Hiện trạng:** nhiều workspace và một role/workspace đã có. Quyền project trong `WorkspaceAccess`
được suy từ active workspace membership hiện tại. Chưa có role snapshot riêng theo project.
Đổi workspace role vì thế có thể làm thay quyền ở các project cũ.

**Tính khả thi: cao. Giá trị: rất cao. Độ thay đổi: lớn và xuyên nhiều API.**

**Phương án đề xuất, chưa duyệt:**

| Phạm vi | Vai trò |
|---|---|
| Account | Quyền quản trị hệ thống, không mặc nhiên thay role sản xuất |
| Workspace membership | Role mặc định để phân công; Leader là trách nhiệm quản lý bổ sung |
| Project membership | Role được chốt tại thời điểm tham gia được duyệt; bất biến trong vòng đời project |
| Activity/review event | Actor và role tại thời điểm thực hiện; tham chiếu artifact/version |

Ví dụ: An là Writer trong X. Sau đó role mặc định trong tổ đổi thành Producer để nhận việc mới;
trong X, An vẫn là Writer và không trở thành Reviewer của phần mình đã viết.

Membership ACTIVE vẫn là điều kiện an ninh: snapshot lịch sử không được làm người đã bị vô hiệu hóa
tiếp tục có quyền ghi. Cần phân biệt role bất biến với trạng thái truy cập/thu hồi quyền.
Nếu cần thay người, dùng một luồng reassignment có audit; không sửa âm thầm role cũ.
Admin bypass hiện tại phải được rà lại: quản trị vận hành không nên vô tình phá nguyên tắc độc lập duyệt.

**Acceptance đề xuất:** đổi role mặc định không đổi quyền project đang làm;
FE chọn đúng role project khi mở clip; toàn bộ mutation kiểm tra participant role server-side;
tự duyệt bị chặn theo policy; đổi tổ hoặc giả mạo project ID không vượt quyền;
inactive participant không được ghi dù giữ snapshot lịch sử.

**Cần chốt:** cố định ở workspace hay project; có cho khác role giữa các project cùng workspace không;
thay người, Admin override và role sau khi project đóng. Đây là quyết định schema/auth phải chốt trước code.

### M15 — Leader quản lý role nhưng không thêm/xóa member

**Hiện trạng:** interceptor coi các mutation membership là quản lý workspace; Leader hoặc Admin
được phép. UI ManageMembers cũng mở các thao tác thêm/xóa cho Leader.

**Tính khả thi: cao. Giá trị: cao.** Phải sửa cả authorization BE lẫn UI, không chỉ ẩn nút.

**Ma trận đề xuất:**

| Hành động | Admin | Leader |
|---|---|---|
| Tạo workspace, thêm/xóa người khỏi workspace | Có | Không |
| Bổ nhiệm/thu hồi Leader | Có | Không, trừ khi team chốt khác |
| Phân công role workspace | Có | Có trong tổ mình, không phá coverage và role project đã chốt |
| Duyệt đề xuất project/yêu cầu tham gia | Quyền quản trị cần chốt | Có trong tổ mình |
| Sửa role project đã chốt trực tiếp | Không | Không |
| Duyệt chất lượng nội dung/video | Theo project role/policy độc lập cần chốt | Không tự có chỉ vì là Leader |

Approve người đã thuộc tổ tham gia project khác với thêm người mới vào tổ.
Cần quyết định vô hiệu hóa/kích hoạt membership có thuộc quyền Admin-only không;
đề xuất có, để Leader không dùng status toggle thay thao tác xóa.

### M16 — Dashboard Leader

**Hiện trạng:** có dashboard project và Admin Portal; chưa có dashboard riêng theo trách nhiệm mới.

**Tính khả thi: cao. Giá trị: cao sau khi có dữ liệu workflow.**

**Đề xuất:** inbox đề xuất project, yêu cầu tham gia; danh sách project theo stage/người phụ trách;
blocker thiếu nhân sự/audio/review/render; lịch sử phân công/contribution; deadline nếu team có quản lý hạn.
Không chỉ trình bày tổng số project hoặc chart trang trí.

**Acceptance đề xuất:** số liệu cùng nguồn với trạng thái project thực; pagination/filter server-side;
chỉ thấy workspace có quyền; approve/reject cập nhật đúng một lần; click mở đúng project/yêu cầu;
trạng thái lỗi không giả thành không có công việc.

### M17 — Khóa phiên chỉnh sửa project

**Hiện trạng:** có khóa nghiệp vụ khi review/approve/publish và composition `baseRevision`/409.
Có lock transaction khi save composition; đó không phải khóa phiên người dùng.
Chưa thấy edit lease có owner/session/token/expiry. Script draft cũng cần rà concurrency protection riêng.

**Tính khả thi: cao. Giá trị: rất cao với nhiều participant.**

| Cơ chế | Mục đích |
|---|---|
| Business state lock | Cấm sửa nội dung đã chuyển sang bước không cho sửa |
| Revision | Phát hiện bản ghi dựa trên dữ liệu cũ |
| Edit lease | Chỉ một phiên được phép ghi tài nguyên đang chỉnh trong một khoảng thời gian |

**Đề xuất MVP:** khóa toàn phần chỉnh sửa nội dung project; người khác vẫn được xem.
Khóa do BE cấp nguyên tử, gắn project/resource + user + session + token/generation + expiresAt;
gia hạn định kỳ; mất kết nối không gia hạn được thì chuyển read-only và giữ draft local.
Release khi rời trang là best effort; expiry giải quyết tab crash. Mọi write endpoint liên quan phải
kiểm tra token/generation hợp lệ, kể cả request đến muộn sau khi khóa đã cấp cho người khác.
Không chỉ kiểm tra lúc mở editor. Vẫn giữ revision và immutable snapshot khi submit/render.

Không giữ transaction hoặc DB row lock mở trong suốt thời gian người dùng chỉnh.
Granularity project/script/composition, thời hạn lease và quyền force-release cần chốt.

**Acceptance đề xuất:** hai user acquire đồng thời chỉ một thành công; hai tab cùng user không ghi chồng;
tab crash được giải phóng qua TTL; token cũ không ghi sau khi lock chuyển chủ;
request save đến muộn không vượt revision; chỉ reader không chiếm khóa;
submit/render không lấy nội dung đang thay đổi ngoài snapshot đã xác định.

**Cần chốt:** comments có được ghi khi người khác edit không; review quyết định trên phiên bản nào;
Admin/Leader có được thu hồi khóa không; UX khôi phục draft khi mất quyền giữ khóa.

## 6. Kho asset hiện tại và ảnh hưởng của hướng mới

### 6.1. Kho đang ở đâu

| Nhóm | Nguồn hiện tại |
|---|---|
| Icon STEM | FE `src/remotion/flat/stemIcons.ts`, component Phosphor |
| Hình/hoạt họa chuyên biệt | FE `src/remotion/director/assets/{general,physics,chemistry}/` |
| Danh mục Director | FE `src/remotion/director/registry.json`, BE resource `director-registry.json` dùng validator |
| Khối Lego | FE `src/remotion/composed/blocks.tsx`, `src/remotion/lego/catalog.ts`, `decompose.ts` |
| Template phân cảnh | FE `src/remotion/compositions/`, `src/constants/templatesCatalog.ts` |
| Kho Template UI | Chọn loại phân cảnh; không phải kho quản trị file media đầy đủ |
| Thư Viện Media UI | Clip đã xuất bản; không phải danh sách toàn bộ icon/asset nguồn |
| Worker | Bundle source Remotion từ FE; Docker mang theo FE src/public để render |

Các hình giống nhau giữa video chủ yếu đến từ component/icon/template/motion tái sử dụng.
Đây chưa phải một hệ quản trị tài sản số tập trung. Chưa thấy upload/search/version/approval
đầy đủ cho mọi loại asset; không nên hứa một “asset library” đã có chỉ vì có registry code.

### 6.2. Hướng quản lý cần xem xét sau khi chốt pipeline

- Phân biệt built-in procedural asset, template, audio artifact, video Manim và media upload.
- Có ID/version, loại, môn/chủ đề, thumbnail/preview, schema tham số, license/source,
  khả năng chỉnh, renderer tương thích và trạng thái được duyệt.
- Project tham chiếu phiên bản asset xác định để thay thư viện không âm thầm đổi video cũ.
- Nếu dùng Manim: quản lý specification/mã template/parameters cùng video artifact;
  không làm người dùng tưởng video Manim có thể sửa từng hình bằng Lego.
- Built-in code assets cần release đồng bộ FE/worker; metadata nằm DB không tự triển khai renderer mới.
- Upload ngoài cần scope quyền, validation, storage và retention riêng; chưa nằm trong quyết định đã chốt.

## 7. Kiến trúc đích sơ bộ — phương án để review

### 7.1. Workflow nghiệp vụ đề xuất

```text
Admin tạo workspace đủ Writer + Reviewer + Producer, chỉ định Leader
    ↓
Writer đề xuất project → Leader approve/reject
    ↓
Leader duyệt participant → chốt project role
    ↓
Writer nhận edit lease → kịch bản + storyboard → Reviewer duyệt version
    ↓
Producer nhận edit lease → dựng + audio + sync → Reviewer duyệt composition xác định
    ↓
Render snapshot → QC artifact thật → xuất bản
    ↓
Contribution và lịch sử được lưu xuyên suốt
```

Đây là flow đề xuất, chưa là state machine được duyệt. Đặc biệt, cần chốt Reviewer có được
phê duyệt trước render như hiện tại hay có thêm final-artifact acceptance sau render.
Web preview đúng không bảo đảm MP4/audio cuối đúng; nên có final QC trước publication.

### 7.2. Dữ liệu có thể cần bổ sung

Tên dưới đây là tên khái niệm, không phải migration/schema cuối:

| Khái niệm | Vai trò |
|---|---|
| ProjectProposal | Đề xuất khởi tạo, người gửi, approver, trạng thái, lý do |
| ProjectJoinRequest | Xin/phân công tham gia, role đề xuất, quyết định và actor |
| ProjectMember | User/project, role snapshot, approvedAt/by, access status |
| ContributionEvent | Sự kiện đóng góp gắn user/role/artifact/version |
| EditLease | Quyền chỉnh sửa có hạn, session/token/generation và expiry |
| IntegrationConfigVersion | Cấu hình provider/model/voice, secret reference, audit |
| NarrationArtifact | Audio đã sinh/duyệt, text/config hash, duration và nguồn |
| VisualArtifact | Video Manim hoặc render nguồn gắn spec/config/source version |

Tận dụng dữ liệu/version/audit hiện có khi phù hợp. Không tạo bảng trùng chức năng chỉ vì
tên trong đề xuất. Không nhét mọi approval vào scriptStatus/videoStatus nếu làm mất ý nghĩa lifecycle.
Ý tưởng video Bubble Sort không làm phát sinh bảng/API mô phỏng. Dữ liệu kiểm chứng chuyên biệt
theo chủ đề chỉ được thiết kế nếu có nhu cầu sản phẩm và task riêng được duyệt.

## 8. Danh sách quyết định cần duyệt

| ID | Quyết định | Phương án đề xuất | Trạng thái |
|---|---|---|---|
| D01 | Phạm vi ý tưởng demo | Bubble Sort chỉ là ý tưởng chủ đề clip mentor; không giới hạn sản phẩm hay chặn refactor | Đã xác nhận cách hiểu; lịch làm demo chưa chốt |
| D02 | Brief video demo | Audience, mục tiêu, thời lượng, ví dụ và rubric của clip được chọn | Chưa chốt |
| D03 | Trắng đen tuyệt đối hay màu nhấn | Academic trung tính; màu chỉ mang nghĩa nếu mentor cho phép | Chưa chốt |
| D04 | TTS/provider/voice và ngân sách | Nghe blind/A-B trên cùng script rồi chọn | Chưa chốt |
| D05 | Vai trò Manim | Prototype đối chiếu Remotion thuần trước tích hợp | Chưa chốt |
| D06 | AI QC có quyền block không | Advisory + bằng chứng; người duyệt quyết định | Chưa chốt |
| D07 | Config global/workspace/project, BYOK | Admin tích hợp; project dùng cấu hình được phép, có snapshot | Chưa chốt |
| D08 | Role cố định ở đâu | Một role mặc định/workspace, role bất biến theo project | Chưa chốt |
| D09 | Khác role giữa project cùng workspace | Cần quyết định rõ trước schema/auth | Chưa chốt |
| D10 | Thiếu một role sau khi workspace hoạt động | Chặn thay đổi hoặc chặn công việc mới với trạng thái thiếu nhân sự | Chưa chốt |
| D11 | Quyền Leader | Gán role/duyệt công việc; Admin-only thêm/xóa/status/bổ nhiệm Leader | Chưa chốt |
| D12 | Tự approve và nhiều Leader | Ngoại lệ khởi tạo cho Writer-Leader; không tự duyệt chất lượng | Chưa chốt |
| D13 | Project participant và khả năng đọc của non-participant | Write theo role đã duyệt; quyền read cần chốt | Chưa chốt |
| D14 | Khi nào cần đủ ba role project | Trước activate hoặc trước gửi review; cần chọn | Chưa chốt |
| D15 | Reassignment, nghỉ việc, Admin override | Thu hồi access nhưng giữ lịch sử, workflow thay người có audit | Chưa chốt |
| D16 | Edit lease granularity/TTL/force release | MVP một editor nội dung/project, read-only cho người khác | Chưa chốt |
| D17 | Comments/review trong khi edit | Review trên version xác định; comment policy cần chốt | Chưa chốt |
| D18 | Contribution dùng để làm gì | Credit/truy vết trước, chưa quy phần trăm/chấm điểm tự động | Chưa chốt |
| D19 | Mức dựng của Producer | Timing/audio/storyboard/artifact; quyền đổi script/structure cần chốt | Chưa chốt |
| D20 | Final QC trước publish | Kiểm audio/video artifact thật sau render | Chưa chốt |
| D21 | Workspace/project cũ | Kế hoạch backfill và remediation, không tự invent role/approver | Chưa chốt |
| D22 | Asset thử nghiệm local | Giữ, sửa hay bỏ sau khi chọn hướng; chưa đưa vào release | Chưa chốt |

## 9. Tiêu chí nghiệm thu xuyên suốt

| Nhóm | Bằng chứng cần có |
|---|---|
| Kiến thức | Nội dung có nguồn, ví dụ/tính toán/minh họa được kiểm chứng phù hợp chủ đề, Reviewer chuyên môn |
| Kịch bản | Mục tiêu, storyboard, ví dụ, thuật ngữ và version được duyệt |
| Hình ảnh | Frame/clip desktop + màn nhỏ, chữ đọc được, chuyển động có mục đích |
| Audio | Người nghe đánh giá cùng script; âm lượng/phát âm/timing đạt ngưỡng được chốt |
| Preview/render | Cùng snapshot/config/artifact, đối chiếu đúng SHA renderer và revision |
| Authorization | Negative tests từng endpoint; không chỉ test ẩn/disable nút |
| Workspace | Invariant đủ người/role, atomic creation và mutation đồng thời |
| Project membership | Approve idempotent; role snapshot bất biến; access thu hồi đúng |
| Edit lease | Acquire race, expiry, late writes, tab crash, hai tab và mất mạng |
| Contribution | Event đúng actor/role/version, không duplicate do retry, không sửa lịch sử |
| UX | Pending tức thì, chống bấm trùng, lỗi giữ input, retry rõ, stale response bị bỏ |
| Vận hành | Job lỗi/timeout/retry, budget/quota, storage lifetime, không false success |

Chưa đặt những ngưỡng MOS/LUFS/timing/latency tự chọn thành yêu cầu mentor.
Team cần định nghĩa ngưỡng trước benchmark và ghi bằng chứng thay vì chỉ tick “PASS”.

## 10. Migration, bảo toàn dữ liệu và rủi ro refactor

- Additive migration trước; không sửa lịch sử Flyway đã apply để ép schema mới.
- Workspace cũ có thể chỉ có một Leader. Phải thống kê và đưa vào luồng bổ sung nhân sự;
  không gán user bất kỳ để đủ ba role.
- Project cũ có owner nhưng không có danh sách participant đầy đủ. Có thể backfill owner/actor
  được chứng minh từ dữ liệu, nhưng không suy ra mọi thành viên workspace đều là contributor.
- Role lịch sử không khôi phục được đáng tin cậy thì ghi trạng thái legacy/unknown hoặc chờ xác nhận;
  không ghi một role đoán rồi tuyên bố là snapshot thật.
- Audit các bypass Admin, API AI, TTS, sync, comments, render/retry, publish, archive và asset;
  không chỉ sửa ScriptController.
- Client autosave cũ có thể không có lease token. Cần kế hoạch rollout/API compatibility hoặc
  version gate để tránh old client ghi vượt cơ chế mới.
- Nếu job tham chiếu model/voice/asset mới, worker cũ phải reject rõ hoặc được nâng đồng bộ;
  không render bằng fallback ngoài ý muốn.
- Test migration trên môi trường tách biệt; backup và rollback/roll-forward được review trước production.
- Không rewrite Git history, rotate secret, đổi hosting hoặc chạy migration production trong task review tài liệu.

## 11. Thứ tự triển khai đề xuất và phụ thuộc

Ước lượng dưới đây là độ khó tương đối, không phải cam kết deadline hay phân công đã chốt.

| Giai đoạn | Nội dung | Đầu ra review được | Độ khó | Phụ thuộc |
|---|---|---|---|---|
| 0 | Duyệt biên bản và policy | Quyết định D01–D22 theo phạm vi từng nhánh công việc | Trung bình | Team + mentor/product owner |
| 1 (nhánh demo độc lập) | Video demo mentor nếu team chọn làm | Brief, script/storyboard, ví dụ đúng và clip được duyệt; Bubble Sort là ý tưởng chủ đề | Theo brief | Quyết định làm demo và brief riêng; không chặn các giai đoạn khác |
| 2 | Spike công cụ | Remotion thuần vs Manim; Vbee vs hiện tại; model benchmark | Trung bình | Ngân sách và sample đã chốt |
| 3 | Thiết kế membership/auth | ERD, permission matrix, proposal/join state machine, migration plan | Cao | D08–D15 |
| 4 | Workspace + approval + role snapshot | BE API/tests, FE flow tạo/duyệt/tham gia | Cao | Thiết kế giai đoạn 3 |
| 5 | Edit lease + revision + contribution | Một editor, lịch sử đúng actor/role/version | Cao | Membership/auth chuẩn |
| 6 | Dashboard Leader | Inbox quyết định, board tiến độ và blockers | Trung bình | Dữ liệu workflow thật |
| 7 | Production quality pipeline | Audio artifact/config snapshot, timing, final QC, Manim nếu được chọn | Cao | Kết quả prototype và phạm vi Producer |
| 8 | Quản lý asset phù hợp pipeline | Catalog/version/provenance/preview và quyền được duyệt | Trung bình–cao | Asset model và renderer đã chốt |
| 9 | Tích hợp/nghiệm thu/release | E2E ba người, failure cases, video thử nghiệm theo tính năng/chủ đề, release tuple | Cao | Các tính năng thuộc release đã nghiệm thu; không phụ thuộc demo Bubble Sort |

Các số giai đoạn không phải yêu cầu thực hiện tuần tự mọi nhánh. Membership/auth phụ thuộc
quyết định nghiệp vụ, quality pipeline phụ thuộc brief/prototype tương ứng; cả hai không phụ thuộc
việc làm xong video demo. Có thể thiết kế nghiệp vụ song song với prototype nội dung nếu phân công rõ.
Không mở rộng kho asset trước để rồi phải sửa lại hoàn toàn khi đổi pipeline.

### Phân công gợi ý — chưa giao việc

- Nội dung/UX QA: brief, script, rubric theo chủ đề, trải nghiệm role; demo mentor có task riêng nếu chọn làm.
- BE: invariants workspace, membership/approval/auth, lease, audit và migration.
- FE: role theo project, approval UI, Leader dashboard, read-only/lease UX, config UX, academic layout.
- AI/worker: adapters, scene specification, audio artifacts, Manim nếu chọn, render snapshots và runtime isolation.
- Infra: credential/storage/queue/cost/deployment; chỉ triển khai khi có task và quyền riêng.

## 12. Liên hệ với debt và công việc trước đó

- `docs/TECH_DEBT_UX_BACKLOG_2026-10-23.md`: giữ làm lịch sử và backlog kỹ thuật;
  kế hoạch này bổ sung yêu cầu mentor, không chứng minh debt cũ đã hoàn tất.
- `STEMotion_FE/docs/UX_QA_CHECKLIST.md`: tiếp tục áp dụng cho pending/error/draft/navigation,
  modal, responsive và acceptance; cần mở rộng cases approval/membership/lease sau khi policy chốt.
- UX commit `2be8fff` đã push; asset thử nghiệm sau đó chưa push và không thuộc release này.
- Pagination/bundle, render reliability, storage lifetime, TTS consistency và release tuple vẫn liên quan.
  Khi refactor cần gắn lại ticket/phụ thuộc, tránh mất các vấn đề đã biết.
- Biên bản không tự đưa các ý tưởng YouTube OAuth, multitrack editor, realtime co-editing,
  chấm điểm contribution hoặc upload asset không giới hạn thành scope bắt buộc.

## 13. Mẫu phản hồi để teammate và AI duyệt

Với mỗi Mxx hoặc Dxx, phản hồi theo mẫu:

```text
ID:
Kết luận: Đồng ý / Đồng ý có điều kiện / Cần sửa / Chưa đủ thông tin
Cách hiểu nghiệp vụ:
Bằng chứng source/schema/runtime mới (repo + SHA + đường dẫn):
Đề xuất thay thế nếu có:
Ảnh hưởng FE / BE / AI / worker / DB / infra:
Acceptance criteria và negative cases:
Phụ thuộc và rủi ro migration:
Owner đề xuất và ưu tiên:
Quyết định cần người phụ trách xác nhận:
```

Checklist trước khi chuyển tài liệu thành task triển khai:

- [ ] Team xác nhận các diễn giải từ biên bản, nhất là “claude ops”, monochrome và phạm vi Producer.
- [ ] Chốt role workspace/project, self-approval, Admin override, read/write của non-participant.
- [ ] Chốt invariant workspace trước/sau khi tạo và migration dữ liệu cũ.
- [ ] Chốt edit lease, revision, release/expiry và phạm vi endpoint được bảo vệ.
- [ ] Chốt phạm vi/brief cho video demo khi quyết định làm; không dùng demo làm điều kiện chặn refactor.
- [ ] Chốt hướng Manim/Remotion theo prototype riêng và nhu cầu minh họa thực tế.
- [ ] Chốt audio/model/config budget và quyền quản trị.
- [ ] Chốt contribution là lịch sử/credit hay đánh giá hiệu suất.
- [ ] Chia ticket có owner, acceptance, dependency; xác nhận thứ tự rollout và test tích hợp.
- [ ] Quyết định xử lý asset thử nghiệm local trước khi commit batch tiếp theo.

## 14. Bản đồ source tham khảo

Đường dẫn dưới đây tương đối theo workspace để dễ đọc trên máy teammate.
BE/worker cần đọc tại SHA/ref đã nêu; checkout local có thể cũ hơn remote.

| Chủ đề | Source |
|---|---|
| Workspace create/membership | BE `domain/workspace/service/WorkspaceService.java`, `dto/WorkspaceDtos.java`, `entity/WorkspaceMember.java`, `controller/WorkspaceController.java` dưới `src/main/java/com/stemotion/` |
| Authorization workspace/project | BE `src/main/java/com/stemotion/core/security/WorkspaceAccess.java`, `ResourceAccessInterceptor.java` |
| Project lifecycle | BE `domain/project/entity/ClipProject.java`, `domain/script/service/ScriptService.java`, `controller/ScriptController.java` dưới package `com/stemotion` |
| Composition/revision/lock nghiệp vụ | BE `domain/production/service/CompositionService.java`, `entity/Composition.java` |
| QA kỹ thuật | BE `domain/review/service/ClipQaService.java` |
| Preview/sync narration | BE `domain/production/service/NarrationToolsService.java`, `integration/client/RenderServiceClient.java` |
| AI gateway/config | BE `integration/client/AiGateway.java`, `integration/config/IntegrationProperties.java`, `src/main/resources/application.yml` |
| AI providers | BE `services/ai/app/providers.py`, `main.py`, `schemas.py`, `validation.py` |
| TTS/render config | Worker `src/tts.ts`, `config.ts`, `renderer.ts`, `server.ts`, `storage.ts` |
| Worker dùng FE source | Worker `src/bundler.ts`, `Dockerfile` |
| Algorithm renderer | FE `src/remotion/compositions/AlgorithmWalkthrough.tsx`, `src/types/stem.ts` |
| Visual style | FE `src/remotion/flat/theme.ts`, `FlatStage.tsx`, `compositions/`, `director/` |
| Producer/Lego | FE `src/components/views/ProducerStudio/`, `src/remotion/lego/`, `src/remotion/composed/` |
| Admin/members UI | FE `src/components/views/AdminPortal/`, `src/components/modals/ManageMembersModal.tsx` |

## 15. Nguồn ngoài đã kiểm tra và phạm vi kết luận

- Vbee: <https://api-docs.vbee.vn/> — xác nhận khả năng tích hợp TTS HTTPS,
  mô tả cách nhận kết quả đồng bộ/bất đồng bộ. Chưa benchmark audio, quota hay chi phí của tài khoản nhóm.
- Chỉ mục Vbee: <https://api-docs.vbee.vn/llms.txt> — danh mục tài liệu TTS, callback,
  truy vấn request và voices. Chưa xác nhận toàn bộ payload của endpoint thực tế.
- Manim quickstart: <https://docs.manim.community/en/stable/tutorials/quickstart.html> — tạo hoạt ảnh lập trình và xuất video.
- Manim output: <https://docs.manim.community/en/stable/tutorials/output_and_config.html> — cấu hình artifact/output;
  khi triển khai phải pin phiên bản và kiểm tra dependency thực tế.
- Remotion video: <https://www.remotion.dev/docs/offthreadvideo> — hỗ trợ đưa video file/URL
  vào composition. Không đồng nghĩa pipeline Manim đã tồn tại trong STEMotion.

Việc kết hợp các công cụ thành kiến trúc STEMotion là **đề xuất suy ra từ khả năng kỹ thuật và source**,
chưa phải phương án đã benchmark hoặc được mentor/team duyệt. Tài liệu không chốt model ID,
giá dịch vụ, nhà cung cấp hay quyền mua/gọi API trả phí.

## 16. Nhật ký duyệt

| Ngày | Người duyệt | ID liên quan | Quyết định / điều kiện | Trạng thái |
|---|---|---|---|---|
| 26/10/2026 | Chưa có | Toàn tài liệu | Bản tổng hợp đầu tiên từ biên bản và đối chiếu source | Chờ review |
| 27/10/2026 | LeTran xác nhận phân công | V22 / bàn giao teammate | V22 đã xử lý; verify/fix production thuộc phía LeTran; teammate tập trung BE/design/tests/contracts | Đã ghi nhận phân công; Phần B chờ chốt |

**Bước tiếp theo của tài liệu:** teammate/AI phản biện và chủ sở hữu chốt quyết định;
sau đó mới tạo kế hoạch triển khai cụ thể. Chưa triển khai refactor theo tài liệu này.
