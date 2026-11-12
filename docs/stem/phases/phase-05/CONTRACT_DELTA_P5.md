# P5 — Contract delta, Chặng 1

05/11/2026, Asia/Saigon. **DESIGN RECORDED / MIGRATION GATE PENDING / IMPLEMENTATION NOT_RUN.**

Phạm vi Q01 B, Q02 A, Q03 A, Q04 A, Q05 C, Q06 A có điều kiện, Q07 A, Q08 A, Q09 A, Q10 A, Q11 A, Q12 A đã duyệt theo bản nguyên văn `APPROVED_EXECUTION_PROMPT.md`. Chi tiết contract trong tài liệu này là thiết kế Chặng 1 để review, chưa là API triển khai. Không có SQL hoặc code P5 tại thời điểm bàn giao Chặng 1.

## 1. Baseline/trust boundary

- `FeedbackService` **đã có minimal hook P3 +13 dòng** (optional StemService, submission/prepare/finish trước CLIP/COMPOSITION); P5 mở rộng hook, không tạo từ đầu. Bốn service Render/QA/Publish/Library chưa patch P3.
- Inventory chốt **13 SceneTypes / 12 BlockKinds**, cùng classifier cho extraBlocks. Xem `FIELD_MATRIX_P5.md`.
- B HEAD f6c6f0bdcdcc6bbaf1f1246f86678d3249dea8e1 / feat/stem-p3-isolated-20261105; F HEAD 9fe67cf7801b3e971b3c852c80f68214fd13bba7 / feat/stem-p4-isolated-20261105. Uncommitted P3/fix/P4 được giữ. `INPUT_BASELINE.json` và `PRE_P5_SOURCE.patch` chụp cả tracked/untracked source trước ghi tài liệu P5.
- G06 không đổi: settings request wire **baseRevision** = cùng counter workspace mà FE gọi modeRevision; response revision/policyIds/defaultPolicyId giữ nguyên. Assessment.expectedModeRevision và View.modeRevision cũng giữ nguyên; không rename wire.
- Three hashes không chứng minh khoa học đúng, MP4 đúng byte hay teacher endorsement. Rubric/brief/source/catalog vẫn DRAFT/UNVERIFIED; mode OFF/ADVISORY, không bật STRICT. Client không được gửi hash/verification claim làm authoritative result.
- Receipt request_digest P3 chỉ là transport digest; không là bất kỳ hash dưới đây.

## 2. Hash specification — deterministic bytes

Phiên bản đề nghị dùng trong P5: `stem-hash.v1`, `stem-canonical.v1`, `stem-projector.v1`, `stem-classifier.v1`. Prefix/version không thay URL `/api/v1` hoặc P3 schema `stem.v1`.

Với projection name đúng một trong `semantic`, `presentationContract`, `renderInput`:

`digest = lowercase_hex(SHA256(UTF8(prefix) || canonical_projection_bytes))`

Prefix là chuỗi sau, **mỗi `\0` biểu thị đúng một byte NUL**, không phải hai ký tự backslash-zero:

`STEMotion/P5\0<projection>\0stem-hash.v1\0stem-canonical.v1\0stem-projector.v1\0stem-classifier.v1\0`

### 2.1. Canonical encoding

1. Input là JSON tree đã qua parsing strict; reject duplicate JSON keys, unpaired surrogate, NaN/Infinity/non-finite, unsupported scalar types; không stringify unknown Java objects. Projection giữ unknown JSON subtree để hash, đánh dấu UNKNOWN thay vì bỏ.
2. NFC cho string và object keys. Nếu hai key trùng sau NFC: 400 + field detail, không overwrite. Không trim/lower-case/strip dấu, sửa dấu trừ/đơn vị hoặc parse LaTeX để nhận tương đương.
3. Object keys sort theo **Unicode scalar value**, không phụ thuộc locale hoặc default UTF-16 ordering. JSON UTF-8 không BOM/whitespace; quote/backslash escape, control U+0000–001F dùng lowercase `\u00xx`; các Unicode scalar khác giữ UTF-8 literal, không escape slash hoặc toàn bộ tiếng Việt. Quy tắc escape này độc lập JSON pretty-printer.
4. Number: mathematical decimal finite, plain notation không exponent, không trailing decimal zeros/dot, `-0` → `0`. Không làm tròn/bỏ precision. Parsing number vào decimal chính xác trước canonical; adapter từ floating point có sẵn dùng finite shortest round-trip decimal của giá trị đang lưu, không lấy binary approximation tùy serializer. Giá trị lớn vượt giới hạn kỹ thuật §6 trả 400, không truncate. JSON literal `1`, `1.0`, `1e0` cùng canonical `1`.
5. Arrays giữ thứ tự. Chỉ projector xử lý các **set field nêu rõ**: subjectKeys/outcomeIds/distributionChannels và contribution.objectiveKeys/taskKeys hoặc reference sets tương ứng. Validate nonduplicate trước, sort Unicode/NFC; duplicate ref là lỗi, không xóa duplicate âm thầm. Không sort scenes/blocks/caption tokens/steps/options/points/objectives/tasks hoặc criterion results tùy tiện.
6. Missing và explicit null **khác nhau** trong unknown subtree. Trên typed P3 DTO, normalize nullable optional fields đúng whitelist DTO thành null; không biến null thành mảng rỗng hoặc field required thiếu thành default. Known presentation defaults chỉ dùng default renderer được đối chiếu và có version; không normalize arbitrary unknown keys.
7. Golden vectors xem `CANONICAL_VECTORS.json`. Chúng là fixture đặc tả tính bằng SHA-256 trên literal canonical bytes; **chưa có implementation test/PASS**.

