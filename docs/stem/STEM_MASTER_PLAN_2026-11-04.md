# STEMotion: Plan bổ sung nghiệp vụ STEM sau Review 2

Ngày 04/11/2026. Trạng thái: **plan để triển khai, chưa có code, chưa migration**.
Baseline: BE `04f49ab`, FE `9fe67cf`, RENDER `3344a29`.

Tài liệu này thay thế bộ plan ngày 03/11 khi hai bên khác nhau. Bộ gốc nằm ở [reference-2026-11-03/](reference-2026-11-03/README.md), vẫn dùng để tra audit A01–A18, nguồn văn bản, ba gói demo và sơ đồ TO-BE.

## 0. Đọc nhanh

**Vấn đề Review 2 nêu (03/11/2026):** web đang giống công cụ quản lý làm clip, thiếu bối cảnh và thiếu nội dung STEM có ràng buộc theo cấp học.

**Mục tiêu:** mỗi clip phải trả lời được: dành cho học sinh lớp nào, dùng trong bối cảnh nào, bám yêu cầu cần đạt nào của chương trình, học sinh làm gì sau khi xem, S/T/E/M nằm ở đâu, ai đã thẩm định.

**Cách làm:** giữ nguyên pipeline Writer → Reviewer → Producer → Reviewer → Publish và Remotion. Thêm package `domain/stem` trong BE: hồ sơ STEM (`StemBrief`) gắn với từng phiên bản script, có gate ở submit, approve, render, publish.

**Thứ tự:** chốt bối cảnh và căn cứ (Giai đoạn 0) → lát cắt brief + gate + rubric (Giai đoạn 1) → bảo vệ nội dung khi dựng và xuất bản (Giai đoạn 2) → AI, thư viện, ba demo (Giai đoạn 3).

**Khác bản 03/11:**

| # | Thay đổi | Lý do |
|---|---|---|
| 1 | Bối cảnh thành dữ liệu có cấu trúc (`usage_context`) kéo theo ràng buộc riêng; hỏi giáo viên chuyển lên Giai đoạn 0 | Một ô chữ tự do không giải quyết nhận xét của reviewer |
| 2 | Phân loại trước các field trình bày (`durationInFrames`, `captionWords`, theme…) | Nếu không, mỗi lần "Tự Khớp Giọng Đọc" sẽ làm hỏng kết quả duyệt STEM |
| 3 | Thêm điều kiện tiên quyết về auth cutover, số Flyway, RLS | Source hiện đòi schema auth/roles chưa áp dụng lên DB dùng chung |
| 4 | Gate video đặt ở `FeedbackService`, không phải `ScriptService` | `approveVideo`, `sendBackToProduction`, `submitForVideoReview` nằm ở đó |
| 5 | Thêm chế độ `OFF/ADVISORY/STRICT` theo workspace | Rollout an toàn, bộ E2E hiện có không bị vỡ |
| 6 | Quy định xử lý khi xóa scene và khi tạo version mới | `copyScenes` tạo ID mới, bản đầu chưa định nghĩa |
| 7 | Lỗi validation dùng **400** + `details`, không dùng 422 | Khớp quy ước API hiện có và form BM |
| 8 | Đánh số theo Report 3 chính thức (09/11/2026): FR-62…73, UC-43…67; FR-56…61 dành cho auth v2 | Tránh hai hệ mã song song |
| 9 | Chỉ người có ghế REVIEWER mới ghi được quyết định STEM | `WorkspaceAccess` cho Admin qua mọi kiểm tra role |

## 1. Phạm vi

**Làm:** catalog cấp/lớp/môn/yêu cầu cần đạt có nguồn; ba policy Tiểu học, THCS, THPT; hồ sơ STEM có bối cảnh; minh chứng S/T/E/M; gate server; rubric cho Reviewer; snapshot khi render và publish; AI theo ngữ cảnh; thẻ học liệu và lọc thư viện; ba gói demo theo trục **năng lượng trong đời sống**.

**Không làm:** LMS, tài khoản học sinh, chấm bài, mô phỏng tổng quát, đổi stack, viết lại auth/render/workflow, CRUD quản trị danh mục đầy đủ (MVP seed bằng migration, Admin chỉ xem và gán policy).

**Định vị:** STEMotion hỗ trợ giáo viên và nhóm học liệu thiết kế, sản xuất, kiểm duyệt clip phục vụ hoạt động giáo dục STEM theo cấp/lớp và yêu cầu cần đạt của chương trình Việt Nam.

Không dùng nhãn "Bộ GD&ĐT chứng nhận". Chỉ nói đã hỗ trợ đủ ba cấp khi cả ba có seed, rule và test chạy thật.

## 2. Điều kiện tiên quyết (Hoàng + Lê chốt trước khi viết migration)

