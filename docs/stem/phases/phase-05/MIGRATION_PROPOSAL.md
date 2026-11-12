# P5 — Đề xuất persistence / gate số-location migration

05/11/2026. **PENDING EXPLICIT USER/BOND CONFIRMATION. Chưa viết SQL, chưa reserve production, chưa chạy DB/app/test.**

Prompt thực thi §4 Chặng1.2 yêu cầu trình số/location rồi **DỪNG trước bất kỳ file migration SQL nào**. Q06 A chỉ duyệt hướng có điều kiện, chưa cấp số. Tài liệu này là logical/physical proposal, không SQL/DDL hoặc lời xác nhận số đã được cấp.

## 1. Kết quả khảo sát source-only

| Existing storage | Có thể tái dùng | Thiếu so với P5 |
|---|---|---|
| render_jobs.input_props + version/composition_revision + attempt_count | Exact frozen renderer props và retry same input; afterCommit dispatch hiện có | Không snapshot STEM/algorithm versions/decision binding/attempt token/proof; props chưa có DB immutability. Không có submission snapshot trước job. |
| clips(render_job_id unique, video_url, qa_report, duration_sec) | Exact artifact row per job; existing completion no URL overwrite | qa_report là kết quả QA, không nên nhét decision/brief provenance rồi dựa mutable QA update; chưa digest/lineage snapshot. |
| publications.metadata + clip_id, project/platform unique | Publication export/title metadata cũ và transaction hiện có | Thiếu STEM snapshot/lineage; LibraryService vẫn đọc project/latest record. JSON metadata không có stage-level FK/immutable review chain. |
| stem_assessments / stem_validation_runs | Assessment đúng existing decision, original revisions và findings; preserve P3 | Không có 3 hashes/versions/frozen semantic/proof. Không mutate P3 payload/checksum/history để thêm data quá khứ. |
| stem_receipts / stem_audit | Existing scoped idempotency24h và audit mutation | Receipts expire, không durable snapshot; audit mutable JSON aggregate không thay FK/immutable binding model. |

**Kết luận đề xuất:** reuse core inputProps/artifact/publication records, thêm **2 bảng sidecar** và references bằng JDBC conditional; không thêm cột lên entity/core tables hoặc sửa P3 V91000. Tránh unconditional JPA schema validate đòi P5 trong baseline legacy tests. Chặng2 có thể đề nghị điều chỉnh nếu constraints không biểu đạt được, nhưng không tự thêm bảng/cột/số ngoài gate này.

## 2. Physical proposal — không phải DDL

### 2.1. `stem_p5_snapshots` (append-only)