### 2.2. Projection shape

| Projection | Payload logic | Không đưa vào / binding riêng |
|---|---|---|
| semantic | `{brief,editions,scenes,unknown}`. Brief gồm policy/topic/grade/subject/outcome refs, scope/context/conditions/adaptations/channels, objectives/tasks/contributions/safety/rawLegacy/evidence explanations và target relationships. Editions gồm ref+version+contentDigest của policy/rules/rubric/source/outcome/topic. Ordered scenes: type/title/narration/caption token text + S fields từng loại/block. Unclassified raw subtree nằm dưới unknown với path. | CapturedAt/actor/receipt key, counters, readiness/verification display status không là nghĩa. Target entity UUID và revisions nằm trong binding, không trộn vào semantic hash chỉ vì row thay. Edition content/hash không bị bỏ dù ID giữ nguyên. |
| presentationContract | `{semanticHash,presentation,unknown}`: visibility/coverage, layout relationships, frame/slots, cues token anchors/event ordering, audio/asset content identity, conditional styles/motion/tone theo matrix. Known raw conditional values được giữ nếu chưa có safe proof. | Không loại timing/style toàn cục. Có proof server cho safe subset mới thay numeric alignment bằng invariant contract. S thay đổi cũng làm contract khác qua semanticHash. |
| renderInput | `{inputProps,resolution,fps,ttsVoice,rendererIdentity,assetIdentities,audioIdentities,snapshotContentDigest}`. Hash đúng canonical input frozen trước dispatch, metadata P5 không làm renderer hiểu nghĩa mới. SnapshotContentDigest là digest nội dung nguồn/timing liên quan **không chứa renderInputHash**, tránh self cycle. | Job ID/attempt/actor/createdAt bind ngoài hash để retry same input vẫn equal. URL transport signed refresh chỉ loại khỏi identity khi đã có digest thật; frozen input thực gửi đi vẫn ghi URL thực trong inputProps, nên renderInput có thể khác dù audio content same. |

Evidence projection thay generated evidence/scene/block IDs bằng **reference map trong target version**: scene ordered position + stable logical objective/task keys; block references resolve vào ordered block index; preserve relationship. Actor/generated row IDs không tạo nghĩa mới. Snapshot vẫn giữ full original IDs và map để kiểm ownership. Dangling/duplicate/wrong-version refs không được canonical thành missing: malformed mới bị400, orphan legacy retained raw + NEEDS_REVIEW. Cùng hash ở target version khác **không copy approval**.

Catalog verification/retirement current status không sửa content hash lịch sử; transition recheck current eligibility riêng. Snapshot copy captured status và edition content để đọc độc lập. Đổi version bất kỳ trong hash/projector/classifier: transition mới NEEDS_REVIEW; không auto-migrate approval hoặc rewrite historical hashes.

## 3. Freshness, AT21 và gate semantics

`CURRENT` dưới đây chỉ **technical compatibility**, không VERIFIED khoa học. Public verificationStatus vẫn UNVERIFIED cho baseline hiện có.

