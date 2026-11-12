# Contract delta P3 — trước code sâu

05/11/2026. Phạm vi P3 đã duyệt Q01–Q11, BE cô lập f6c6f0b. Contract triển khai kỹ thuật này không cập nhật P2 hoặc chính thức SRS. Mọi nội dung giáo dục DRAFT/UNVERIFIED; không activation/STRICT setting. P0/P1/P2 PARTIAL giữ nguyên.

## 1. Physical mapping / prerequisites / environment

Persistence P3 dùng JDBC repositories trong Spring transaction, domain records/DTO Java; không thêm JPA entity scan vào baseline ứng dụng khi schema STEM chưa có. Feature `app.stem.enabled=false` mặc định; bật chỉ isolated test profile. JSONB aggregate chứa context/objectives/tasks/contributions với typed DTO; evidence/assessment/audit rows riêng. Không pretend số logical entities bằng số bảng. Topic/TopicVersion, SourceEdition, OutcomeEdition, PolicyVersion có repository/service DRAFT; topic constraints chỉ provenance/mapping, validator thực thi policy centrally. MVP catalog read/gán, không public catalog mutations/activation.

**M-B dải TEST ONLY V91000–V91009**, khác9000/9001 và candidate production23/24/25+. Chọn trong phạm vi Q01 cho phép; ledger ghi trước SQL. `src/test/resources/db/stem-p3/`, profile test locations V1–22 + auth-v2-e2e V9000/V9001 đúng một lần + stem-p3 V91000. Fresh new embeddedPG; không sửa fixture P0 hoặc source migrations. Không outOfOrder/repair/restore dump. Không shared connection/preflight/reservation. Test ledger/schema manifest tách production.

## 2. Exact DTO / limits — PRODUCT_RULE, không ngưỡng Bộ

Typed records Jackson của stem DTO reject unknown fields (kể cả server-owned claims); required nullable `context.primary` kiểm presence ở controller; create empty endpoint không cần full aggregate. IDs opaque max128; schemaVersion literal stem.v1; revisions integer >=0; phase SCRIPT/VIDEO. Subject/catalog IDs không lấy từ enum môn video legacy; mapping catalog được validate theo refs, không tự suy lớp.

Limits: request JSON UTF-8 **262144 bytes**, nested depth16, mỗi string max4000 (safety/scope/rationale), title/key/id128; maximum20objectives,20tasks,200evidence,4components(S/T/E/M không duplicate),20source/outcome/subject refs,4secondary contexts,10channels,100policy assignments, catalog page size1–100(default20), query title128. Lý do: ngăn payload/CPU amplification và giữ một brief review được; không bằng chứng sư phạm. Context enums IN_CLASS_SCREEN/GROUP_PRACTICE; distribution channel DEPARTMENT_LIBRARY. Unknown enum400. Valid enum nhưng thiếu primary/condition/adaptations là finding, draft vẫn lưu. Dữ liệu và tài liệu P1 không bị seed/chốt bởi enums.

Validate rate: 10 requests/actor/target/60 seconds, transaction persisted counter; exceeded429 + retry seconds. Mutation idempotency24h: key ASCII8–128chars, actor+operation+target+key scope, request digest SHA256 **transport digest only, KHÔNG semantic hash**. Idempotency ghi transaction cùng mutation; repeat cùng normalized DTO/target trả cached response không duplicate/revision bump; khác payload/target trong cùng scoped operation key409. Target là phần key scope nên key cùng actor/operation được ràng buộc một target trong thời hạn; khác target409. Expired receipt loại bỏ ở request tiếp theo trong scope; không background provider/cron.