| Nhóm | Field logic dự kiến / invariant |
|---|---|
| Identity | id opaque varchar36; stage SUBMISSION/APPROVAL/RENDER_INPUT/CLIP/PUBLICATION; schema_version stem-snapshot.v1; captured_at; actor_id nullable cho integration; dedupe_key64 server-derived unique. Dedupe dùng operation+target+content+receipt/transition identity, không semantic hash riêng làm identity. |
| Target FK | workspace_id/project_id/script_version_id required; brief_id nullable cho legacy, brief_revision/script_revision/mode_revision không âm; composition_id/revision nullable khi script-only. IDs phải cùng project/workspace/version qua service checks (cross-table relational invariant), không chỉ FK tồn tại. |
| Stage linkage | parent_snapshot_id nullable self-FK, approval_snapshot_id nullable self-FK; decision_id FK status_histories và assessment_id FK stem_assessments nullable cho legacy note-only. APPROVAL bắt decision thật; không fake assessment để đủ FK. Stage-specific parent kind kiểm transactional service và new-table trigger nếu cần. |
| Artifact linkage | render_job_id nullable; clip_id nullable; publication_id nullable; attempt_token và assuranceLevel. Unique render_job_id khi stage RENDER_INPUT; unique clip_id khi CLIP; unique publication_id khi PUBLICATION; approval decision_id unique khi APPROVAL. Những field này có thể xuất hiện ở child snapshot nên uniqueness phải stage-scoped, không global nullable unique nhầm. |
| Hash/version | semantic_hash, presentation_contract_hash, render_input_hash nullable chỉ khi projection chưa available (SUBMISSION/APPROVAL chưa freeze); hash/canonical/projector/classifier version required; classification/state; no synthetic hash claim cho missing data. `request_digest` P3 không copy vào đây. |
| Immutable content | payload JSONB object: copied brief/evidence/edition refs+content digests/statuses/context/tasks/contributions, full original identity map, mode/policy capture, decision/validation refs, renderer/asset/audio manifest, public metadata and label; frozen_input JSONB chỉ RENDER_INPUT; snapshot_content_digest không self-reference; payload bytes bounded theo contract. |
| Stage rules | SUBMISSION có candidate target, không decision giả; APPROVAL có existing decision + optional actual assessment; RENDER_INPUT có exact job/frozen_input + approval ref khi tồn tại; CLIP có clip/job + parent render snapshot hoặc explicit LEGACY unknown provenance; PUBLICATION có publication/clip + parent clip snapshot khi có. Thiếu chain không biến thành MATCH bằng default. |
| Label | verification_status UNVERIFIED hoặc LEGACY_UNMAPPED, không VERIFIED/ACTIVE. Captured effectiveMode OFF/ADVISORY; không mode STRICT row hoặc promotion fixture. Current eligibility computed riêng, không update snapshot label khi OFF. |

Unmapped legacy publication **không cần backfill row**: read fallback trả LEGACY_UNMAPPED. New explicit legacy publication có PUBLICATION snapshot ghi thiếu lineage, acknowledgement/audit và known facts; parent có thể null với `LEGACY_UNVERIFIED` route, không bịa parent/hash. Known mismatch vẫn bị chặn trước insert.

### 2.2. `stem_p5_compatibility` (append-only)

Fields dự kiến: id; approval_snapshot_id; before_snapshot_id; after_snapshot_id; actor_id (server actor provenance); proof_version; semantic_hash/presentation_contract_hash; before_render_input_hash/after_render_input_hash; before/after target tuple/revisions JSONB; proof JSONB bounded; result COMPATIBLE/INCOMPATIBLE/UNKNOWN; created_at. FKs self/core types đúng thực; no approval-copy, không lấy dòng “latest”. Key unique theo source approval + before/after + proof version để retry không nhân record. Negative/UNKNOWN proof được lưu có audit nếu workflow cần; không là human assessment. Snapshot after có thể stage RENDER_INPUT hoặc candidate/submission theo source measured sync, không sửa old approval để gắn measured revision mới.

## 3. FK types và immutability

Read-only source: V1 legacy parents users/workspaces/clip_projects/script_versions/scenes/status_histories dùng varchar36 theo P3 prerequisite. V10 clips.id UUID; render_job_id dựng theo actual job.id. V13 publications.id/clip_id UUID; project/publisher type theo parent. V19 compositions.id UUID trong fresh path, render_jobs.composition_id UUID. **Không áp một kiểu cho tất cả.** Chặng2 migration phải inventory pg_attribute trên disposable schema sau bootstrap, assert exact prerequisites trước DDL. Drift → fail trước create, không IF NOT EXISTS che mismatch. Shared không được truy cập/suy từ fresh.

New tables: PK/FK/index/unique/check stage/versions/digests/body JSON shape; indexes project/version/captured_at, decision/job/clip/publication refs; RLS bật, revoke PUBLIC/anon/authenticated khi role tồn tại, backend-only test role đủ quyền; không client policies. Không đổi grants/RLS old9tables/core. Quyền owner test có thể bypass RLS nên cần client-role negative tests riêng, không gọi owner query PASS là chứng minh RLS.