1. **Auth/roles chưa lên DB dùng chung.** `deploy/auth-v2-drafts` và `deploy/roles-mail-drafts` chưa áp dụng; README của chúng ghi không khởi động source hiện tại trên schema cũ. Hibernate đang `validate`. Mọi thử nghiệm STEM dùng DB cô lập hoặc `E2eServer`.
2. **Giữ số Flyway.** Hai bộ draft chưa có số chính thức. Đề xuất giữ **V23 cho auth, V24 cho roles/mail, STEM bắt đầu từ V25**. Nếu áp dụng V25 trước rồi mới thêm V23, Flyway sẽ báo lỗi thứ tự. Chốt việc này trước tiên.
3. **Kiểu FK.** Không viết cứng `uuid` hay `varchar`. Theo mẫu `V10`, `V12`, `V13`, `V19`: đọc kiểu thật rồi `EXECUTE format(...)`. `users`, `workspaces`, `clip_projects`, `script_versions`, `scenes` là bảng từ V1; `clips`, `compositions`, `publications` dùng `uuid`.
4. **RLS.** Bảng mới ở schema public phải bật RLS, không có policy cho client, thu hồi quyền của `anon`/`authenticated` nếu các role đó tồn tại, giống bản nháp auth. Kiểm tra role của backend truy cập được.
5. **Không dựa vào cột auth mới** trong migration STEM. Chỉ tham chiếu `users(id)`, để test chạy được cùng fixture `V9000`, `V9001`.
6. **Role:** system role chỉ còn `ADMIN`/`USER`; Writer/Reviewer/Producer là role trong workspace.

## 3. Căn cứ giáo dục

Ba lớp căn cứ, không được trộn:
1. **Văn bản tổ chức giáo dục STEM:** xác định bản chất và cách tổ chức.
2. **Chương trình môn theo lớp:** nơi lấy yêu cầu cần đạt. Đây mới là dữ liệu đối chiếu theo lớp.
3. **Quy tắc của STEMotion:** thiết kế sản phẩm, không phải quy định của Bộ.

Không có "mỗi lớp một công văn STEM". THCS và THPT dùng chung hướng dẫn trung học nhưng khác chương trình môn; **mỗi cấp có profile riêng trong hệ thống**. Nên giải thích rõ điều này với reviewer.

| Mã | Văn bản | Dùng cho | Việc còn phải làm |
|---|---|---|---|
| L01–L03 | TT 32/2018, TT 13/2022, TT 17/2025 (CTGDPT và sửa đổi) | Cấp, lớp, môn, yêu cầu cần đạt | Tìm đúng phụ lục môn/lớp, ghi section/trang |
| L04 | CV 909/BGDĐT-GDTH (08/03/2023) | Tiểu học | Đối chiếu bản gốc |
| L05 | CV 3089/BGDĐT-GDTrH (14/08/2020) | THCS, THPT | Đối chiếu hiệu lực |
| L06–L08 | TT 86/2026/TT-BGDĐT, hiệu lực 30/09/2026 | Căn cứ mới nhất cho cả ba cấp | **Chưa đọc bản ký gốc.** Bắt buộc đọc trước khi bật policy |
| L09 | CV 5208/BGDĐT-GDPT (07/08/2026) | Nhiệm vụ năm học 2026–2027 | Lưu theo năm học |

Liên kết nguồn: [02_CAN_CU_VA_RANG_BUOC.md](reference-2026-11-03/02_CAN_CU_VA_RANG_BUOC.md). Không kết luận CV 909/3089 đã hết hiệu lực khi chưa có căn cứ. Chỉ người phụ trách mới đặt trạng thái `VERIFIED`; AI không được tạo hoặc xác nhận nguồn.

## 4. Bối cảnh và người dùng

Đây là phần reviewer nhận xét nặng nhất, phải làm trước và phải thấy được trên sản phẩm.

**Hai nhóm người, không được gộp:**
- **Người vận hành:** giáo viên, nhóm học liệu (Writer, Reviewer chuyên môn, Producer, Admin).
- **Người xem:** học sinh một lớp cụ thể. Không có tài khoản trong hệ thống.

**`usage_context` (bắt buộc, chọn một):**

| Mã | Tình huống | Nhu cầu người xem | Brief phải khai báo | Clip / gói đầu ra phải có |
|---|---|---|---|---|
| `PRE_CLASS` | Trước giờ học, khơi gợi | Nhận ra vấn đề, đưa dự đoán | Kiến thức đã có, câu hỏi thực tiễn | Mở bằng câu hỏi; kết bằng câu hỏi dự đoán |
| `IN_CLASS_SCREEN` | Chiếu chung trong lớp | Nhìn rõ, có thời gian thảo luận | Cách tổ chức nhóm, thời lượng tiết | Ít nhất một điểm dừng thảo luận; thẻ nhiệm vụ |
| `GROUP_PRACTICE` | Thực hành nhóm | Biết đo gì, ghi gì, so sánh ra sao | Vật liệu, biến kiểm soát, rủi ro | Bảng ghi dữ liệu, tiêu chí, lưu ý an toàn |
| `HOME_SELF_STUDY` | Tự xem ở nhà, thiết bị yếu | Ngôn ngữ vừa sức, không cần phòng lab | Điều kiện vật liệu, có người lớn giám sát hay không | Phụ đề/transcript; phương án quan sát thay thế |
| `DEPARTMENT_LIBRARY` | Kho học liệu tổ chuyên môn | Giáo viên biết clip dùng cho đâu | Phạm vi và giới hạn sử dụng | Thẻ học liệu đầy đủ |

Các bối cảnh này hiện là **giả thuyết**. T14a kiểm chứng chúng với ít nhất một giáo viên mỗi cấp **trước khi chốt field**. Ghi lại: đang dùng clip ở bước nào, học sinh có thiết bị gì, lỗi khoa học nào hay gặp, sau khi xem phải làm được gì, tiêu chí nào quyết định dùng clip. Thay đổi `usage_context` theo kết quả; ghi rõ nếu không hỏi được.

**Bối cảnh phải xuất hiện ở:** form tạo dự án, Writer Studio, prompt AI, rubric Reviewer, panel Producer, thẻ học liệu, bộ lọc thư viện, Report 1.