| Kết quả server | Transition behavior |
|---|---|
| Exact target + versions + semantic/presentation contract match | Có thể reuse technical assessment reference trên cùng target lineage; kiểm seat/mode/policy/lifecycle và artifact riêng. |
| Semantic hoặc presentation contract khác | NEEDS_REVIEW; không nhận assessment cũ là áp dụng cho target mới. Stale request kỳ vọng target cũ →409 không partial write. OFF/ADVISORY có thể đi legacy/advisory workflow không assessment hợp lệ; **không dùng decision cũ gắn artifact mới**. Reviewer tạo decision đúng bản hiện tại qua workflow hiện hữu. |
| Unknown classification/proof | UNKNOWN/NEEDS_REVIEW; không auto-inherit. Reviewer vẫn có thể review workflow DRAFT/OFF/ADVISORY với technical limitations được capture; không tự chứng nhận visibility/semantic equivalence. |
| Malformed/ownership/CAS/lifecycle/wrong artifact hoặc media hard FAIL | Chặn theo existing guard/error ở mọi mode; OFF không bypass. |
| Chỉ thiếu expert/source/outcome completeness | Advisory OFF/ADVISORY; không scientific STRICT ngầm, không verified label. |

AT21 supported kỹ thuật: same version lineage, same scenes/IDs and field shapes; exact narration/caption token sequence, finite ordered caption boundaries within duration; evidence tokens/relationships không mất; event ordering/coverage không giảm; same audio content identity khi audio hiện hữu. Safe proof tối thiểu có thể cho **scene không arbitrary layout/visualPlan/conditional motion**, chỉ duration kéo dài/caption numeric align với token order không đổi. Time-derived event ordering phải tính từ projector bám renderer; không tự tính coverage=100% vì duration dương. Trường hợp chưa chứng minh được: UNKNOWN/NEEDS_REVIEW, không proof giả.

Giữ word tokens chính xác, chẳng hạn caption thêm/bớt “không”, `-`, `mA` khác `A` → semantic khác. Rút duration cắt event, answer trước question, draw/count/arrow đổi diễn giải, hidden/clipping, asset digest thiếu → không inherit. Technical safe compatibility append record (source approval, old/new measured targets, hashes, proofVersion/details); không sửa decision/assessment row cũ. Artifact mới vẫn cần QA. Không bypass CompositionService.lockedReason/CAS để chạy measured sync trong IN_QA/APPROVED. Test AT21 có thể chạy comparison cùng rule trên synthetic input; không nhận pixel/audio end-to-end PASS.

## 4. Review/render/publication transaction contract

1. Authorize project/workspace/active REVIEWER cho assessment (kể cả Admin); lock theo thứ tự thống nhất project → current ScriptVersion → settings (nếu có) → composition → brief/evidence → job/clip/publication khi cần. Trước implementation phải đối chiếu các lock P3 hiện hữu để không tạo đường ngược; settings update vẫn workspace-only. Không tự rename hoặc giảm kiểm P3.
2. Submit video: freeze candidate SUBMISSION snapshot từ actual composition (ensure như source hiện hữu), capture mode counter/brief tuple; new submission không approval.
3. Approve common hook tính target/projections **trước rẽ nhánh**; B01 có current clip: bind decision đúng clip/job/input, không enqueue thay clip. B02 chưa clip: decision+assessment+APPROVAL+RENDER_INPUT/job atomic; dispatch afterCommit chỉ frozen bytes. Legacy note-only approvals giữ signature nhưng không tự có STEM assessment.
4. Replay key actor+operation+target+key 24h: same request/target trả cùng decision/job; changed payload/note/target409; không enqueue lần hai. Không dùng latest timestamp để chọn decision/clip. Target scope và request digest theo P3 receipts, không thay P3 schema/key semantics; operation mới không trùng tên cũ.
5. Send-back/request-changes giữ reason/quyền và đường sửa; không bắt findings PASS. Copy scenes/evidence oldId→newId và orphan provenance P3 giữ; không copy approval/hash eligibility.
6. Callback: xem `CALLBACK_MANIFEST_PROPOSAL.md`; authenticated exact job/attempt/frozen input before current URL/clip state mutation. Terminal duplicate same payload no-op, contradictory replay409; old valid artifact không tự current/publishable. Retry dùng frozen input và attempt lineage; snapshot immutable.
7. Publish: project/current-version/composition/mode/clip/job/binding recheck trong transaction, require lifecycle + existing media QA trên frozen input. Create PublishRecord + Publication + PUBLICATION snapshot atomic/unique. YouTube request vẫn503 `PUBLISH_TARGET_UNAVAILABLE` trước writes. Duplicate/race không nhiều publication. Legacy route §5 riêng với known mismatch.
8. Feature persistence candidate backend JDBC sidecar để legacy unconditional JPA validate không đòi schema P5. Chỉ bật artifact layer trong isolated P5 test profile; default off ngoài test, không sửa production config. Chưa quyết/áp migration thì không bật feature hoặc chạy app.

## 5. Additive response and endpoint plan (chưa implemented)

