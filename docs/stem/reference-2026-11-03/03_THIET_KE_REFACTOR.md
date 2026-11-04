# Thiết kế refactor tăng cường nghiệp vụ STEM

Trạng thái: đề xuất để triển khai theo từng gói. Không có migration hoặc endpoint dưới đây đang được áp dụng bởi bộ tài liệu này.

## 1 Ranh giới kiến trúc

Giữ nguyên FE, core API Spring Boot, AI service và render worker. Thêm package `domain/stem` trong BE, không tạo microservice curriculum mới. BE là nơi quyết định quyền, dữ liệu nào có nguồn hợp lệ, khi nào được submit/approve/render/publish. AI gợi ý nội dung; worker nhận snapshot để render, không xác nhận bài học đạt STEM.

```text
Workspace policies + grade/subject/outcome catalogs
                 ↓
ClipProject → ScriptVersion + StemBrief + SceneEvidence
                 ↓ validate + human review
Approved script → Composition → STEM clip review + existing media QA
                 ↓ freeze input + brief + policy + decisions
RenderJob → Clip → Publication (snapshot học liệu có nguồn)
```

`ClipProject` vẫn là dự án sản xuất clip. `StemBrief` là hợp đồng nội dung của một `ScriptVersion`. `LearningActivity` là nhiệm vụ của học sinh được mô tả trong brief, không phải một project/task sản xuất. Phải phân biệt ba ý nghĩa này trong UI, conceptual và SRS.

## 2 Mô hình vật lí đề xuất

Các bảng mới có id/audit timestamps theo quy ước repository. Dùng kiểu PK/FK **đúng với schema thực tại lúc migration**; không suy kiểu từ tài liệu cũ. Tên bảng dưới đây là thiết kế; cần kiểm tra collision và số Flyway mới nhất lúc viết migration, không mặc định V23 còn trống.

| Bảng / entity | Trường và quan hệ trọng yếu | Ràng buộc |
|---|---|---|
| `grade_levels` / GradeLevel | `grade_no` PK, `level_code`, label | grade 1–12; CHECK level đúng khoảng PRIMARY 1–5, LOWER_SECONDARY 6–9, UPPER_SECONDARY 10–12 |
| `school_subjects` / SchoolSubject | id, code, name, aliases | code unique; có Toán/TN&XH/Khoa học/KHTN/Công nghệ/Tin học và các môn THPT; không dùng Engineering như tên môn bắt buộc |
| `grade_subjects` / GradeSubject | id, grade_no FK, subject_id FK, program_version, availability | unique theo grade/subject/program; biểu diễn lựa chọn/điều kiện khi cần |
| `reference_documents` / ReferenceDocument | id, type, issuer, number, title, issued/effective dates, source_url, file_hash, verification_status, verified_by/at, supersedes_id FK | Nguồn curriculum/guidance/science/dataset phân loại rõ; chỉ nguồn đã xác minh dùng cho outcome VERIFIED; không bắt buộc có ngày hiệu lực với bài báo khoa học |
| `curriculum_outcomes` / CurriculumOutcome | id, document_id FK, grade_subject_id FK, local_code, text, locator, verification_status | unique document/code; locator là section/page đã kiểm; mã nội bộ không giả làm mã Bộ |
| `stem_policy_versions` / StemPolicyVersion | id, profile_code, version_no, level_code, rules JSONB, rubric JSONB, schema_version, status, content_hash, approved_by/at | unique profile/version; DRAFT → VERIFIED → ACTIVE → RETIRED; nội dung không đổi khi ACTIVE, đổi rule tạo version mới |
| `policy_document_refs` / PolicyDocumentRef | id, policy_version_id FK, document_id FK, locator, rationale | unique policy/document/locator; ghi rule nào tham khảo nguồn nào; không diễn giải field sản phẩm thành điều khoản Bộ |
| `workspace_stem_policies` / WorkspaceStemPolicy | id, workspace_id FK, policy_version_id FK, is_default, enabled_for_new_projects | unique workspace/policy; tối đa một default cho mỗi cấp trong workspace, kiểm tra service/DB phù hợp |
| `stem_briefs` / StemBrief | id, script_version_id FK unique, policy_version_id FK, grade_no FK, context/problem/prerequisites, objectives JSONB, activities JSONB, safety JSONB, coverage_scope, source_snapshot JSONB, revision, schema_version | Draft chỉnh được; submitted/approved phải bất biến; các field JSON theo schema version, không phải JSON tự do |
| `stem_brief_subjects` / StemBriefSubject | id, brief_id FK, grade_subject_id FK, role PRIMARY/SUPPORTING, contribution | unique brief/grade_subject; đúng một PRIMARY; tất cả môn cùng lớp/program với brief, liên môn phải giải thích đóng góp |
| `stem_brief_outcomes` / StemBriefOutcome | id, brief_id FK, outcome_id FK, objective_keys JSONB, mapping_rationale | unique brief/outcome; objective keys tồn tại; outcome khớp lớp/môn đã chọn, VERIFIED tại thời điểm đóng băng |
| `stem_scene_evidence` / StemSceneEvidence | id, brief_id FK, scene_id FK nullable, component S/T/E/M, objective_key, activity_key nullable, pedagogical_role, contribution, evidence_kind, evidence_payload JSONB, source_document_id FK nullable | Nếu scene null phải có activity_key hợp lệ; scene phải thuộc đúng script version; không trỏ scene của project khác; CHECK cần contribution thật và có nơi chỉ minh chứng |
| `stem_validation_runs` / StemValidationRun | id, brief_id FK, target_type SCRIPT/COMPOSITION/CLIP, script_version_id FK, composition_id FK nullable, composition_revision nullable, clip_id FK nullable, fingerprint, policy_hash, rules_hash, report JSONB, status, created_by | Report bất biến, schema version; lưu ruleId, severity, path, expected/actual, source ref; index brief/target/fingerprint/created_at |
| `stem_review_decisions` / StemReviewDecision | id, validation_run_id FK, reviewer_id FK, decision, criterion_results JSONB, reason, fingerprint, decided_at | Chỉ append; policy/rubric/hash phải trùng validation; không reuse SCRIPT decision để coi CLIP tự được duyệt |