**Mỗi brief trả lời:** Ai học? Đã biết gì? Vấn đề thực tiễn nào? Clip giúp bước nào? Học sinh làm gì tiếp? Làm sao biết đạt? Làm được trong điều kiện nào?

## 5. Ba profile cấp học

Mã nội bộ `PRIMARY_V1`, `LOWER_SECONDARY_V1`, `UPPER_SECONDARY_V1`, chưa phải policy đã kiểm chứng.

| Thuộc tính | Tiểu học | THCS | THPT |
|---|---|---|---|
| Lớp | 1–5 | 6–9 | 10–12 |
| Môn trong catalog | Toán; TN&XH (1–3); Khoa học (4–5); Tin học, Công nghệ theo lớp | KHTN, Toán, Công nghệ, Tin học | Toán, Vật lí, Hóa học, Sinh học, Công nghệ, Tin học |
| Mục tiêu | Quan sát, nêu, so sánh, đo/đếm, lắp ráp có hướng dẫn | Giả thuyết, đo lường, xử lí dữ liệu, thử phương án | Phân tích, mô hình hóa, tối ưu theo ràng buộc |
| Hoạt động tối thiểu | Nhiệm vụ quan sát hoặc mẫu đơn giản + tiêu chí dễ hiểu | Kế hoạch + dữ liệu cần thu + so sánh | Phương án có căn cứ + biến/ràng buộc + phân tích + cải tiến |
| An toàn | Có người hướng dẫn; có phương án không thực hành | Khai báo rủi ro điện/nhiệt/hóa chất | Vẫn đánh giá rủi ro, không mặc định làm được mọi thí nghiệm |
| Lưu ý | Không dùng nhãn "Vật lí" ở tiểu học | Không tách KHTN thành ba môn trong catalog | Có điều kiện môn lựa chọn |

**Trục demo:** tiểu học lớp 4 (lớp bọc giữ đá lâu tan), THCS lớp 8 (vật liệu giữ nhiệt), THPT lớp 11 (đèn LED DC). Đây là lớp dự kiến, xác nhận lại sau khi đọc chương trình. Chi tiết: mục 7 của [tài liệu 02](reference-2026-11-03/02_CAN_CU_VA_RANG_BUOC.md).

**Bốn thành tố:** mỗi thành tố có đóng góp thật và chỉ ra nơi thể hiện. Icon nguyên tử không phải S; "làm bằng AI/Remotion" không phải T; quay sản phẩm đẹp không phải E; một công thức không dùng tới không phải M. Thành tố có thể nằm ở nhiệm vụ ngoài clip nếu ghi rõ phạm vi. Clip chỉ có S/M và quiz ghi nhớ không được gắn nhãn "hoạt động STEM hoàn chỉnh".

## 6. Bộ quy tắc

Đây là quy tắc sản phẩm. `DRAFT` được lưu thiếu để không phá autosave.

| Rule | Nội dung | Gate | Xác minh |
|---|---|---|---|
| BR-ST01 | Lớp 1–12 khớp cấp và policy workspace cho phép | Submit | Server |
| BR-ST02 | Một môn chính hợp lớp, môn phối hợp trong catalog; ngưỡng "ít nhất hai môn" cần nhóm chốt | Submit | Server + Reviewer |
| BR-ST03 | Ít nhất một yêu cầu cần đạt `VERIFIED`, đúng môn/lớp/phiên bản | Submit | Server + Reviewer |
| BR-ST04 | Mục tiêu cụ thể, nối mục tiêu → yêu cầu cần đạt → scene/nhiệm vụ | Submit | Server + Reviewer |
| BR-ST05 | Có `usage_context` và đủ field của bối cảnh đó (mục 4), có vấn đề thực tiễn | Submit | Server + Reviewer |
| BR-ST06 | Có hoạt động vận dụng và tiêu chí/sản phẩm dự kiến | Submit | Server + Reviewer |
| BR-ST07 | Khai báo S/T/E/M với đóng góp và nơi thể hiện | Submit | Server + rubric |
| BR-ST08 | Có đánh giá rủi ro; `NOT_APPLICABLE` phải có lý do | Approve | Reviewer |
| BR-ST09 | Scene đầu có vai trò nêu vấn đề/mục tiêu; scene cuối gắn lại câu hỏi, kết luận, nhiệm vụ tiếp theo | Submit (có vai trò) + Approve (đúng nội dung) | Server + Reviewer |
| BR-ST10 | Số liệu/công thức có nguồn; số minh họa ghi rõ là minh họa | Approve | Reviewer |
| BR-ST11 | AI chỉ gợi ý; kết quả nhận vào đi qua cùng validation với viết tay | Mọi đường ghi | Server |
| BR-ST12 | Quyết định chỉ có hiệu lực với đúng fingerprint đã duyệt | Render, review, publish | Server |
| BR-ST13 | Đổi nội dung khoa học ở composition phải duyệt STEM lại; đổi phần trình bày thì không | Video review | Server + Reviewer |
| BR-ST14 | Publish cần media QA + quyết định STEM đúng artifact; Admin không được bỏ qua | Publish | Server |
| BR-ST15 | Không suy diễn lớp cũ; dự án legacy phải hoàn thiện hồ sơ trước khi xuất bản mới | Rollout | Server |

Đủ field chỉ đạt "đúng cấu trúc". Kết luận phù hợp STEM là việc của Reviewer. Điểm AI không bao giờ thành PASS.

## 7. Mô hình dữ liệu

