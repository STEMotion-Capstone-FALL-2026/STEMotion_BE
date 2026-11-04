# Sơ đồ và traceability STEM đề xuất

Tất cả sơ đồ trong file này là **TO-BE**, chưa phải bằng chứng chức năng đã triển khai. Giữ sơ đồ gốc để đối chiếu. Mermaid dùng cho context/conceptual/ERD/class/activity/sequence/state; use case UML đúng ký pháp nằm trong [PlantUML riêng](diagrams/stem-usecases.puml). Đây là nguồn sơ đồ có thể chỉnh sửa, chưa xuất ảnh thay vào Word/drawio.

## 1 Context và ranh giới trách nhiệm

```mermaid
flowchart LR
  Teacher[Giáo viên hoặc nhóm học liệu] --> FE[STEMotion Web Studio]
  Reviewer[Reviewer chuyên môn] --> FE
  Producer[Producer] --> FE
  FE --> BE[Core API và domain STEM]
  Docs[Nguồn giáo dục và khoa học được xác minh] --> Registry[Catalog và policy version]
  Registry --> BE
  BE --> DB[(PostgreSQL)]
  BE --> AI[AI gợi ý theo STEM context]
  AI --> BE
  BE --> Worker[Render và TTS worker]
  Worker --> BE
  BE --> Library[Publication và thẻ học liệu]
  Library --> Learner[Học sinh xem clip và thực hiện nhiệm vụ]
```

Registry do người phụ trách xác minh; mũi tên nguồn không hàm ý hệ thống tự crawl hoặc tự diễn giải pháp luật. Học sinh là đối tượng hưởng lợi, không mặc định user đăng nhập.

## 2 Conceptual domain

```mermaid
flowchart TD
  Workspace -->|cho phép| Policy[STEM Policy Version]
  Policy -->|tham chiếu| Source[Reference Document]
  Source --> Outcome[Curriculum Outcome theo môn và lớp]
  Workspace --> Project[Clip Project]
  Project --> Version[Script Version]
  Version --> Brief[STEM Brief]
  Brief -->|chọn| Policy
  Brief --> Context[Learner Context và Real World Problem]
  Brief --> Objective[Learning Objective]
  Objective -->|đối chiếu| Outcome
  Brief --> Subject[Môn chính và môn phối hợp]
  Brief --> Activity[Learning Activity và Criteria]
  Brief --> Safety[Risk Assessment]
  Brief --> Evidence[STEM Integration Evidence]
  Evidence -->|thể hiện trong| Scene[Scene]
  Evidence -->|hoặc nhiệm vụ ngoài clip| Activity
  Version --> Scene
  Version --> Composition
  Composition --> RenderJob
  RenderJob --> Clip
  Brief --> Validation[STEM Validation]
  Validation --> Decision[Human STEM Review]
  Decision -->|cho đúng snapshot| Publication
  Clip --> Publication
  Publication --> Card[Thẻ học liệu và hoạt động tiếp nối]
```

Context/Objective/Activity/Safety là value object có schema trong brief ở MVP, không bắt buộc mỗi khái niệm thành một bảng. Subject/Outcome/Policy/Evidence/Decision có dữ liệu và quan hệ độc lập. Conceptual không trộn actor Writer/Reviewer vào entity dữ liệu.

## 3 DB ERD phần mở rộng

```mermaid
erDiagram
  grade_levels ||--o{ grade_subjects : permits
  school_subjects ||--o{ grade_subjects : maps
  grade_subjects ||--o{ curriculum_outcomes : has
  reference_documents ||--o{ curriculum_outcomes : defines
  stem_policy_versions ||--o{ policy_document_refs : cites
  reference_documents ||--o{ policy_document_refs : supports
  workspaces ||--o{ workspace_stem_policies : allows
  stem_policy_versions ||--o{ workspace_stem_policies : assigned
  clip_projects ||--o{ script_versions : versions
  script_versions ||--o| stem_briefs : has
  stem_policy_versions ||--o{ stem_briefs : governs
  grade_levels ||--o{ stem_briefs : targets
  stem_briefs ||--o{ stem_brief_subjects : integrates
  grade_subjects ||--o{ stem_brief_subjects : contributes
  stem_briefs ||--o{ stem_brief_outcomes : maps
  curriculum_outcomes ||--o{ stem_brief_outcomes : fulfills
  script_versions ||--o{ scenes : contains
  stem_briefs ||--o{ stem_scene_evidence : proves
  scenes o|--o{ stem_scene_evidence : anchors
  reference_documents o|--o{ stem_scene_evidence : supports
  stem_briefs ||--o{ stem_validation_runs : validated
  script_versions ||--o{ stem_validation_runs : targets
  compositions o|--o{ stem_validation_runs : targets
  clips o|--o{ stem_validation_runs : targets
  stem_validation_runs ||--o{ stem_review_decisions : reviewed
  users ||--o{ stem_review_decisions : decides
  stem_review_decisions o|--o{ render_jobs : script_gate
  render_jobs ||--o| clips : produces
  stem_review_decisions o|--o{ publications : clip_gate
  clips ||--o{ publications : publishes

  stem_briefs {
    string id PK
    string script_version_id FK
    string policy_version_id FK
    int grade_no FK
    int revision
    string schema_version
    jsonb objectives
    jsonb activities
    jsonb safety
    jsonb source_snapshot
  }
  stem_scene_evidence {
    string id PK
    string brief_id FK
    string scene_id FK
    string source_document_id FK
    string component
    string objective_key
    string activity_key
    string pedagogical_role
    jsonb evidence_payload
  }
  stem_validation_runs {
    string id PK
    string brief_id FK
    string script_version_id FK
    string composition_id FK
    int composition_revision
    string clip_id FK
    string fingerprint
    string policy_hash
    string rules_hash
    jsonb report
  }
  stem_review_decisions {
    string id PK
    string validation_run_id FK
    string reviewer_id FK
    string fingerprint
    string decision
    jsonb criterion_results
  }
```