Đây là **14 bảng đề xuất**, bao gồm catalog và junction. Có thể nhập catalog và policy bằng migration/seed trước khi xây admin CRUD. Nếu team cần giảm bảng, có thể gộp catalog tĩnh theo lựa chọn có tài liệu; không gộp bỏ versioning, FK outcome và review evidence vì đó là bằng chứng cốt lõi.

Mục tiêu/hoạt động/safety là value object có schema trong StemBrief ở MVP: mỗi object có key ổn định và được version cùng brief; không thêm bảng chỉ để tăng số entity. Conceptual vẫn biểu diễn chúng vì chúng có ý nghĩa nghiệp vụ. Tách bảng khi cần tìm kiếm/phân quyền/đời sống độc lập đã được chứng minh.

### Những cột bổ sung vào bảng cũ

| Bảng | Bổ sung | Mục đích |
|---|---|---|
| `clip_projects` | `stem_migration_state` (`LEGACY_UNMAPPED`, `MAPPED`, `VALIDATED`) | Theo dõi chuyển đổi; không thay script_status/video_status |
| `render_jobs` | `stem_snapshot` JSONB, `stem_fingerprint`, `stem_review_decision_id` FK nullable | Bằng chứng đồng bộ với inputProps/compositionRevision hiện có |
| `clips` | `stem_snapshot` JSONB, `stem_fingerprint` | Đầu ra tự giữ nguồn gốc học liệu của render tương ứng |
| `publications` | `stem_snapshot` JSONB, `stem_fingerprint`, `stem_review_decision_id` FK nullable | Thông tin công bố bất biến theo artifact, không bị title/grade hiện tại ghi đè |

Không thêm level/grade duplicate vào project làm nguồn chuẩn thứ hai. DTO có thể expose cấp/lớp từ brief hiện tại; `subject` và `grade_level` cũ giữ làm compatibility metadata trong giai đoạn chuyển đổi. Nhãn legacy không được dùng thay grade chuẩn.

`stem_snapshot` chứa schema version, policy/outcome IDs và bản text/hash tại thời điểm duyệt, mục tiêu, hoạt động, evidence, nguồn, quyết định và actor/time. Cần cả FK để truy vấn và snapshot để tái hiện khi catalog nguồn được cập nhật. Cấm hard-delete dữ liệu được snapshot tham chiếu; dùng RETIRED/soft delete và giữ lịch sử.

## 3 Invariant và cơ chế chống lách gate