Bảng mới, tuân theo mục 2. Kiểm tra trùng tên trước khi viết migration.

| Bảng | Trường chính | Ràng buộc |
|---|---|---|
| `grade_levels` | `grade_no` PK, `level_code`, `label` | CHECK: PRIMARY 1–5, LOWER_SECONDARY 6–9, UPPER_SECONDARY 10–12 |
| `school_subjects` | `id`, `code`, `name`, `aliases` | `code` unique |
| `grade_subjects` | `id`, `grade_no`, `subject_id`, `program_version`, `availability` | Unique grade/subject/program |
| `reference_documents` | `id`, `type`, `issuer`, `number`, `title`, `issued_on`, `effective_on`, `source_url`, `file_hash`, `verification_status`, `verified_by`, `verified_at`, `supersedes_id` | Chỉ nguồn đã xác minh mới dùng cho outcome `VERIFIED` |
| `curriculum_outcomes` | `id`, `document_id`, `grade_subject_id`, `local_code`, `text`, `locator`, `verification_status` | Unique document/code; `locator` là section/trang đã kiểm |
| `stem_policy_versions` | `id`, `profile_code`, `version_no`, `level_code`, `rules`, `rubric` (jsonb), `schema_version`, `status`, `content_hash`, `approved_by`, `approved_at` | DRAFT → VERIFIED → ACTIVE → RETIRED; ACTIVE không sửa được |
| `policy_document_refs` | `id`, `policy_version_id`, `document_id`, `locator`, `rationale` | Unique policy/document/locator |
| `workspace_stem_policies` | `id`, `workspace_id`, `policy_version_id`, `is_default`, `enabled_for_new_projects`, **`enforcement_mode`** | Unique workspace/policy; `OFF`/`ADVISORY`/`STRICT` |
| `stem_briefs` | `id`, `script_version_id` (unique), `policy_version_id`, `grade_no`, **`usage_context`**, `context_details` (jsonb), `problem`, `prerequisites`, `objectives`, `activities`, `safety` (jsonb), `coverage_scope`, `revision`, `schema_version` | Sửa được khi draft; bất biến sau submit |
| `stem_brief_subjects` | `id`, `brief_id`, `grade_subject_id`, `role`, `contribution` | Đúng một PRIMARY |
| `stem_brief_outcomes` | `id`, `brief_id`, `outcome_id`, `objective_keys`, `mapping_rationale` | Outcome khớp lớp/môn, `VERIFIED` |
| `stem_scene_evidence` | `id`, `brief_id`, `scene_id` (nullable), `component`, `objective_key`, `activity_key`, `pedagogical_role`, `contribution`, `evidence_kind`, `evidence_payload`, `source_document_id` | `scene_id` null thì phải có `activity_key`; scene đúng script version; FK **RESTRICT** |
| `stem_validation_runs` | `id`, `brief_id`, `target_type`, `script_version_id`, `composition_id`, `composition_revision`, `clip_id`, `fingerprint`, `policy_hash`, `rules_hash`, `report`, `status`, `created_by` | Report bất biến |
| `stem_review_decisions` | `id`, `validation_run_id`, `reviewer_id`, `decision`, `criterion_results`, `reason`, `fingerprint`, `decided_at` | Chỉ thêm, không sửa |

Thêm cột vào bảng cũ:

| Bảng | Cột | Mục đích |
|---|---|---|
| `clip_projects` | `stem_migration_state` (`LEGACY_UNMAPPED`, `MAPPED`, `VALIDATED`) | Theo dõi chuyển đổi |
| `render_jobs` | `stem_snapshot`, `stem_fingerprint`, `stem_review_decision_id` | Đóng băng cùng `inputProps` |
| `clips` | `stem_snapshot`, `stem_fingerprint` | Nguồn gốc của artifact |
| `publications` | `stem_snapshot`, `stem_fingerprint`, `stem_review_decision_id` | Thẻ học liệu bất biến |

Giữ `subject`, `grade_level` cũ làm metadata tương thích; không thêm cấp/lớp vào `clip_projects` làm nguồn chuẩn thứ hai. `pedagogical_role` nằm ở `stem_scene_evidence`; không sửa bảng `scenes` và `SceneType`. Mục tiêu, hoạt động, an toàn là jsonb có schema, mỗi phần tử có key ổn định.

## 8. Invariant, fingerprint, phân loại field

**Invariant:**
1. Một `ScriptVersion` có tối đa một brief. Draft thiếu brief vẫn autosave; submit ở STRICT phải đủ.
2. **Xóa scene trong draft:** service xóa evidence của scene đó cùng transaction, brief về trạng thái chưa đủ; FK RESTRICT bảo vệ version đã submit.
3. **Request changes tạo version mới:** `copyScenes` tạo ID mới và không lưu liên kết, nên phải **sao chép brief và map evidence theo `order_no` trong cùng transaction**. Không sao chép quyết định duyệt.
4. Quan hệ lớp/môn/outcome được kiểm tra ở service; FK đơn lẻ không chứng minh chúng cùng lớp.
5. Không tin dữ liệu client gửi (`isStem`, report PASS, trạng thái VERIFIED). AI response là gợi ý không tin cậy.
6. **Quyết định STEM chỉ do thành viên có ghế REVIEWER đang ACTIVE của workspace ghi.** `WorkspaceAccess.isAdmin` cho Admin qua kiểm tra role, nên service STEM phải kiểm tra ghế riêng. Admin cấu hình policy nhưng không tự duyệt nội dung.
7. Tính lại fingerprint trong transaction trước khi chuyển trạng thái; cũ thì trả 409. Gọi AI ngoài transaction.
8. Composition giữ nguyên số scene, ID, thứ tự, loại (`CompositionService.normalize` đã bắt buộc), nên `scene_id` dùng ổn định cho đến hết version.