Kiểu `string` trong sơ đồ không quy định SQL VARCHAR hay UUID. Xem tài liệu 03 để biết unique/FK/check/invariant và snapshot columns ở render_jobs/clips/publications. Cardinality optional tồn tại để hỗ trợ draft/legacy; **strict submit/publish có điều kiện bắt buộc mạnh hơn ERD**. Không có FK cho `activity_key` nội bộ JSON; service/schema kiểm tra key tồn tại. Không vẽ các FK cũ không liên quan để giữ sơ đồ dễ đọc.

## 4 Class và trách nhiệm

```mermaid
classDiagram
  class StemBriefService {
    saveDraftWithRevision()
    copyForReturnedVersion()
    resolveContext()
  }
  class StemCatalogService {
    verifiedOutcomesForGrade()
    allowedPolicyForWorkspace()
  }
  class StemValidationService {
    validateStructure()
    requireCurrentDecision()
  }
  class StemFingerprintService {
    scriptFingerprint()
    compositionFingerprint()
    artifactFingerprint()
  }
  class StemReviewService {
    decideWithRubric()
  }
  class StemSnapshotService {
    freezeForRender()
    freezeForPublication()
  }
  ScriptService --> StemBriefService
  ScriptService --> StemValidationService
  StemBriefService --> StemCatalogService
  StemValidationService --> StemFingerprintService
  StemReviewService --> StemValidationService
  RenderService --> StemSnapshotService
  PublishService --> StemValidationService
  PublishService --> StemSnapshotService
  AiScriptService --> StemBriefService
```

Đây là trách nhiệm logic, có thể gộp class nhỏ trong implementation; không tạo abstraction chỉ để khớp số box. Entity/DTO/repository theo convention package đang có.

## 5 Activity từ brief đến xuất bản

```mermaid
flowchart TD
  Start([Bắt đầu]) --> Profile[Chọn cấp lớp môn và policy]
  Profile --> Draft[Lưu draft brief và script]
  Draft --> Validate[BE kiểm tra cấu trúc nguồn evidence]
  Validate --> Ready{Đủ điều kiện submit?}
  Ready -->|Không| Fix[Trả lỗi rule và field cho Writer]
  Fix --> Draft
  Ready -->|Có| Review[Reviewer thẩm định mục tiêu và tích hợp STEM]
  Review --> Accept{Đúng nội dung và rubric?}
  Accept -->|Không| Return[Tạo version sửa và giữ lịch sử]
  Return --> Draft
  Accept -->|Có| Produce[Producer tạo composition]
  Produce --> Changed{Semantic fingerprint đổi?}
  Changed -->|Có| Recheck[Review STEM của composition mới]
  Recheck --> Produce
  Changed -->|Không hoặc đã review đúng hash| Freeze[Đóng băng input và STEM snapshot]
  Freeze --> Render[Render và TTS]
  Render --> ClipReview[Review clip và media QA]
  ClipReview --> Gate{Artifact và STEM decision còn khớp?}
  Gate -->|Không| Produce
  Gate -->|Có| Publish[Publish clip và thẻ học liệu snapshot]
  Publish --> End([Học sinh dùng clip và nhiệm vụ tiếp nối])
```

Sơ đồ mô tả invariant, không thay workflow web-first hiện có bằng yêu cầu luôn render MP4 trước mọi review. T09 giữ đường reviewer xem composition trước render; cần quyết định semantic hợp lệ khi render và acceptance đúng artifact khi công bố.

## 6 Sequence submit và approve