1. Một ScriptVersion có tối đa một StemBrief. Draft thiếu brief vẫn được autosave; submit bắt buộc brief hoàn chỉnh. Khi request changes tạo version mới, copy brief/evidence sang scene IDs mới theo mapping, không copy approval.
2. Grade/subject/outcome đúng quan hệ phải được kiểm tra server. FK đơn lẻ không chứng minh chúng thuộc cùng lớp; các invariant liên bảng cần service transactional, composite constraints khi phù hợp và integration tests.
3. Workspace chỉ cho project dùng profile nằm trong allow-list. Workspace nhiều cấp không ép mọi project dùng một profile. Đổi workspace policy không viết đè brief đã duyệt.
4. Không tin client gửi `isStem=true`, report PASS, rule results hoặc source VERIFIED. Server tự đọc catalog, tính fingerprint và tạo report. AI response là untrusted suggestion.
5. Nội dung đủ trường chỉ có thể đạt STRUCTURALLY_VALID; quyết định phù hợp STEM cần reviewer. Reviewer ghi PASS/CHANGES_REQUIRED theo từng tiêu chí, chỉ ra evidence, không chỉ note chung “OK”.
6. Render phải dùng script approval đang hợp lệ và frozen payload; clip review/publish phải có review tương ứng với composition/render snapshot. Producer đổi lời thoại/công thức không được hưởng approval khoa học của bản cũ.
7. Fingerprint được tính lại server trong transaction trước chuyển trạng thái. Request stale trả 409, không đổi status hay tạo publication. Tác vụ AI dài chạy ngoài transaction, khi ghi kết quả phải kiểm tra revision/hash nguồn.
8. Quyền truy cập hiện tại vẫn áp dụng; validation bổ sung không thay RBAC. Admin có thể cấu hình policy nhưng không được bỏ qua invariant nội dung. Người duyệt phải có quyền nội dung tương ứng; phân công năng lực chuyên môn ở MVP có thể là quy trình workspace, không cần xây chứng chỉ nghề.

## 4 Fingerprint và thay đổi nội dung

```text
scriptFingerprint = SHA256(canonicalJSON(
  brief schema + brief data + subjects/outcomes/source snapshots + evidence
  + policy/rubric version hashes
  + scriptVersionId/draftRevision + sceneIds/order
  + narration + titles + scientific/assessment props
))

compositionFingerprint = SHA256(scriptFingerprint + canonical semantic composition)
artifactFingerprint = SHA256(compositionFingerprint + frozen render input hash + renderJobId)
```

Canonical JSON có thứ tự khóa ổn định, chuẩn đơn vị/Unicode và phân biệt thiếu/null theo schema. Không lấy Java `toString()` hay timestamp cập nhật toàn record làm hash nội dung. Hash là chứng minh bản được duyệt khớp, **không chứng minh khoa học đúng**.

Phân loại field theo schema scene:

- Semantic: narration, số liệu, nhãn trục, đơn vị, công thức, chú thích, đáp án/giải thích, thứ tự thao tác và màu mang nghĩa khoa học (ví dụ legend biểu đồ). Thay đổi → review khoa học lại.
- Presentation thuần túy: căn lề/spacing/style đã được whitelist và không mã hóa ý nghĩa; đổi → media QA/render freshness lại, không tự bỏ script approval.
- Field chưa phân loại: coi là semantic cho đến khi có quyết định rõ. Không dùng quy tắc “mọi màu đều không quan trọng”.

Giữ lifecycle script/video hiện có. Thêm trạng thái kiểm tra STEM độc lập: INCOMPLETE, NEEDS_REVIEW, APPROVED, CHANGES_REQUIRED, STALE. Sau mỗi lần sửa draft, kết quả cũ trở thành stale qua fingerprint, không cần sửa lại lịch sử bất biến.

Reviewer cần kiểm tra khả năng đọc, hình, lời đọc và việc không làm sai ý nghĩa ở đối tượng review được chọn. **Giữ đường web-first:** reviewer có thể duyệt composition snapshot trước render; sau đó BE liên kết decision với artifact chỉ khi job chứng minh đã render đúng snapshot và media QA đạt. Không tự bắt thêm một vòng người duyệt sau render trong mọi trường hợp. Policy có thể yêu cầu xem MP4 cuối khi nội dung/rủi ro cần thiết; nếu đã chọn review CLIP thì decision gắn clipId cụ thể. Voice mới hoặc render có thay đổi có nghĩa không tự hưởng review artifact cũ. Decision SCRIPT không thay thế review nội dung production ở cả hai đường.

## 5 Điểm tích hợp với service hiện có