**Fingerprint:**
```text
scriptFingerprint      = SHA256(canonicalJSON(brief + subjects/outcomes + evidence + policy hash
                                + scriptVersionId + scene ids/order + semantic fields))
compositionFingerprint = SHA256(scriptFingerprint + semantic composition)
artifactFingerprint    = SHA256(compositionFingerprint + frozen render input hash + renderJobId)
```
Canonical JSON có thứ tự khóa ổn định. Không đưa timestamp, `draftRevision` hay field trình bày vào hash. Hash chỉ chứng minh đúng bản đã duyệt, không chứng minh khoa học đúng.

**Phân loại field (làm trong T03, viết test cho từng `SceneType`):**

| Nhóm | Field | Khi đổi |
|---|---|---|
| Trình bày | `durationInFrames`, `captionWords`, `visualTheme`, font/spacing/vị trí, `motion`, `emphasis`, `at`, `until` | Không đổi fingerprint; media QA vẫn chạy |
| Nội dung | `title`, `narration`, `latex`, `steps`, `dataPoints`, nhãn trục, `equation`, `reactants`, `products`, `options`, `correctIndex`, `answer`, `explanation`, `labels`, `stages`, `topicA/B`, chữ trong `blocks`, `tone` | Cần duyệt STEM lại |
| Chưa phân loại | Mọi field còn lại | Coi là nội dung, và test phải báo để phân loại |

Đủ 13 `SceneType`, kể cả khối lồng trong `COMPOSED`. **Test bắt buộc: "Tự Khớp Giọng Đọc" không làm thay đổi fingerprint.**

**Trạng thái STEM** (suy ra từ report, decision, hash; không sửa lifecycle script/video): `INCOMPLETE → NEEDS_REVIEW → APPROVED | CHANGES_REQUIRED`; `CHANGES_REQUIRED → INCOMPLETE`; `APPROVED → STALE → NEEDS_REVIEW`.

## 9. Điểm tích hợp

| Nơi sửa | Thay đổi |
|---|---|
| `ScriptService.create/updateDraft` | Giữ API; draft có thể chưa có brief; xử lý evidence khi xóa scene; không gọi AI khi autosave |
| `ScriptService.submitForReview` | Sau quyền/status gọi `StemValidationService` theo `enforcement_mode` |
| `ScriptService.approve` | Cần rubric đúng script fingerprint; ghi STEM decision và workflow cùng transaction |
| `ScriptService` request-changes | Sao chép brief, map evidence (mục 8) |
| `CompositionService.update` | Tính semantic hash; báo UI khi cần duyệt lại |
| `RenderService.start`, `renderApproved` | Kiểm tra quyết định, đóng băng snapshot cùng `inputProps` |
| **`FeedbackService.submitForVideoReview`, `approveVideo`, `sendBackToProduction`** | Rubric clip theo composition snapshot. `approveVideo` có **hai nhánh**: đã có clip, và chưa có clip nên gọi `renderApproved`. Cả hai phải có gate |
| `ClipQaService` | Giữ media QA; báo cáo STEM đặt cạnh, không trộn điểm |
| `PublishService.publish` | Đối chiếu fingerprint, decision, media QA trong transaction |
| `LibraryService`, `/library/clips`, `/library/filters` | Trả thẻ từ snapshot; lọc server-side, có phân trang |
| AI (`AiScriptService`, `AiGateway`; Python `prompts`) | `stemContext` do server giải từ brief; sửa cả nhánh Java và Python |
| Worker | Metadata tùy chọn, không kiểm tra sư phạm |

## 10. API

Prefix `/api/v1`, dùng error envelope hiện có.

| API | Quyền |
|---|---|
| `GET /stem/catalog?grade=&programVersion=` | Thành viên workspace |
| `GET /workspaces/{id}/stem-policies` | Thành viên workspace |
| `PUT /workspaces/{id}/stem-policies` | Admin |
| `GET /scripts/{id}/stem-brief?scriptVersionId=` | Người xem được project |
| `PUT /scripts/{id}/stem-brief` | Writer trên draft, kèm `baseRevision`, `scriptVersionId` |
| `POST /scripts/{id}/stem-validation` | Server tạo report, theo target |
| `GET /scripts/{id}/stem-validation` | Trả current hay stale |
| `POST /scripts/{id}/approve` (mở rộng) | Thêm `fingerprint`, `validationRunId`, `criterionResults` |
| `POST /scripts/{id}/approve-video`, `/send-back` (mở rộng) | Tương tự, target composition/clip |
| `/ai/script/*` (mở rộng) | Nhận `briefId`, không nhận rule từ client |
| `GET /library/clips` (mở rộng) | `educationLevel`, `grade`, `subjectId`, `outcomeId`, `usageContext`, `page`, `size` |

Không tạo luồng approve thứ hai. Lỗi:
- **400:** `STEM_BRIEF_INCOMPLETE`, `STEM_GRADE_SUBJECT_MISMATCH`, `STEM_OUTCOME_UNVERIFIED`, `STEM_EVIDENCE_MISSING`, `STEM_CONTEXT_INCOMPLETE`, `STEM_POLICY_NOT_ALLOWED`
- **409:** `STEM_REVIEW_STALE`, `STEM_POLICY_REVALIDATION_REQUIRED`, `STALE_REVISION`
- **403:** thiếu quyền