```mermaid
sequenceDiagram
  actor W as Writer
  actor R as Reviewer
  participant FE as Web Studio
  participant API as ScriptService
  participant ST as STEM Services
  participant DB as Database
  W->>FE: Lưu brief và scene
  FE->>API: Submit với version và revision
  API->>DB: Đọc và khóa bản hiện tại
  API->>ST: Validate profile, outcomes, evidence
  alt Thiếu hoặc sai dữ liệu
    ST-->>API: Rule findings
    API-->>FE: 422, không đổi trạng thái
  else Đủ cấu trúc
    API->>DB: Lưu validation và IN_REVIEW atomically
    API-->>FE: Report cùng fingerprint
  end
  R->>FE: Xem evidence và chấm rubric
  FE->>API: Approve đúng fingerprint
  API->>ST: Tính lại fingerprint và kiểm tra rubric
  alt Stale hoặc rubric không đạt
    API-->>FE: 409 hoặc lỗi rubric, không approve
  else Hợp lệ
    API->>DB: Append STEM decision và workflow approval atomically
    API-->>FE: APPROVED
  end
```

## 7 State STEM độc lập pipeline

```mermaid
stateDiagram-v2
  [*] --> INCOMPLETE
  INCOMPLETE --> NEEDS_REVIEW: Đủ cấu trúc và nguồn
  NEEDS_REVIEW --> APPROVED: Reviewer đồng ý đúng fingerprint
  NEEDS_REVIEW --> CHANGES_REQUIRED: Reviewer yêu cầu sửa
  CHANGES_REQUIRED --> INCOMPLETE: Draft version mới
  APPROVED --> STALE: Semantic hoặc policy áp dụng thay đổi
  STALE --> NEEDS_REVIEW: Validate lại bản hiện tại
```

Đây là view trạng thái suy ra từ report/decision/hash, không sửa đè record quyết định cũ. RenderJob giữ QUEUED/PROCESSING/COMPLETED/FAILED; script/video vẫn giữ enums hiện có.

## 8 Ma trận use case và requirement đề xuất

| Requirement / UC | Actor | Quy tắc | Entity | Gate/API | Acceptance |
|---|---|---|---|---|---|
| FR-ST01 / UC-ST01 Cấu hình phạm vi STEM workspace | Admin | BR-ST01/02 | Policy, WorkspaceStemPolicy | Workspace policy API | AT01/13/14 |
| FR-ST02 / UC-ST02 Chọn chương trình và yêu cầu cần đạt | Writer | BR-ST01/02/03 | GradeSubject, Outcome | Catalog/brief | AT01/04/06 |
| FR-ST03 / UC-ST03 Soạn bối cảnh và hồ sơ STEM | Writer | BR-ST04/05/06 | Brief/Objective/Activity | Brief save/submit | AT03/07/20 |
| FR-ST04 / UC-ST04 Gắn minh chứng STEM với scene/task | Writer | BR-ST07/09/10 | SceneEvidence | Evidence in brief | AT05/06/07 |
| FR-ST05 / UC-ST05 Nhận AI gợi ý theo chương trình | Writer | BR-ST03/10/11 | Brief/AiRequest | Existing AI API extended | AT04/17 |
| FR-ST06 / UC-ST06 Kiểm tra điều kiện nộp script | Writer | BR-ST01…11 | ValidationRun | Submit | AT01–08 |
| FR-ST07 / UC-ST07 Thẩm định nội dung STEM | Reviewer | BR-ST07/08/10/12 | ReviewDecision | Approve extended | AT05/08/09 |
| FR-ST08 / UC-ST08 Bảo toàn nội dung STEM khi dựng | Producer | BR-ST12/13 | Composition/Evidence | Composition update/render | AT10/11/12 |
| FR-ST09 / UC-ST09 Kiểm tra clip theo mục tiêu học | Reviewer | BR-ST08/09/12/13 | Clip/Validation/Decision | Video review | AT10/12/16 |
| FR-ST10 / UC-ST10 Công bố gói học liệu STEM | Producer theo quyền hiện có | BR-ST12/14/15 | Publication/Snapshot | Publish | AT14/15/16 |
| FR-ST11 / UC-ST11 Tìm học liệu theo cấp/lớp/outcome | Authorized User | BR-ST01/03/14 | Publication snapshot | Library | AT15/19 |
| FR-ST12 / UC-ST12 Chuyển đổi project legacy | Writer có quyền + người duyệt | BR-ST15 | Brief/Migration state | Brief update/submit | AT02/18 |

Các use case nền như login, render progress, quản lí thành viên không cần giả tạo hành vi sư phạm riêng. Trong UC specification phải ghi chúng hỗ trợ workspace/project STEM nào, còn nội dung ràng buộc tập trung ở use case tạo/duyệt/sản xuất/công bố. “Mọi use case liên quan STEM” nên hiểu là traceable tới mục tiêu hệ thống, không phải chèn chữ STEM vào mọi nút.