Không sửa nội dung/hashes file `CONTRACT_DELTA.md` P3. Không thêm field vào P3 StemDtos.Assessment/View để old strict clients hỏng; hash/snapshot output dùng surface P5 riêng. Settings wire/G06 giữ nguyên. Không STEM approve endpoint mới.

**Proposed read endpoint** `GET /api/v1/scripts/{projectId}/stem-artifact-status`, cùng project read authorization, actual server target. Nếu caller chỉ định `clipId`/`publicationId` optional: verify same project/visibility; không mặc định brief current làm snapshot lịch sử. Response `schemaVersion:stem-artifact.v1`, `target`, `freshness`, `classification`, `integrity`, `publishEligibility`, `snapshotOrigin`, `verificationStatus:UNVERIFIED`, `reasons[]`, `hashVersions`, `hashes?`, `qa?`. `publishEligibility` là `{allowed:boolean,route:CURRENT|LEGACY_UNVERIFIED|BLOCKED,requiresLegacyAcknowledgement:boolean}`; không cấp quyền client bằng response stale.

GET không ghi validation/snapshot hoặc gọi provider. QA response chỉ saved/last applicable QA và `qa.evaluatedAgainst` frozen snapshot/job; missing QA là UNKNOWN, không chạy expensive checks liên tục qua GET. Existing qa-report transition/read behavior giữ; display phân biệt scientific warning với integrity block. Exact status enums dự kiến freshness CURRENT/NEEDS_REVIEW/LEGACY_UNMAPPED; classification CLASSIFIED/UNKNOWN; integrity MATCH/UNKNOWN/MISMATCH, không VERIFIED.

| Existing action | Candidate additive behavior, preserving old signatures |
|---|---|
| submit-video-review/approve-video/send-back | Internal snapshots/projections. Existing assessment target tuple vẫn server checks; optional `artifactExpectation` trên outer video approve request nếu cần, không chạm shape P3 stemAssessment. Legacy absent token phải recompute+bind actual; UI dùng status ref để không confirm nhầm. Exact outer field nếu dùng phải review trước code. |
| render start/retry | Server input freeze/sidecar bindings, không nhận client hash như proof; optional new response section chỉ khi consumer cần. Không apply P3 transport cap lên full render props. |
| callback | P5 optional manifest schema theo gate; không chạm worker trong scope hiện tại. |
| publish | Đề nghị optional outer `legacyUnverifiedAcknowledged:boolean` (default false), `artifactExpectation:{clipId,jobId,inputHash,snapshotId}` và `idempotencyKey` để bind user selection/retry. Fields supplied always checked, hashes remain untrusted preconditions. Legacy thiếu lineage phải explicit true; absent →409 candidate reason LEGACY_ACK_REQUIRED. Known mismatch vẫn409 kể cả acknowledgement true. |
| library reads/filters hiện hữu | Consumer new publication fields từ immutable publication snapshot/clip; existing entries không snapshot vẫn legacy read/export theo quyền, không infer/backfill curriculum/current STEM. Existing subject/grade filter và available filters phải lấy cùng snapshot fields như item; không chỉ sửa card rồi query project metadata. Không full P6 filters/card. |

Các endpoint/field/reason mới trên là thiết kế Chặng 1, **chưa tồn tại**. Surface publish expectation/legacy ack phải được ghi trong gate review cùng persistence/callback; không gọi endpoint này để test trước migration approval.

OFF: new publication UNVERIFIED hoặc LEGACY_UNMAPPED; historical captured mode/label/decision giữ nguyên. Current display projection có thể mất eligibility khi mode thay, phải trả capturedMode và effectiveMode tách; không sửa snapshot để rollback nhãn. Strict không được bật, positive verified path không fake fixture.

## 6. Limits inventory và errors

Read-only source inventory: P3 brief actual cap262144 bytes/depth16/text4000/key128/objectives20/tasks20/evidence200; catalog per-entry encoded length65536 hiện là Java chars (không byte proof), catalog ref graph có thể lớn hơn một brief. COMPOSED max12 blocks; strings block220, items6/icons6/points8/options2–4; renderer cũng slices12. Scene request/full render props chưa có aggregate cap tương đương P3. Snapshot gồm brief + nhiều catalog editions + inputProps, nên **không suy snapshot/renderInput <=256KB**. Chưa đo workload thực/DB vì stage1 không chạy app.

Đề nghị bounds riêng cho P5 layer, PRODUCT_RULE kỹ thuật; không nâng giới hạn endpoints P3 cũ:

| Surface | Proposed bounded input | Rationale/handling |
|---|---|---|
| Frozen inputProps server projection | 8MiB UTF-8 canonical, depth32, max200 scenes, max12 COMPOSED blocks/scene và max12 extraBlocks/scene, max20000 total JSON nodes, scalar text max65536 Unicode scalars; existing tighter validators still apply | Chặn amplification, cho lớn hơn brief; nonconforming legacy vẫn read/export, không truncate/hash một phần. New render/write cần explicit bounded failure. Các ngưỡng này cần fixture-boundary evidence trước thực thi feature; không claim derive từ dataset. |
| Snapshot payload | 16MiB UTF-8, depth40, max200 copied catalog editions, mỗi edition max262144 UTF-8 bytes và existing catalog limits; reject cyclic refs, resolve each ref một lần | Gồm input8MiB + brief + copied editions; tổng cap vẫn áp dù individual hợp lệ. Thiếu/overflow không bỏ source; return bounded failure/UNKNOWN legacy route theo step, không certified. |
| Proof/manifest | 64KiB JSON, depth8, IDs128, digests64 lowercase hex, versions128, per-scene proof max200, text4000 | Không nhận arbitrary tree/binary/audio trong callback. URL output cap4096 UTF-8, không log signed URL/key/token. |
| Proposed GET status | One project, optional IDs128; reasons tối đa200, page không thêm; không trả full16MiB snapshot | Stable small UI projection, không leak full input/catalog/token. |
| Publish additive body | Max64KiB total/depth8/new key8–128 ASCII; preserve tighter existing field guards; idempotency TTL24h | Payload nhỏ chứa expectations/ack, không nhận snapshot payload từ client. |

Numeric parsing guard proposal cho P5 projections: decimal precision<=512 digits, absolute exponent<=1024 và canonical number output<=2048 characters; validate trước expand/plain serialization, không allocate theo exponent vô hạn. ArtifactBytes nếu supplied phải integer trong 0..9007199254740991. Giới hạn này không làm tròn; overflow400details. Không sửa parser/guards P3 global để áp sang endpoint ngoài P5.

P3 validate rate10/actor/version/min unchanged; GET status không gọi expensive validator, áp existing auth/rate infrastructure; không tự đặt new global limiter. New transition hashes một lần sau locks, reuse bounded immutable bytes. Callback lifecycle không áp validate10/min vì progress callbacks khác workload; existing authenticated integration controls + bounded body/terminal dedupe. Rate code429 chỉ khi thực sự có rate guard, kèm Retry-After, không bịa429 cho unknown integrity.

Reuse error envelope `{status,code,message,timestamp,details}`. Existing400/403/404/409/429 và YouTube503 giữ; callback thiếu key401 existing giữ. **Không422**. Đề nghị reuse `STEM_INVALID_FIELD` cho invalid P5 input (400); `STEM_STALE_TARGET` cho stale/known mismatch/legacy ack conflict (409), với `details.reason` versioned thêm: `HASH_VERSION_CHANGED`, `REVIEW_TARGET_CHANGED`, `ARTIFACT_BINDING_MISMATCH`, `CALLBACK_MANIFEST_MISMATCH`, `LEGACY_ACK_REQUIRED`, `UNKNOWN_CLASSIFICATION`. Unknown scientific finding không tự trả409; chỉ stale precondition/blocked integrity mutation dùng409. Idempotency conflict reuse existing code. Đây là candidate reasons, FE chỉ implement sau contract Chặng2, không hardcode chưa có BE.

## 7. Stage1 gate / validation chưa chạy

Xem `MIGRATION_PROPOSAL.md`: đề nghị 2 sidecar tables + TEST_ONLY V92000/location riêng; user/Bond phải xác nhận trước SQL. Xem `CALLBACK_MANIFEST_PROPOSAL.md`: không worker patch, manifest optional input có assurance limits và explicit legacy callback policy chờ cùng gate. Không endpoint/code/migration/test/app nào được thực thi tại Chặng1; regression38+3/216/2live giữ là kế hoạch bắt buộc, không PASS mới.

**FE dependency mới tìm thấy:** publisher hiện nằm ở `src/services/publicationService.ts` và `src/components/modals/PublishModal.tsx`, ngoài danh sách FE cụ thể prompt §4.8. Chỉ hiển thị status ở Producer/StemReadOnlySummary trong allowlist vẫn làm được; muốn gửi explicit legacy ack/expectation qua flow publish hiện hữu cần mở đúng hai file này (và test P5 mới, giữ P4 test source/config/runner). Chưa có quyền sửa chúng, không tạo publisher khác trong service được phép để lách allowlist. Trình cùng gate; nếu không duyệt, giữ publish UX legacy-ack integration NOT_RUN/blocker, BE API route có thể test qua HTTP sau gate.