Chi tiết lỗi đặt trong `details.findings[]`: `ruleId`, `fieldPath`, `expected`, `actual`, `suggestion`. FE đã đọc `details` qua `apiClient`.

## 11. Giao diện và AI

- **Tạo dự án:** cấp → lớp → môn chính/phối hợp → yêu cầu cần đạt → **bối cảnh sử dụng** → vấn đề thực tiễn. Bỏ "Đại chúng/Cơ bản/Nâng cao/EdTech" khỏi vai trò cấp lớp (`CreateProjectModal`).
- **Writer:** panel "Hồ sơ STEM"; mỗi scene có vai trò sư phạm và liên kết mục tiêu/nhiệm vụ; hiện thiếu gì và vì sao, không chấm phần trăm.
- **Reviewer:** hai vùng nội dung/chương trình và video/media; rubric có evidence trỏ đúng cảnh, link nguồn, dấu stale.
- **Producer:** thấy mục tiêu, bối cảnh, evidence; panel báo khi sửa nội dung khoa học.
- **Publish/Library:** thẻ "dùng cho lớp nào, trong bối cảnh nào, mục tiêu gì, học sinh làm gì tiếp, nguồn, ai duyệt". Clip cũ hiện "Chưa đối chiếu hồ sơ STEM".
- **Dashboard và trang chủ:** hiện cấp/lớp, bối cảnh và trạng thái STEM trên thẻ dự án, để người xem thấy đây là công cụ STEM, không chỉ quản lý clip.
- **AI:** prompt dựng từ catalog đã kiểm và `usage_context`; có ngôn ngữ riêng cho tiểu học; gợi ý có `objectiveKey`/`activityKey`; không tự tạo nguồn. Accept chỉ tạo dữ liệu draft.

## 12. Migration và rollout

1. Chốt mục 2, kiểm kê schema trên DB cô lập.
2. Migration thêm bảng và cột nullable; không sửa migration cũ.
3. Seed cấp/môn và ba policy `DRAFT`; chỉ seed outcome sau khi xác minh nguồn.
4. Đánh dấu dự án cũ `LEGACY_UNMAPPED`. `Đại chúng`, `Cơ bản`, `Nâng cao`, `EdTech`, null không suy ra lớp.
5. Mặc định `OFF`. Bật `ADVISORY` trên workspace demo, so kết quả với Reviewer.
6. Bật `STRICT` cho workspace mới khi ba bộ seed và test đạt. Dự án cũ vẫn xem/tải được.
7. Thư viện dùng snapshot khi có, nếu không thì hiện nhãn legacy.

Rollback: chuyển về `OFF`, giữ dữ liệu, không drop bảng. Mọi bước trên DB dùng chung đều cần cả nhóm duyệt.

## 13. Backlog

Ước lượng là ngày công tập trung, chưa tính chờ chuyên môn. Người phụ trách là đề xuất, nhóm xác nhận.

### Giai đoạn 0: Chốt nền

| Task | Việc | Người | Công | Xong khi |
|---|---|---|---|---|
| T00 | Chốt mục 2; tạo nhánh; thống nhất số Flyway | Hoàng, Lê | 0,5 | Có quyết định bằng văn bản |
| T01 | Định vị, persona, `usage_context`, ba brief mẫu; sửa problem/vision Report 1 | Trung | 1–2 | Phân biệt người vận hành/người xem; ba brief có đủ câu trả lời mục 4 |
| T14a | Hỏi ít nhất một giáo viên mỗi cấp | Trung + cả nhóm | 1–2 + chờ | Có ghi chép; `usage_context` được giữ hoặc sửa có lý do |
| T02 | Đọc bản ký TT86, phụ lục môn/lớp; bảng nguồn và outcome cho ba demo | Lê | 2–4 | Có locator, hash, người xác minh; điểm chưa rõ được ghi |
| T03 | Chốt schema, JSON schema, phân loại field, API, lỗi | Hoàng, Trì, Trung | 1–2 | UC → model → API → rule → test nối được |
| T04 | Ba policy, seed lớp 4/8/11; một bộ hợp lệ, một bộ lỗi mỗi cấp | Lê, Hoàng | 2–3 | Có đủ 6 bộ; DRAFT/VERIFIED/ACTIVE phân biệt |

### Giai đoạn 1: Lát cắt chạy được (mốc demo tối thiểu)

| Task | Việc | Người | Công | Xong khi |
|---|---|---|---|---|
| T05 | Migration, entity, repository, CRUD brief có revision, xử lý xóa scene và copy version | Hoàng | 3–5 | Invariant 1–4 có test; không mất dữ liệu cũ |
| T06 | `StemValidationService`, rule, gate submit/approve, `enforcement_mode` | Hoàng | 2–4 | Thủ công và AI cùng rule; API trực tiếp không lọt |
| T07 | FE: cấp/lớp/môn/outcome/bối cảnh, panel brief, evidence, lỗi theo field | Trì | 3–5 | Lưu nháp được; lỗi chỉ đúng field; autosave không vỡ |
| T08 | Rubric Reviewer, quyết định, kiểm tra ghế REVIEWER | Trì, Hoàng | 2–3 | Tích hợp giả vẫn bị trả về; không có nút bỏ qua |
| T13a | Cập nhật fixture E2E, thêm `wf05-stem.spec.ts` | Trung | 2–3 | Bộ cũ xanh ở `OFF`; spec mới chạy `STRICT` trên CI |

