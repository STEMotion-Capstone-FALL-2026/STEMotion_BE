# Prompt thực thi P5 cho Codex — Nội dung đến artifact (T09)

Bạn là Codex, agent backend của STEMotion STEM track. Thẻ phạm vi P5 đã được người dùng duyệt ngày 05/11/2026 với các lựa chọn dưới đây. Đây là prompt thực thi duy nhất cho P5. Thực thi đúng phạm vi đã duyệt, rồi bàn giao và DỪNG. Không tự mở P6/P7/P8, không tự đóng P0–P5, không merge/push/deploy.

## 1. Quyết định đã duyệt — khóa nguyên văn, không tự thay

- **P5-Q01 B — BE + FE tối thiểu:** BE làm gate/snapshot/publish; FE chỉ thêm hiển thị stale/unknown/publish eligibility/snapshot origin và QA. Không redesign, không Create, không full library cards.
- **P5-Q02 A — 3 projection versioned:** semantic, presentationContract, renderInput theo §2 P5.1 của thẻ, conservative unknown; đổi công thức → NEEDS_REVIEW cho transition mới. Không auto-migrate approval.
- **P5-Q03 A — AT21:** technical team quyết proof schema/tokens/order/visibility; nhận định mang nghĩa/safety đưa Bond/người dùng/chuyên môn quyết, unknown không inherit. Không chọn "mọi timing an toàn".
- **P5-Q04 A — Freeze 4 mốc:** submission capture + approval snapshot + frozen enqueue input + clip/publication snapshots liên kết. Ưu tiên BE binding + fake manifest trước; worker patch nếu cần phải mở phạm vi rõ, không nhận hash echo là MP4-proof.
- **P5-Q05 C — Gate ở cả hai nơi:** FeedbackService và PublishService, với binding tại enqueue/callback. Chặn sai sớm và recheck cuối chống race/direct API.
- **P5-Q06 A (có điều kiện) — Migration TEST_ONLY:** additive test persistence nếu khảo sát cần, dải TEST_ONLY riêng không đụng P3 existing; số/location do người dùng/Bond xác nhận trước khi viết SQL. Xem gate ở §4.
- **P5-Q07 A — Integrity hard gate ở mọi mode:** auth/target/job/artifact integrity + existing media FAIL hard ở mọi mode (OFF/ADVISORY/STRICT đều chặn artifact sai); scientific missing findings chỉ advisory; legacy thiếu lineage đi route Q12 riêng. Không đòi mọi source VERIFIED, không xóa historical label/decision.
- **P5-Q08 A — Review COMPOSITION:** projector/reference/visibility proof cho supported subset + evidence linkage + rubric DRAFT, unknown không inherit. Baseline inventory đã xác nhận: **13 SceneTypes / 12 BlockKinds**.
- **P5-Q09 A — G05 reuse:** giữ tiêu chí/ngưỡng QA hiện hữu, bind QA đúng artifact, hiển thị kết quả nếu FE được duyệt (Q01 B). Không tự đặt ngưỡng mới, không coi FR38 advisory là quyền bỏ existing FAIL.
- **P5-Q10 A — Môi trường:** tái dùng hai clone B+F đã fix/patch, preserve dirty baseline bằng hashes/delta, DB disposable. Không merge, không checkout/pull FE gốc, không đụng user assets.
- **P5-Q11 A — Mức test:** mandatory cases kỹ thuật (A04/V02/F01–F14/B01–B04/P01/P02/P03/G05) + regression 38 P3 + 3 blocker + 216 FE + 2 live P4/G06; real HTTP cả CLIP/COMPOSITION, render/callback synthetic/fake, không MP4 thật. Không nhận proof pixel/audio/MP4; F06/AT21 visual end-to-end vẫn PARTIAL/NOT_RUN tương ứng.
- **P5-Q12 A — Library/publication/legacy:** publication mới immutable snapshot; entry cũ giữ LEGACY_UNMAPPED/read-export theo quyền, không backfill curriculum, không cấp current-STEM eligibility; consumer fields/filter hiện hữu phải dùng snapshot khi có. Lần publish mới thiếu lineage: đi đường explicit unverified khi không có mismatch đã biết; mismatch đã biết vẫn chặn. Không ghi đè snapshot lịch sử khi OFF.
- **Xác nhận kèm theo duyệt:** GAP-01 — `FeedbackService` đã có minimal hook P3, P5 mở rộng hook đó; GAP-05 — inventory **13 SceneTypes / 12 BlockKinds**, F10 giữ PARTIAL cho tới khi Q08 xác nhận (Q08 A đã duyệt inventory này làm baseline).

## 2. Hai đính chính baseline — mang nguyên văn sang thực thi, không viết lại thành mô tả cũ