Snapshot/compatibility UPDATE/DELETE bị reject bởi trigger **chỉ trên hai bảng mới**; insert stage/ref checks, historical FK delete RESTRICT/NO ACTION, không cascade mất review. Không trigger có tác dụng phụ trên core table. Rollback logical OFF không xóa snapshots. New-row dedupe và receipts vẫn commit atomic cùng existing workflow; dispatch afterCommit. Bounds trigger/service phải khớp byte limit UTF-8, không length(chars) giả bytes.

## 4. Số và location trình xác nhận

**Phương án đề nghị:** dải **TEST_ONLY V92000–V92009**, dùng đúng **V92000** cho hai bảng trên; location **`src/test/resources/db/stem-p5/`** trong clone B. Tên file candidate (CHƯA TẠO): `V92000__stem_p5_artifact_snapshots_test_only.sql`.

- Tìm filename read-only dưới repo BE gốc và gói isolated 05/11 không thấy file V92*/92000. Đây **không** reservation trên mọi branch/remote/shared; không fetch/DB check. Chưa có người dùng/Bond cấp số thì vẫn PENDING.
- P5 bootstrap fresh disposable: existing V1–22 + V9000/V9001 từ auth-v2-e2e location **một lần** + existing `db/stem-p3` V91000 + new `db/stem-p5` V92000. Expected **26 migrations** chỉ khi SQL đã được duyệt/viết và chạy thật; hiện **NOT_RUN**, không actual26.
- Chỉ P5 isolated profile thêm new location + feature artifact flag; không production application config, không sửa P3 test config/runner/source/assertions để thêm location. Supporting P5 runner/config output riêng được trình Chặng2, dùng dependencies hiện có; old regression dùng original test source.
- V1–22, V9000/V9001, V91000 giữ bytes/checksum/history; không placeholder23/24, outOfOrder/repair, copy/promote SQL test thành production, rerun auth cutover. V23 auth/V24 roles/V25+ STEM vẫn đề xuất ở ledger C01/C02/BR-01, cloud Flyway OFF.
- Reuse stem_receipts cho operations mới <=30 chars, key scope/TTL24h unchanged; không alter P3 receipt PK để theo cách hiểu target khác. Cross-target cùng scoped key vẫn conflict như P3 contract. P5 snapshot dedupe không phụ thuộc receipt tồn tại vĩnh viễn.

## 5. Gate test sau duyệt — chưa chạy

Fresh disposable bootstrap/validate26 nếu đúng plan; wrong prerequisite type fail/no partial new tables; RLS client denial; stage/ref/check/unique/immutability; decision+snapshot+job rollback; race mode/brief/composition; same/different key replay; clone baseline source migrations/test assertions unchanged; cleanup đúng owned DBs. Không schema/shared rehearsal, không production numbering claim. Restore dump không dùng; synthetic TEST_ONLY DRAFT/UNVERIFIED, no verified positive fixture.

## 6. Phần cần xác nhận riêng rồi mới sang Chặng2

1. **Cấp TEST_ONLY V92000–V92009, dùng V92000 / `src/test/resources/db/stem-p5/`, file candidate nêu trên, 2 sidecar tables; không core/P3 column/schema edits.** Nếu muốn số/location khác, sửa ledger proposal trước SQL.
2. **Callback policy A tại CALLBACK_MANIFEST_PROPOSAL.md**: optional additive manifest server-check trong BE với fake/synthetic fixtures, không worker patch; missing manifest là provenance UNKNOWN/explicit legacy route, không hash echo=MP4 proof. Nếu muốn strict manifest mandatory cho worker thật, cần scope worker riêng, chưa thực thi.
3. Contract additive P5 read status/publish expectation + explicit legacy acknowledgement tại CONTRACT_DELTA_P5.md: không sửa wire P3/G06 hoặc thêm approve endpoint; new limits kỹ thuật là proposal có rationale, cần boundary checks sau gate. Không tự mở P6 library features.

**DỪNG CHẶNG1 tại đây. Chưa viết SQL/code hoặc chạy suite nào; chưa tự đóng P5.**