### Giai đoạn 2: Bảo vệ nội dung đến khi xuất bản

| Task | Việc | Người | Công | Xong khi |
|---|---|---|---|---|
| T09 | Semantic hash, snapshot ở render/clip/publication, gate `FeedbackService` (2 nhánh) và `PublishService` | Hoàng, Lê | 4–6 | Sửa công thức làm stale; khớp giọng đọc thì không; sai artifact không publish được |

### Giai đoạn 3: AI, thư viện, demo

| Task | Việc | Người | Công | Xong khi |
|---|---|---|---|---|
| T10 | AI theo brief, profile tiểu học, Java + Python | Lê | 2–4 | Không sinh nguồn VERIFIED; AI lỗi vẫn viết tay được |
| T11 | Thẻ học liệu, lọc thư viện, dashboard, legacy | Trì | 2–3 | Lọc cấp/lớp/môn/outcome/bối cảnh có phân trang |
| T13b | Acceptance ba cấp, regression, rehearsal migration | Trung | 3–5 | AT01–AT24 có kết quả trên commit ghi rõ |
| T14b | Ba gói demo, chuyên môn xem thử, chỉnh | Cả nhóm | 1–2 + chờ | Có nhận xét thật, không nói là đã đo hiệu quả học tập |

### Tài liệu (chạy song song)

| Task | Việc | Người | Công |
|---|---|---|---|
| T12a | Bản thiết kế: sơ đồ và Report ở mức TO-BE; đồng bộ BM (mục 15) | Trung, Hoàng | 2–3 |
| T12b | Cập nhật theo code thật; bảng Designed/Implemented/Tested/Deployed | Cả nhóm | 1–2 |

**Tổng thô 35–59 ngày công.** Không chia cho 4 người để hứa hạn, vì nhiều việc phụ thuộc nhau. Nếu thiếu thời gian: hoàn thành Giai đoạn 0, 1 và T09 cho **một cấp**, hai cấp còn lại giữ ở mức thiết kế và nói rõ như vậy với reviewer.

```text
T00 → T03 → T05 → T06 → T08 → T09 → T11
T01 → T14a ↗       T07 ↗
T02 → T04 ─────────┘
```

## 14. Kiểm thử

**Ca nghiệm thu (chưa chạy):**

| Mã | Tình huống | Kỳ vọng |
|---|---|---|
| AT01 | PRIMARY + lớp 8, hoặc lớp 4 + Vật lí | Server từ chối, chỉ đúng field |
| AT02 | Grade cũ `Đại chúng`/null | Giữ legacy, xem được, publish mới đòi mapping |
| AT03 | Script thủ công không có brief | Lưu draft được, submit bị từ chối ở STRICT |
| AT04 | AI trả outcome giả | Không submit được |
| AT05 | Đủ bốn chữ nhưng T là "dùng Remotion", E là "thêm icon" | Cấu trúc qua, Reviewer trả về có lý do |
| AT06 | Brief trỏ scene/outcome của dự án khác | Từ chối, không lộ dữ liệu |
| AT07 | Hoạt động nằm ở thẻ ngoài clip | Xét theo `coverage_scope` |
| AT08 | Sửa thẳng brief đã submit | Từ chối; version mới không kế thừa quyết định |
| AT09 | Reviewer đang xem bản N, bản đổi thành N+1 | 409, giữ ghi chú |
| AT10 | Producer đổi đáp án, công thức, số liệu, narration | STALE, chặn publish |
| AT11 | Đổi style trong danh sách trình bày | Script review còn hiệu lực |
| AT12 | Callback job cũ tới muộn | Không gắn snapshot cũ |
| AT13 | Đổi policy mặc định workspace | Không viết lại hồ sơ đã duyệt |
| AT14 | Admin gọi thẳng publish, giả report PASS | Không qua được |
| AT15 | Sửa title/grade sau khi đã công bố | Thẻ cũ giữ snapshot |
| AT16 | Mỗi cấp một hồ sơ đúng và một hồ sơ thiếu | Đúng thì hoàn tất; thiếu thì dừng đúng gate |
| AT17 | AI timeout | Vẫn làm tay được |
| AT18 | Migrate dữ liệu có grade số, nhãn, null; rollback | Không mất dữ liệu, không gắn nhãn sai |
| AT19 | Lọc thư viện kết hợp phân trang | Tổng/trang đúng |
| AT20 | Thiếu an toàn; số minh họa ghi như số đo | Bị từ chối |
| AT21 | Bấm "Tự Khớp Giọng Đọc" sau khi đã duyệt | Fingerprint không đổi |
| AT22 | `HOME_SELF_STUDY` thiếu phương án thay thế; `IN_CLASS_SCREEN` thiếu điểm dừng | `STEM_CONTEXT_INCOMPLETE` |
| AT23 | Xóa scene có evidence; request changes rồi mở version mới | Evidence xử lý đúng, map sang scene ID mới |
| AT24 | Admin không có ghế REVIEWER gọi approve với rubric | Không ghi được quyết định STEM |

**Tầng test:** unit cho rule, fingerprint và phân loại từng `SceneType`; Postgres cho FK, transaction, migration, race approve; contract cho Java/Python/FE; E2E Playwright.

