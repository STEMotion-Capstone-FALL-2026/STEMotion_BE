# P5 — Contract thực tế trong clone, bàn giao 06/11/2026

**LOCAL / UNCOMMITTED / P5 PARTIAL, chưa nghiệm thu hoặc đóng phase.** File này bổ sung actual lên thiết kế Chặng1; không sửa CONTRACT_DELTA.md P3, prompt duyệt, scope hoặc handoff/log P3/P4. CONTRACT_DELTA_P5.md và FIELD_MATRIX_P5.md giữ làm design history; khác biệt/giới hạn actual được ghi dưới đây.

## 1. Surface đã triển khai

| Surface | Actual additive contract |
|---|---|
| GET `/api/v1/scripts/{projectId}/stem-artifact-status` | Member được đọc project; actor từ principal. Trả schemaVersion `stem-artifact.v1`, target current project/version/composition + clip/job/snapshot/inputHash khi có; freshness CURRENT/NEEDS_REVIEW, classification CLASSIFIED/UNKNOWN, integrity MATCH/MISMATCH/UNKNOWN, publishEligibility `{allowed,route,requiresLegacyAcknowledgement}`, snapshotOrigin `{snapshotId,capturedMode,effectiveMode}`, verificationStatus UNVERIFIED và reasons. CURRENT chỉ là tương thích kỹ thuật. |
| Snapshot lịch sử | Endpoint status chỉ current target trong P5; optional historical selectors/hash-version details/QA inline ở proposal Chặng1 chưa được mở. Publication snapshot-backed metadata qua library API hiện hữu. Không lấy current brief làm metadata clip lịch sử. |
| POST video submit / approve / send-back | Giữ endpoint/note/StemAssessment P3. P5 mở rộng common hook có sẵn ở FeedbackService, capture submission và exact existing decision trước rẽ CLIP/COMPOSITION. Không thêm approve endpoint, không thay outer ReviewDecisionRequest P3 bằng API khác. |
| Render start/retry/callback | Giữ URL hiện hữu `/api/v1/render/...`. Job inputProps/version/composition refs vẫn core; sidecar lưu fingerprints, parent approval, nonce attempt. P5 automatic retry tạo job/attempt riêng, frozen input giữ; khi feature P5 tắt vẫn hành vi retry baseline cũ. |
| Callback DTO | Thêm `artifactManifest:JsonNode?`; các constructor6/7args cũ giữ. Manifest optional gồm schemaVersion `stem-render-manifest.v1`, attemptToken, renderInputHash, snapshotId, rendererVersion và optional artifactSha256/artifactBytes/assurance. Các giá trị expected so từ server snapshot. Không nonce qua GET public; hash/nonce không thay API-key auth. |
| POST `/api/v1/scripts/{projectId}/publish` | Existing targets/title/description + optional `legacyUnverifiedAcknowledged`, `artifactExpectation:{clipId,jobId,snapshotId,inputHash}`, `idempotencyKey`. Constructor3args cũ giữ. Supplied expectations phải match; chúng chỉ preconditions, không authority. Thiếu lineage/manifest thuộc legacy route cần ack; mismatch đã biết không được ack bypass. |
| Publish replay | Optional new key dùng receipt operation P5_PUBLISH, actor/operation/key + target/digest check, TTL24h; same request trả kết quả cũ, changed payload/target409. Publication/PublishRecord/snapshot/receipt cùng transaction. Existing controller trả201 cả replay; enqueue trả202 như baseline, không sửa thành200 để khớp test. |
| Media QA | Existing `/qa-report`, thresholds PASS/WARN/FAIL giữ; khi có frozen job snapshot, chạy checks trên frozen scenes và job fps. Thêm nullable `evaluatedAgainst:{jobId,snapshotId,inputHash,...}`; giữ constructor5args cũ. Không tự tạo QA score khoa học. |
| Library list/detail/filters | Existing title/subject/grade/duration/count/owner/workspace từ publication snapshot khi có; export metadata từ publication gắn đúng clip, không latest record. Existing subject/grade filter và availableFilters dùng cùng snapshot values. DTO thêm verificationStatus/snapshotId, constructor cũ mặc định LEGACY_UNMAPPED/null. Legacy không backfill curriculum; vẫn read/export theo quyền/lifecycle cũ. Không full P6 filters/card. |

G06 settings wire vẫn **baseRevision** cùng workspace counter; FE alias modeRevision và response adapter P4 giữ. Assessment.expectedModeRevision/View.modeRevision giữ. Không422 hoặc policy/source/outcome/rubric activation.

## 2. Hash / target / classification thực tế

`StemFingerprint` triển khai SHA-256 với domain/version prefix Chặng1, UTF-8/NFC, Unicode scalar key sort, strict duplicate/NFC-key collision/surrogate handling, finite bounded decimal/-0, ordered arrays và declared reference-set sort. Golden spec29 được kiểm bằng implementation; tổng pure projection suite92 tests. Transport receipt digest vẫn độc lập semantic hash.