| Service | Thay đổi cụ thể |
|---|---|
| `ScriptService.create/updateDraft` | Giữ API cũ; draft có thể chưa có brief. Update metadata/brief qua revision thống nhất, invalidate derived validation; không gọi AI mỗi autosave |
| `ScriptService.submitForReview` | Sau quyền/status/revision, gọi `StemValidationService.requireReadyForSubmission`; lỗi trả rule/path để Writer sửa |
| `ScriptService.approve` | Yêu cầu quyết định rubric của đúng script fingerprint; ghi STEM decision và workflow decision cùng transaction |
| `CompositionService.update` | Giữ normalize scene ID/order/type; tính semantic hash mới; UI hiện trạng thái STEM review cần cập nhật khi thay nội dung |
| `RenderService.start` | Kiểm tra brief/script review, đóng băng STEM snapshot cùng inputProps. Cho render preview nếu sản phẩm muốn nhưng không gắn nhãn đã duyệt; MVP dùng gate nghiêm trên render chính thức |
| `ScriptService` video review hiện có | Kết hợp rubric clip với composition snapshot; giữ web-first review và render tự chạy nếu đã có trong workflow |
| `ClipQaService` | Giữ checks kỹ thuật; STEM report được tổng hợp cạnh media QA, không trộn điểm AI vào PASS/FAIL media |
| `PublishService.publish` | Tính/đối chiếu artifact fingerprint, STEM decision và media QA trước ghi publication; cùng transaction không dùng check từ FE |
| `LibraryService` | Trả thẻ học liệu từ publication snapshot; query cấp/lớp/môn/outcome server-side, có paging/index |
| Worker `types/renderer` | Metadata mới optional trong rollout; render chỉ field cần hiển thị, callback vẫn theo jobId. Không gọi curriculum API trong từng frame |

## 6 API và lỗi đề xuất

Prefix `/api/v1`. Những route dưới đây là mới hoặc mở rộng dự kiến; không phải mô tả API hiện đang tồn tại.

| API | Quyền/ý nghĩa |
|---|---|
| `GET /stem/catalog?grade=8&programVersion=...` | User có quyền workspace; trả môn/outcome VERIFIED và nguồn cho lớp |
| `GET /workspaces/{id}/stem-policies` | Đọc profile được phép, default và phiên bản |
| `PUT /workspaces/{id}/stem-policies` | Admin theo quyền hiện tại; đề xuất Leader có thể đề nghị, chưa tự cấp quyền quản trị mới |
| `GET /scripts/{id}/stem-brief` | Đọc brief của version chỉ định bằng query `scriptVersionId` |
| `PUT /scripts/{id}/stem-brief` | Writer có quyền trên draft; `baseRevision`, `scriptVersionId`, `baseDraftRevision`; lưu brief/evidence atomically |
| `POST /scripts/{id}/stem-validation` | Tạo validation theo target SCRIPT/COMPOSITION; chỉ server tạo report; target phải thuộc project |
| `GET /scripts/{id}/stem-validation` | Lấy report theo version/hash, trả rõ current hay stale |
| `POST /scripts/{id}/stem-review` | Reviewer, payload gồm target/fingerprint/validationRunId/criterionResults/decision/reason |
| Mở rộng AI generate/approaches/storyboard hiện có | `stemContext` server-resolved từ briefId/version/hash; không tin rule/outcome text do client giả mạo |
| Mở rộng library hiện có | `educationLevel`, `grade`, `subjectId`, `outcomeId`, `activityType`, `topic`, page/size; tương thích query subject cũ |

Ưu tiên dùng lại endpoint approve/submit hiện tại để thực hiện workflow. `stem-review` ghi nhận chuyên môn; approve hiện tại kiểm tra và liên kết decision, không tạo luồng approve thứ hai mâu thuẫn. Một UI action có thể gửi một request approve mở rộng với rubric và để BE tạo cả hai record trong transaction.

Lỗi: 422 `STEM_BRIEF_INCOMPLETE`, `STEM_GRADE_SUBJECT_MISMATCH`, `STEM_OUTCOME_UNVERIFIED`, `STEM_EVIDENCE_MISSING`, `STEM_POLICY_NOT_ALLOWED`; 409 `STEM_REVIEW_STALE`, `STEM_POLICY_REVALIDATION_REQUIRED`, `STALE_REVISION`; 403 cho thiếu quyền. Response có `ruleId`, `fieldPath`, `expected`, `actual`, `suggestion`; không lộ dữ liệu project khác qua lỗi.