**E2E hiện có:** `e2e/support/api.ts` tạo dự án với `gradeLevel: 'Cơ bản'` và không có brief. Giữ workspace ở `OFF` cho các spec cũ. Thêm helper `seedStemBrief`, mở rộng `E2eFakes` (AI theo `stemContext`), và spec mới bật `STRICT`.

## 15. Đồng bộ tài liệu

**Đánh số (đã đưa vào Report 3 ngày 09/11/2026):** UC-43…67 theo sơ đồ use case 61 ô; FR-56…61 cho auth v2, FR-62…73 cho STEM; `BR-ST01…15` là nhóm riêng.

| FR | UC | Nội dung |
|---|---|---|
| FR-62 | UC-62, UC-63 | Quản lý phiên bản policy STEM và gán cho workspace |
| FR-63 | UC-46 | Chọn lớp, môn và yêu cầu cần đạt |
| FR-64 | UC-43 | Soạn hồ sơ STEM và bối cảnh sử dụng |
| FR-65 | UC-47 | Gắn minh chứng S/T/E/M với scene/nhiệm vụ |
| FR-66 | UC-44 | Nhận AI gợi ý theo chương trình |
| FR-67 | UC-48 | Kiểm tra điều kiện nộp script |
| FR-68 | UC-52 | Thẩm định nội dung STEM theo rubric |
| FR-69 | UC-49 | Bảo toàn nội dung khi dựng (fingerprint) |
| FR-70 | UC-56 | Kiểm tra clip theo mục tiêu học |
| FR-71 | UC-51 | Công bố thẻ học liệu STEM |
| FR-72 | UC-67 | Tìm học liệu theo cấp/lớp/outcome/bối cảnh |
| FR-73 | UC-45 | Chuyển đổi dự án legacy |

| Tài liệu | Việc |
|---|---|
| Use case | **Gộp vào diagram của nhóm** (`docs/diagrams/source/STEMotion_UseCase.drawio`). Giữ actor Reviewer; giữ Producer và Admin publish. Nguồn tham khảo `reference-2026-11-03/diagrams/stem-usecases.puml` |
| Conceptual ERD, ERD (`05-erd.mmd`) | Thêm entity mục 7. Không đưa actor vào entity |
| State, activity, sequence | Thêm state STEM và policy; sửa activity WF-02, WF-03, WF-04; thêm sequence submit/approve/publish |
| Report 1 | Người vận hành/người xem, bối cảnh, phạm vi ba cấp, demo |
| Report 2 | WBS, rủi ro, phân công |
| Report 3 | Đã cập nhật 09/11/2026: FR-62…73, BR-ST, UC-43…67, messages; **đồng bộ FR-01…05, UC-01, UC-24 theo auth v2** (`docs/AUTH_V2_SRS_SDD_CHANGESET_2026-11-02.md`) |
| Report 4 | Package `domain/stem`, ERD, class, sequence, state, snapshot; đồng bộ auth v2 |
| Phiếu v2, BM03 | Đổi "topic" thành "yêu cầu cần đạt và bối cảnh sử dụng" |
| BM01, BM04 | Tách F-14 thành 4 feature; đổi tên bảng ở G-09; sửa F-01 và G-06 theo auth v2 (username, whitelist); cập nhật hạn theo mục 13 |

**Tách feature và số test (tính lại khi chốt):**
- F-14 Khung STEM và policy workspace
- F-15 Hồ sơ STEM, bối cảnh, minh chứng
- F-16 Gate và thẩm định STEM
- F-17 Thẻ học liệu và thư viện theo chương trình

Khi đó: 17 feature × 3 = 51; 4 workflow; 22 + 9 transition mới = 31; 5 NFR → **khoảng 91 test**, tự động ít nhất 46.

"Mọi use case có STEM" hiểu là truy vết được về mục tiêu STEM; login, render progress không cần hành vi sư phạm riêng. **Nên xác nhận cách hiểu này với reviewer.**

## 16. Cần nhóm chốt

| # | Câu hỏi | Đề xuất | Hạn chốt |
|---|---|---|---|
| 1 | Thứ tự auth cutover và số Flyway | V23 auth, V24 roles, V25+ STEM | T00 |
| 2 | Làm đủ ba cấp hay một cấp trước | Schema ba cấp; demo chạy thật theo thời gian còn lại | T01 |
| 3 | Trục demo năng lượng | Đồng ý | T01 |
| 4 | Ngưỡng "ít nhất hai môn" | Thử với chuyên môn rồi chốt | T04 |
| 5 | Ai xác minh chuyên môn từng cấp | Cần tên người thật | T02 |
| 6 | Hạn nộp hồ sơ Review 2 và Defense 1.1 | Dùng để cắt phạm vi | Ngay |
| 7 | Reviewer chấp nhận cách hiểu "mọi use case" và "không có công văn cho từng lớp" | Hỏi sớm | Ngay |

## 17. Bằng chứng cho lần review tiếp theo

1. Giải thích ngắn: người xem là ai, trong bối cảnh nào, STEMotion giúp ở đâu.
2. Ma trận nguồn → cấp/lớp/môn/outcome → rule → field → UC/API → test.
3. Conceptual và DB có policy, outcome, brief, evidence, review.
4. Demo: dự án thiếu hồ sơ bị chặn; dự án đủ được duyệt; sửa số liệu thì STALE; khớp giọng đọc thì không.
5. Ba thẻ học liệu ba cấp, rõ phần nào ở MP4, phần nào ở nhiệm vụ ngoài clip.
6. Bảng Designed / Implemented / Tested / Deployed, có link bằng chứng. Cột Accepted do reviewer quyết định.