- Semantic projection giữ brief/context/tasks/objectives/contributions/evidence relationships, catalog edition data/digests, ordered scene content; generated IDs bind riêng và reference-map trong target. Unknown raw fields không bị bỏ khỏi fingerprints. Full original IDs/provenance vẫn trong snapshot.
- PresentationContract giữ semanticHash + conditional presentation/unknown data và composition visualTheme. RenderInput hash giữ exact inputProps/config/expected renderer identity. Renderer identity là expected FE template baseline `FullSTEMVideo:baseline-9fe67cf`, **không đo worker binary hoặc chứng minh MP4**.
- Đổi bất kỳ hash/canonical/projector/classifier version không được nhận là current qua matching; lịch sử bất biến, không auto-migrate approvals. Target version/revision/CAS vẫn kiểm; hash equal không copy approval sang version mới.
- **Supported numeric timing proof hiện tại: TITLE_HERO/OUTRO mặc định**, same IDs/semantic/contract, duration không giảm, finite ordered caption times trong scene, exact tokens; absence caption alignment được project từ narration tokens nhưng narration nguyên văn vẫn trong semantic hash. Có record technical compatibility append-only; no expert verification/pixel/audio claim.
- 13 SceneTypes và12 BlockKinds/extraBlocks đều được projection/hash. **11 template động còn lại, block/cue/layout/motion/visualPlan/asset/audio hoặc theme chưa có proof giữ UNKNOWN/NEEDS_REVIEW cho auto-inheritance.** Đây là supported-subset boundary, không “mọi scene duration tăng là safe”. Không có block thứ13/table.
- Projection-level mutation specimens của92tests có nhiều mẫu tối giản/chưa đủ scene DTO; không gọi đó là valid template acceptance hoặc pixel proof. Schema validators/mapper regression hiện hữu chạy riêng. Không nhận full F09/F10/renderer coverage từ số lượng testcase.

## 3. Frozen snapshots và integrity

Hai sidecar TEST_ONLY: stem_p5_snapshots và stem_p5_compatibility. Stage SUBMISSION → APPROVAL → RENDER_INPUT → CLIP/PUBLICATION. Snapshot copy target, mode/counter, hashes/versions, brief/evidence/edition data, public metadata, refs và frozen input; DB reject UPDATE/DELETE, RLS backend-only. Existing core tables/migrations không alter.

Active-seat/CAS P3 giữ. Khi review, P5 xác định đúng existing decision mới trong transaction bằng tập ID trước/sau, không chọn latest timestamp. SCRIPT assessment có snapshot để measured composition cùng version có thể lưu technical compatibility; legacy note-only không tự được cấp assessment. Video approval CLIP bind clip hiện tại, không enqueue thay; COMPOSITION ghi approval snapshot rồi enqueue sau commit. Send-back vẫn mở theo quyền/reason.

Callback auth hiện hữu; supplied manifest sai input/job/attempt/snapshot/renderer →409 no partial write. Same terminal completion no-op, contradictory URL/manifest409; valid old job chỉ historical, không đè current URL khi target đã đổi. Retry P5 có job/nonce mới, input hash giữ; callback attempt cũ không thắng. Missing manifest là UNKNOWN, cần explicit unverified route khi publish. Với P5 job đã có atomic review lineage, missing/stale approval trong chain bị chặn; mixed feature-flag epochs không được tự hợp thức hóa thành verified và rollout/reconciliation vẫn ngoài P5.

Publish recheck độc lập version/composition/job/input/snapshot/approval, quyền/lifecycle, current settings, media hard FAIL. OFF/ADVISORY không bỏ integrity hoặc QA; scientific completeness vẫn advisory, mọi label còn UNVERIFIED/LEGACY_UNMAPPED. New OFF publication không certified; captured history/mode/decision không rewrite khi OFF. YouTube vẫn503 PUBLISH_TARGET_UNAVAILABLE trước writes.

**Hash echo/API key/artifact digest assertion chỉ correlation, không MP4 byte-proof.** Không worker patch, không tải arbitrary artifact/audio URL, không real MP4/AI/TTS. Audio signed URL same-content proof chưa có resolver được kiểm chứng; F13 positive content-digest inheritance còn PARTIAL, thiếu proof luôn UNKNOWN.

## 4. Bounds và errors actual

P3 guards không đổi. New P5 callback raw body<=1MiB, publish<=64KiB, JSON nesting<=16, bounded strings/numbers/strict duplicate/root field whitelist; manifest<=64KiB, output URL<=4096 UTF-8 bytes. Chặng1 proposal riêng manifest depth8 chưa là guard độc lập: actual whole-envelope depth16. Frozen input<=8MiB; snapshot<=16MiB, projector node/depth/string/number bounds; max200 scenes/12 blocks per array and catalog closure200. Existing tighter scene/block validators giữ. Không áp brief256KiB lên full renderInput hoặc tự thêm limiter10/min cho callbacks.

400 STEM_INVALID_FIELD + details;403/404 theo guards hiện hữu;409 STEM_STALE_TARGET với reason ARTIFACT_BINDING_MISMATCH/CALLBACK_MANIFEST_MISMATCH/REVIEW_TARGET_CHANGED/LEGACY_ACK_REQUIRED, idempotency conflict giữ code riêng; existing429 Retry-After ở validate và503 YouTube giữ. FE không tính trusted hash hoặc giả quyền từ allowed flag; backend luôn recheck. New DTO constructor compatibility giữ legacy clients.

## 5. Feature/environment boundary

`app.stem.artifact.enabled=true` chỉ trong owned P5 runners/tests (cùng app.stem.enabled và26test migrations). Không sửa production application config/lock/toolchain/auth cutover; default feature absent/disabled, không shared. P3/fix/P4 files vẫn uncommitted cùng branch/HEAD baseline. Test/renderer input payload hợp lệ không chứng minh chương trình GDPT đúng; cả3brief4/8/11 và mọi provenance/rubric DRAFT/UNVERIFIED.