DTO shape (JSON property names tương ứng Java records):
- Create empty POST version/stem: `{schemaVersion,expectedScriptRevision, idempotencyKey}` → brief revision0, primary:null, empty arrays, DRAFT/UNVERIFIED.
- PUT version/stem: `{schemaVersion,baseRevision,expectedScriptRevision,idempotencyKey,brief,evidence}`. `brief` required `{policyVersionId?,topicVersionId?,gradeKey?,subjectKeys:[],outcomeIds:[],scopeOfClip?,context:{primary:ContextEntry|null,secondary:[]},distributionChannels:[],objectives:[],studentTasks:[],contributions:[],safetyNotes?,rawLegacy?}`. Lists required, null lists reject400. rawLegacy string opaque bounded, never automatically mapped. ContextEntry `{key,condition?,adaptations?}`. Objective `{key,outcomeId?,learnerAction?,observableEvidence?}`. Task `{key,learnerAction?,teacherSupport?,materials?,safety?,assessmentEvidence?,executionContextKey?}`. Contribution `{component,explanation?,objectiveKeys:[],taskKeys:[]}`.
- Evidence `{id?,sceneId?,taskKey?,objectiveKey?,component,explanation?}`. At least scene/task required; wrong scene version400details; missing deleted target stored orphan only for existing evidence via originalSceneId, never accept arbitrary other-version scene. Delete referenced scene keeps originalSceneId and explicit orphan finding. Evidence removals explicit full PUT audit, submitted immutable. No approval supplied by caller.
- Settings PUT `{baseRevision,mode:OFF|ADVISORY,policyVersionIds:[],defaultPolicyVersionId?,idempotencyKey,legacyModeSelection?}`. Default at most1, must belong selected assignments. Legacy conflicting modes requires explicit selection; no max/min. Existing pins not rewritten when assignment/default changes. No policy mode override, no STRICT accepted.
- Validate POST `{expectedBriefRevision,expectedScriptRevision,phase,expectedCompositionRevision?}`. Returns append-only ValidationRun findings, phase target; no approve.
- Assessment additive on EXISTING approve request: `stemAssessment:{expectedBriefRevision,expectedScriptRevision,expectedCompositionRevision?,expectedModeRevision, rubricVersionId?,criteria:[{key,judgment,rationale,evidenceIds:[]}],idempotencyKey}`; only DRAFT/UNVERIFIED persisted. Allowed judgments ADEQUATE/NEEDS_CHANGES/NOT_ASSESSED/NOT_APPLICABLE; N/A rationale required. No expected semantic/presentation hash input trusted (not implemented P3). Criteria max20, evidenceIds20. Legacy `{note}` and Java one-arg constructor remain compatible.

Response brief `{schemaVersion,briefId,scriptVersionId,revision,verificationStatus:UNVERIFIED,readiness:INCOMPLETE|NEEDS_REVIEW|LEGACY_UNMAPPED,effectiveMode,modeRevision,brief,evidence,findings}`. No hash stub, certified label or verification claim. Empty GET returns brief:null/LEGACY_UNMAPPED. Catalog GET includes type/version/provenance/edition relations and paginated items; no mutation APIs. Settings default OFF revision0; explicit first save requires0; user update creates audit. List size caps checked before persistence.

## 3. Endpoints / permission / errors

GET `/api/v1/stem/catalog?kind=&page=0&size=20`: authenticated catalog metadata only. GET `/api/v1/stem/frameworks`: Admin only. GET/POST/PUT `/api/v1/scripts/{projectId}/versions/{versionId}/stem`: read access / active Writer (no Admin bypass for STEM authoring); must belong project, project not archived. POST same `/validate`: read access plus rate limit. GET/PUT `/api/v1/workspaces/{workspaceId}/stem-settings`: member read / systemAdmin manage; workspace must exist. No public draft catalog CRUD, no STEM approval endpoint.

SystemAdmin legacy approve follows existing controller permission if no stemAssessment; **assessment requires real active REVIEWER seat of target workspace, including Admin**. Inactive or cross-workspace403 (not found404 only when target absent). Service verifies even direct call; no reliance solely on controller annotation. Actor from security principal, not payload. Manual/AI accepted content uses same saved script/brief validator; no prompt/Python schema modification.

400 STEM_INVALID_FIELD details.field/findings for malformed/unknown/server-owned/policy assignment/reference violation; 409 STEM_STALE_TARGET for CAS target/brief/mode mismatch, STEM_IMMUTABLE for submitted mutation, STEM_IDEMPOTENCY_CONFLICT for mismatch; 403 for role; 404 missing; 429 rate; no422. Existing error envelope status/code/message/timestamp/details unchanged.

## 4. Transactions, copy, integration

Order authorize -> lock ScriptVersion row (for target mutations/transitions) -> workspace setting lock if present -> load brief -> idempotency lookup -> validate/CAS -> mutation/receipt/audit -> commit. Same project locks cover script edits/copy/review; settings updates lock workspace row, do not acquire script locks, avoid inversion. Brief CAS independent revision; validate transitions recheck script + brief + mode revisions. Settings creation under workspace row lock prevents first-write race. Active-seat check queries membership, not Admin cached bypass. All writes through JDBC use same DataSource/Spring transaction as JPA decision; no separate approval transaction.

Copy uses saved scene objects paired directly to original references during saveAll; map must one-to-one cover old scene IDs, no order/title matching. New version brief copied DRAFT/NEEDS_REVIEW, objective/task logical keys preserved scoped by new brief, evidence new IDs and copiedFrom, mapped scene references; validation/assessment/approval never copied. Missing/wrong/multiple mapping throws before commit. Delete scene retains orphan provenance; script/composition content changes conservatively mark existing assessment NEEDS_REVIEW, not claim actual semantic hash. No cached assessment survives a change of target revision.