1. **GAP-01:** `FeedbackService` đã có minimal hook P3 (+13 dòng: optional StemService, submission/prepare/finish). P5 **mở rộng hook có sẵn** (common hook trước hai nhánh CLIP/COMPOSITION), không tạo hook từ đầu. Không được ghi trong code/handoff rằng service này "chưa patch" trước P5.
2. **GAP-05:** Baseline là 13 SceneTypes / 12 BlockKinds. Không bịa block thứ 13 (ví dụ table). Ma trận projector/hash phải đủ 13 scene và 12 block + extraBlocks; field/block chưa phân loại giữ UNKNOWN/NEEDS_REVIEW, không bỏ khỏi hash.

## 3. Môi trường — chụp baseline trước khi chạm code

- BE clone B: `C:/Users/LeTran/FPT University/2026_FALL/STEMotion/.audit/stem-phase-2026-11-05/phase-03/STEMotion_BE`, HEAD `f6c6f0b`, nhánh `feat/stem-p3-isolated-20261105` (P3 + fix blocker A, uncommitted).
- FE clone F: `C:/Users/LeTran/FPT University/2026_FALL/STEMotion/.audit/stem-phase-2026-11-05/phase-04/STEMotion_FE`, HEAD `9fe67cf`, nhánh `feat/stem-p4-isolated-20261105` (patch P4, uncommitted).
- Trước thực thi, chụp HEAD/branch/status/hash/diff **kể cả untracked code** của cả hai clone, phân biệt baseline P3/fix/P4 với delta P5. Không reset/stash/clean để bắt sạch. Nếu lệch baseline/handoff hoặc có thay đổi mới không rõ owner thì dừng phần phụ thuộc.
- Localhost ports trống mới, E2eServer full Spring + embedded PostgreSQL disposable, test profile riêng, `app.stem.enabled` chỉ trong test. Không user server/shared/staging/production, không .env/secret thật, không mail/AI/TTS/Brevo/YouTube thật. Build/cache/log/output P5 ở location mới dưới clone/audit, không overwrite evidence P3/P4. Cleanup chỉ PID/data dir do runner P5 tạo.

## 4. Trình tự thực thi — hai chặng, có gate migration ở giữa

### Chặng 1 — Contract, hash matrix, migration proposal (chưa viết SQL)

1. Viết contract delta P5: field matrix đủ 13 scene/12 block, 3 projection + canonical vectors (UTF-8/NFC, keys sort, ordered arrays giữ order, số hữu hạn deterministic, -0→0, không normalize LaTeX mất nghĩa), SHA-256 có domain/version prefix, error envelope reuse (400 details, 403/404, 409 stale/conflict, 429 Retry-After), idempotency actor+operation+target+key TTL 24h như P3.
2. Khảo sát persistence: additive snapshot/hash/compatibility/binding cần bảng/cột gì, reuse JSON metadata hiện hữu có đủ immutability/atomicity không. Trình **đề xuất số/location migration** (dải TEST_ONLY riêng, không đụng V91000, không V23 production) và DỪNG ở đây để người dùng xác nhận số/location trước khi viết bất kỳ file migration SQL nào. Không viết SQL khi chưa có xác nhận riêng này.
3. Trình bày phương án callback manifest/artifact digest (server hash + worker echo chỉ là correlation, chưa phải MP4 byte-proof) và legacy callback policy, chờ quyết cùng gate migration nếu liên quan worker contract.

### Chặng 2 — Implement sau khi gate migration được xác nhận

1. **P5.1 Hash/freshness:** server tính 3 projection độc lập có version; `request_digest` P3 không tái dùng làm semantic hash; target identity/actor/revision/CAS/mode bind riêng với decision/snapshot; đổi projector/classifier/hash version → NEEDS_REVIEW cho transition mới, giữ hash/decision lịch sử.
2. **P5.2 FeedbackService gates:** mở rộng common hook P3 (authorize/active seat → khóa target/CAS → resolve policy/mode/brief actual → tính projections → decision/assessment/snapshot atomic); submit/approve-video CLIP (B01) và COMPOSITION (B02) tách riêng; send-back giữ quyền/reason/target; semantic edit → assessment cũ không còn áp dụng; measured sync hợp lệ → compatibility link, không bypass CAS/lockedReason. Không thêm STEM approval endpoint riêng.
3. **P5.3 Snapshot/callback/job:** 4 mốc liên kết (submission, approval, frozen enqueue input, clip/publication); snapshot chứa target tuple, 3 hash + algorithm versions, copied data đủ đọc độc lập, validation/assessment/decision refs, effective mode + workspace counter, renderer/input/assets/audio identity; immutable/append-only. Dispatch chỉ frozen bytes sau commit; callback kiểm authenticated integration, exact job/attempt/input binding, terminal/replay, clip identity; old job không tự thành current/publishable.
4. **P5.4 Publish integrity:** recheck độc lập tại PublishService transaction (current clip/job/input/snapshot/decision, quyền/lifecycle, mode/policy, QA đúng artifact); Publication + PublishRecord atomic/unique; publication mới lấy STEM metadata từ snapshot đúng clip, không từ mutable brief/catalog/project/latest record; OFF không xóa/đổi assessment/history/snapshot, publication mới OFF mang UNVERIFIED/LEGACY_UNMAPPED; YouTube giữ 503 PUBLISH_TARGET_UNAVAILABLE, atomic no-write.
5. **P5.5 Projector:** whitelist có version + conservative raw fallback, tái dùng representation renderer đang tiêu thụ; đủ 13 scene/12 block; arbitrary asset semantics/visualPlan/layout/pixel proof không đảm bảo → UNKNOWN/NEEDS_REVIEW. Không viết renderer/FE mới, không đổi enum/block catalog.
6. **P5.6 G05 QA:** tái dùng tiêu chí/ngưỡng hiện hữu, QA chạy trên frozen artifact input; không tự đặt ngưỡng mới.
7. **P5.7 Persistence/contract:** chỉ viết migration sau gate §4; không sửa V1–22/V9000/V9001/V91000/checksum/history, không placeholder/repair/outOfOrder/rerun cutover. Không mặc định áp 256KB cho renderInput — inventory size trước, đặt riêng byte/depth/count/string/rate cho DTO mới.
8. **Q01 B FE tối thiểu:** chỉ DTO/display stale/unknown/publish eligibility/snapshot origin/QA trong các file đã duyệt ở thẻ §3 (types stem.ts, stemClient/reviewService/renderService/compositionService/libraryService, Writer/Reviewer/Producer components, StemReadOnlySummary). Không mở Create, không P6 UI.