## 7 Thay đổi UX và AI

**Create:** chọn cấp → lớp → môn chính/môn phối hợp → chương trình/outcome → vấn đề thực tiễn. Có “Lưu bản nháp” khi chưa đủ brief. Dời “Đại chúng/Cơ bản/Nâng cao/EdTech” sang audience/difficulty/format nếu còn nhu cầu, không map thành grade.

**Writer:** panel “Hồ sơ STEM” gồm mục tiêu, kiến thức trước, hoạt động học sinh, S/T/E/M, nguồn và an toàn. Scene editor có pedagogicalRole và liên kết objective/task. Hiển thị thiếu gì và vì sao, không chỉ điểm phần trăm đạt STEM. Vẫn dùng các template/formula/chart hiện có.

**Reviewer:** xem hai vùng: nội dung/chương trình và video/media; rubric có evidence đang trỏ đúng cảnh, link nguồn, dấu stale. Có thể trả về vì mối liên hệ môn học giả tạo, mục tiêu không hợp lớp hoặc dữ liệu thiếu nguồn dù script hợp JSON.

**Producer:** thấy mục tiêu và evidence để hiểu hình nào cần thể hiện; panel báo thay đổi semantic. Sửa số liệu/đáp án không bị mất khả năng chỉnh clip, nhưng cần review mới trước approve/publish. Hỗ trợ cảnh/nhiệm vụ ngoài clip để không ép tất cả vào 90 giây.

**Publish/Library:** có thẻ “dùng cho lớp nào, mục tiêu nào, hỗ trợ bước nào, người học làm gì tiếp, nguồn và ai đã duyệt”. Không dùng nhãn “Bộ GDĐT chứng nhận”. Clip legacy hiện “Chưa đối chiếu hồ sơ STEM”, không giả nhãn đã đạt.

**AI:** build prompt từ catalog/outcome đã kiểm; dùng source IDs nội bộ, trả suggestions có objectiveKey/activityKey/evidence và uncertainty. Nếu thiếu context thì yêu cầu hoàn thiện brief, không tự bịa outcome. Bổ sung profile ngôn ngữ cho tiểu học. Đồng bộ Java prompt/schema và Python `prompting.py`, `prompts/*.txt`, contracts để các nhánh không lệch. Accept tạo dữ liệu draft, không tạo quyết định duyệt.

## 8 Migration và rollout bảo toàn pipeline

1. Inventory schema và kiểu FK trên staging, backup theo quy trình hiện có, chọn version Flyway chưa dùng. Không chạy DB production trong giai đoạn thiết kế.
2. Tạo catalog/bảng STEM và nullable snapshot columns; giữ cột subject/grade_level cũ, không sửa migration đã chạy. Deploy BE đọc optional và worker chịu metadata optional trước khi FE ghi.
3. Seed grade/cấp + ba policy DRAFT; chỉ seed outcome chính thức sau khi nguồn và chuyên môn xác minh. Catalog chưa VERIFIED không được bật strict.
4. Đánh dấu project cũ LEGACY_UNMAPPED. Chỉ map grade số đáng tin và đúng quy tắc; `Đại chúng`, `Cơ bản`, `Nâng cao`, `EdTech`, null không suy ra lớp. Lưu provenance của quyết định mapping.
5. Chạy chế độ advisory trên một workspace demo. So sánh validation với Reviewer và sửa policy; không tuyên bố advisory đã bảo đảm STEM.
6. Bật strict có version cho workspace/project mới sau khi ba bộ seed và tests đạt. Project cũ vẫn xem/download bản đã công bố; khi tạo bản mới/xuất bản mới phải hoàn thiện hồ sơ. Thay luật nghiêm trọng có thể cần gỡ/đánh dấu học liệu cũ qua quyết định riêng.
7. Library chuyển sang snapshot khi có; fallback legacy hiển thị nhãn rõ. Sau khi cả client đã chuyển mới xem xét loại bỏ compatibility fields; đây là giai đoạn sau, không bắt buộc để đóng Review 2.

Rollback giai đoạn đầu bằng tắt strict và quay FE về contract cũ, giữ bảng/record STEM để không mất evidence. Không drop dữ liệu mới khi rollback app. Một rollback khẩn cấp không được tiếp tục gắn nhãn STEM verified cho publication không qua gate; nếu BE cũ không giữ được điều này thì tạm khóa publish workspace strict trong rollback runbook.