Existing ScriptService submit/approve/requestChanges + FeedbackService approveVideo common prebranch permission/target/revision hooks only. Optional request stemAssessment permits atomic assessment+existing decision linkage; send-back/request-changes reason remain unchanged. Track decision IDs before/after same target transaction; require exactly one new existing decision to link, never infer by timestamp/latest row. Same assessment retry key only returns previous decision when same target/request; cross-target/payload409; no duplicated render enqueue. Legacy request without assessment unchanged when mode OFF/ADVISORY; findings advisory except malformed/reference/authorization. STRICT evaluator is pure unit input only; no persisted strict mode or fake verified fixtures. RenderService/ClipQaService/PublishService/LibraryService untouched. Full hash/snapshot/artifact protection P5; no code claim publication STEM current. AI Java/Python grounding, library filters P6; FE/worker consume DTO contract only, no code/build/tests.

## 5. Legacy / specialist / boundaries

Pin policy old version, never settings backfill. Disabled/retired/mismatch policy yields finding UNAVAILABLE and UNVERIFIED label; draft can persist unchanged pin, cannot choose a new unassigned pin. Submitted pin immutable; changes require copied new draft, no approval inheritance. Default policy only suggestion for future briefs, not implicit assignment. Raw context preserved, suggestions separate until Writer explicit CAS save. DEPARTMENT_LIBRARY -> suggested channel only, primary null. Variants contradictory in task/safety require human choice/separate version; machine checks references only.

Three profiles/briefs4/8/11 still tentative, no teacher feedback/datasheet/kit/outcomeMath11; DRAFT/UNVERIFIED, no catalog official seeds. P0AI exact-lock41/77 remainsP6 blocker. FR38 official sync requires separate approval. Native diagram export unresolved. No commit/push/deploy/shared; BM dates unchanged.

## Additive copy retry DTO, recorded before hook implementation

Existing request-changes accepts optional `changeTarget:{expectedVersionId,expectedScriptRevision,expectedBriefRevision,idempotencyKey}` alongside note; no stemAssessment accepted on rejection. New keyed clients get24h idempotent retry returning copied version, different note/target/key payload409. Legacy unkeyed note-only callers retain existing behavior (repeated after transition rejected, never duplicate); all request-changes now locks source version before rechecking status. Keyed target snapshot checked before copying; copy receipt commits in same transaction. This is additive contract detail for approved C08/Q10, not a new endpoint. Unknown fields still400details. Original note-only constructor and note+assessment constructor retained.

Evidence full PUT retires removed rows and records previous evidence in audit rather than destroying referenced history; retained IDs preserve copiedFrom. Nine-table physical catalog mapping is detailed in test ledger. Runtime feature defaults disabled by conditional components; no production config changes. Deployment with STEM schema is outside P3.

## Exact catalog response / transport cap

Catalog/framework reads return a JSON array of `{id,kind,logicalKey,version,parentId,sourceId,status:DRAFT,data:<JSON object>,revision,verificationStatus:UNVERIFIED}` bounded by page/size; no total-count envelope in P3. No public catalog mutation. A STEM-path servlet filter caps actual request bytes262144 (including whitespace) before Jackson, in addition to recursive node limits; only enabled when isolated app.stem.enabled=true. Filter uses same400/details envelope, no body logging. Existing note-only review endpoints remain compatible below this cap.

## Legacy suggestions DTO

View adds `legacySuggestion:{primaryKey?,channelKeys:[],requiresConfirmation,reason}` or null. rawLegacy=DEPARTMENT_LIBRARY suggests channel only, does not set primary/persist anything. Recognized learning-context legacy key suggests primaryKey only; unknown/multiple values suggest neither and require Writer choice/separate variant. rawLegacy always preserved; readiness LEGACY_UNMAPPED while context remains null. Explicit PUT with baseRevision is the sole persistence of Writer choice. Suggestions are not verified teaching context.

## Technical completeness / basis

Each finding includes basis=PRODUCT_RULE; no technical finding is represented as a Ministry law or a human scientific judgment. Empty objectives/tasks, missing learner action/observable evidence/task support/materials/safety/assessment and scope/safety notes are completeness findings, not rejection of draft save. Missing contributions warn, no min-two-subject rule and no auto-grading scientific quality. Synthetic CLIP-branch tests create local dummy artifact metadata with qa=PENDING, no MP4/network/render, not valid or verified learning content.

## Approval receipt binds full decision payload

Approval receipt digest includes assessment plus outer note/reason, not just assessment. Same key with changed note returns409 even when target is already approved. changeTarget is accepted only on request-changes; stemAssessment only on approve/approve-video, not send-back/reject. Legacy note-only requests remain unchanged. This closes action-field ambiguity without new approval endpoints.

## P2 enum alignment clarification (supersedes wider enumeration above)

P2 CONTRACT_SPEC starts with only IN_CLASS_SCREEN/GROUP_PRACTICE. Keep these two as the P3 typed learning-context enum; PRE_CLASS/HOME_SELF_STUDY in master/P1 remain hypotheses/raw legacy until a catalog change is explicitly reviewed. No extra context is silently introduced from the master. Unknown/hypothesis legacy keys remain raw, suggestedMapping requires Writer decision; no auto-conversion. This narrows the earlier draft delta enumeration to the approved input before handoff.