## 5. Những thứ tuyệt đối không đổi

- Contract delta P3 và G06 wire `baseRevision`; không rename field wire, không thêm endpoint approve STEM mới, không 422, không hardcode code lỗi mới ở FE trước contract delta.
- 38 test P3 + 3 regression blocker: giữ nguyên source/assertions, phải pass lại; không drop/skip/relabel để suite xanh.
- Không sửa FE/P4 test source/config/runner ngoài phạm vi Q01 B, không chạm assets Remotion/worker, không đổi package/lock/toolchain/renderer architecture.
- Không bật STRICT/activate toàn hệ thống, không nâng brief/rubric/catalog/source/outcome lên VERIFIED/ACTIVE, không cấp verified publication thiếu người xác nhận thật, không liên hệ giáo viên/tạo phản hồi giả, không chốt chuyên môn/LED/Toán 11, không sửa FR-38/Word/BM/diagram/T12a.

## 6. Test — theo Q11 A, tách từng ca

- Unit: golden canonical vectors, field-by-field mutation 13 scene + từng block, unknown fallback, timing safe/unsafe, audio digest/token proof, mode/label eligibility, algorithm upgrade không viết lại lịch sử.
- PostgreSQL transaction/schema test cô lập: decision+assessment+snapshot atomic, CAS race brief/composition/mode, receipts/replay, copy identity/no approval copy, immutable editions, job→clip→publication identity.
- Full Spring context + real localhost HTTP/JWT: tách B01 CLIP và B02 COMPOSITION; submit/approve/send-back/queued-render/callback/publish/library read, Admin no-seat 403; wrong job/hash/revision/replayed callback; race no partial decision/job/publication. DB/AI/TTS/render/mail synthetic/fake, không mock BE thay HTTP thật.
- FE (Q01 B): stale reasons, artifact/publish eligibility, warning khác hard error, snapshot vs current summary, 400/403/404/409/429 giữ input, G06 alias. Vitest/RTL hiện hữu.
- Rerun bắt buộc: 38 P3 + 3 blocker regression + 216 FE + 2 live P4/G06 trên source cuối; không cộng 38 hai lần trong 41; không chạy full 439 BE hoặc 216 FE ngoài B-Q04 A trừ khi gate đã duyệt yêu cầu. Không sửa assertion để hợp thức hóa feature.
- Fake render chứng minh lifecycle/binding bằng manifest tổng hợp, không chứng minh MP4 audio/pixel/renderer. Không MP4 thật, không browser E2E, không AI exact-lock.

## 7. Đầu ra và dừng

Đầu ra trong hai clone, chưa commit:
- Contract delta P5 + field matrix + canonical vectors; patch BE/(FE theo Q01 B) và migration test-only (sau gate §4) với ledger; snapshot/binding model + compatibility examples; bảng FE method↔BE endpoint; mapping từng case/subcase (52 ca + AT21 tách riêng); test baseline/log per suite; source/input/output hash manifest; handoff expected/actual, lỗi/giới hạn, ngoài kế hoạch, cleanup, dependencies.
- Không sửa báo cáo P3/P4/Word chính thức để thay lịch sử; kết quả P5 ghi riêng.

Rollback: chỉ revert chọn lọc delta P5 theo snapshot trước thực thi; giữ nguyên code P3/fix/P4 uncommitted và docs/log baseline; không reset/stash/clean clone/repo gốc; không down/drop/repair schema/history/shared.

**Bàn giao xong thì DỪNG. Không commit/push/deploy/merge, không tự đóng P0–P5, không mở P6.**
