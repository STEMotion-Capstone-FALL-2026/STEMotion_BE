# P6 — Mapping 52 ca + AT21, actual subcases

07/11/2026. Không có ý nghĩa 52 rows = 52 tests. PASS_SUBCASE chỉ phần kỹ thuật có evidence: final BE/AI/FE suite và live theo TEST_BASELINE_P6.md. Không nâng chuyên môn/shared/render proof hoặc đóng phase. Tách mỗi ca, không gộp AI01/AI02, L01/L02, F09/F10.

| Ca | Actual P5 kế thừa | Actual P6 và giới hạn |
|---|---|---|
| I01 | DOCUMENT_REVIEW: registry/official reports giữ; preservation originals không drift | DOCUMENT_REVIEW — giữ registry/UC/FR candidate; không sửa Reports/BM/diagram. |
| R01 | PARTIAL: snapshot capture labels/edition refs/digests; DRAFT catalog regression giữ | PARTIAL — frozen editions/source refs và UNVERIFIED đọc thật qua library/AI; nguồn/chuyên môn/currency chưa xác minh. |
| C01 | PASS_SUBCASE regression typed/null/context fields + snapshot copy | PASS_SUBCASE — primary/secondary, condition/adaptation/task trong context và card của fixture lớp4; không mọi biến thể sư phạm. |
| C02 | PASS_SUBCASE old channelCannotBePrimary và FE dropdown regression | PASS_SUBCASE — DEPARTMENT_LIBRARY chỉ channel; primary/secondary filter có role và invalid channel-as-context400. |
| C03 | PASS_SUBCASE draft/null/schema bounds regression | PASS_SUBCASE — draft/null, text/array/depth/byte limits và legacy null context; semantic safety còn PARTIAL. |
| C04 | PASS_SUBCASE raw legacy/suggestion manual CAS giữ; no curriculum backfill | PASS_SUBCASE — legacy thiếu snapshot/curriculum không backfill; raw label không thành lớp/outcome. |
| C05 | PARTIAL references/evidence giữ; no auto expert decision | PARTIAL — task/objective refs giữ theo server brief; không máy tự thẩm định an toàn. |
| G01 | PASS_SUBCASE null/draft legacy old tests; feature default off không đòi schema P5 | PASS_SUBCASE — manual draft/null regression và AI failure không ghi draft; no auto-call AI trên autosave. |
| G02 | PASS_SUBCASE OFF/ADVISORY video approval/publication UNVERIFIED/legacy, no fake assessment | PASS_SUBCASE — OFF/ADVISORY, card/suggestion UNVERIFIED; không tăng scientific readiness. |
| G03 | PASS_SUBCASE pure negative STRICT evaluator regression, setting chỉOFF/ADVISORY | PASS_SUBCASE regression negative evaluator; STRICT workspace/activate NOT_RUN/ngoài scope. |
| G04 | PASS_SUBCASE existing AI-warning evaluator giữ, science missing advisory | PASS_SUBCASE — terms/grade/extract là advice; model claims bị reject và không cấp assessment. Không accuracy/chuyên môn. |
| G05 | PASS_SUBCASE actual HTTP mediaFAIL hardOFF; frozen-input QA + evaluatedAgainst; UIQA tách | PASS_SUBCASE regression frozen QA, giữ P5 thresholds; actual media/pixel/audio vẫn NOT_RUN. |
| G06 | PASS_SUBCASE mode change before VIDEO assessment409 no writes; original2live gồm settings wirebaseRevision/stale409 | PASS_SUBCASE — original2live G06 và regression CAS; settings wire chỉ baseRevision. Shared rollout NOT_RUN. |
| A01 | PASS_SUBCASE real HTTP Adminno-seat403 VIDEO + old SCRIPT regression | PASS_SUBCASE — active WRITER cho grounded, active REVIEWER guards giữ nguyên trong mandatory regression; không Admin bypass assessment. |
| A02 | PASS_SUBCASE original inactive/crossworkspace guards; new status unauthorized401/403 | PASS_SUBCASE — producer403, foreign project/version403, catalog missing/out-of-scope negative; original cross-workspace/seat regression giữ. |
| A03 | PASS_SUBCASE original briefCAS/unknown-field + new publish raw-byte/server-claim400, receipts | PASS_SUBCASE — unknown/root client claims400, missing/negative revisions và raw-byte bound; old brief CAS/atomicity regression. |
| A04 | PASS_SUBCASE real target/hash/input mismatch409; concurrent keyed review one decision/assessment/snapshot/job | PASS_SUBCASE — revision stale trước call, actual mutation trong Python call409, stale accept409/FE giữ input; no approval/save write. |
| V01 | PASS_SUBCASE original copy-map/keyed retry giữ; P5 không copy snapshots/eligibility sang version khác | PASS_SUBCASE mandatory copy-ID/no approval-copy regression; không thêm project clone. |
| V02 | PASS_SUBCASE evidence identity regression + pure ordered-scene semantic hash đổi | PASS_SUBCASE mandatory evidence identity/reorder hash regression; pixel proof NOT_RUN. |
| V03 | PASS_SUBCASE old insert/evidence identity regression | PASS_SUBCASE mandatory insert-ID mapping regression; không auto-apply scene mới/evidence. |
| V04 | PASS_SUBCASE old orphan/explicit retirement; snapshot history append-only | PASS_SUBCASE mandatory orphan/retirement/history giữ; no AI delete evidence. |
| V05 | PASS_SUBCASE old copy rollback/map failures; P5 atomic approve negative/concurrent guards | PASS_SUBCASE mandatory copy/transaction rollback regression; shared rehearsal NOT_RUN. |
| F01 | PASS_SUBCASE pure duration/caption numeric alignment + new same-token alignment; real PG measured→compatibility→CURRENT, semantic edit→NEEDS_REVIEW | PASS_SUBCASE regression static TITLE_HERO/OUTRO safe timing; full render/AT21 PARTIAL. |
| F02 | PASS_SUBCASE shorter duration/caption cutoff không inherit; conditional cases UNKNOWN | PASS_SUBCASE negative cutoff regression; positive safe-decrease dời. |
| F03 | PASS_SUBCASE caption bỏ “không”/unit/minus changes semantic vectors | PASS_SUBCASE token/negative/unit canonical regression; no actual ASR/TTS. |
| F04 | PASS_SUBCASE cue anchor/reveal changes hashed; dynamic cue layouts UNKNOWN no inheritance | PASS_SUBCASE cue/raw/UNKNOWN regression; pixel reveal ordering NOT_RUN. |
| F05 | PASS_SUBCASE motion/geometry raw hashed, unknown blocks no auto-inherit | PASS_SUBCASE motion/reference raw regression; broad motion equivalence dời. |
| F06 | PARTIAL negative/conservative hidden/layout handling + P4 identity guard regression | PARTIAL — no pixel/clipping/visibility proof; P5 conservative boundary giữ. |
| F07 | PARTIAL semantic preserved but conditional theme changes contract/UNKNOWN; default theme numeric proof supported | PARTIAL — broad safe-theme inheritance không được mở. |
| F08 | PASS_SUBCASE unknown raw subtree retained/hash differs/no inheritance; feature classified subset explicit | PASS_SUBCASE unknown raw fallback/guards regression; model authority fields không thành trusted context. |
| F09 | PARTIAL13scene projection-level semantic mutation vectors + existing schema/mapper regression | PASS_SUBCASE mới đủ13 valid DTO qua BE ScenePropsValidator + FE AI scene validator; mutation semantic. Full per-leaf/render acceptance PARTIAL. |
| F10 | PARTIAL12block kinds + extraBlocks semantic mutation/raw preservation; user confirmed count | PASS_SUBCASE mới đủ12 kinds + extraBlocks qua BE/FE, mutation và dangling-reference negative; không block13. Full rendered visibility PARTIAL. |
| F11 | PASS_SUBCASE29golden canonical specimens include object/set invariance, ordered arrays/order, NFC, domain separation; targets/counters separate | PASS_SUBCASE mandatory canonical/versioned golden tests; không đổi thuật toán P5. |
| F12 | PASS_SUBCASE finite/precision/LaTeX/unit/case/-0/duplicate/NFCcollision/surrogate vectors + old limits + raw-byte guard | PASS_SUBCASE bounds/finite/-0/LaTeX regression + context Unicode UTF-16/finite Python parity; no symbolic equivalence. |
| F13 | PARTIAL audio URL without content proof UNKNOWN/no inheritance, URL changes retained in input/contract | PARTIAL — no positive audio resolver/digest/actual audio; UNKNOWN/no inherit giữ. |
| F14 | PARTIAL version-domain separation + all4version checks + old pin/immutable edition regressions + immutable snapshots | PASS_SUBCASE mới 4version mismatch không match/inherit, unchanged history; upgrade bridge/production withdrawal PARTIAL. |
| B01 | PASS_SUBCASE **real HTTP CLIP** review exact current clip, no replacement job; publish path passes | PASS_SUBCASE original CLIP HTTP regression; fake media, no actual MP4. |
| B02 | PASS_SUBCASE **real HTTP COMPOSITION** pre-render review, snapshots/afterCommit job/callback/publish/library | PASS_SUBCASE original COMPOSITION HTTP regression; frozen input/lineage giữ, no worker changes. |
| B03 | PASS_SUBCASE wrong hash/input tamper409 no writes, same/conflicting completion, old-target historical no currentURL; retry new job/nonce sameinputhash, old attempt cannot win | PASS_SUBCASE original callback/retry mismatch regression; independent checksum/MP4 proof NOT_RUN. |
| B04 | PASS_SUBCASE actual HTTP send-back reason works without STEM PASS | PASS_SUBCASE send-back regression; không đòi AI PASS để trả sửa. |
| P01 | PASS_SUBCASE current revision mismatch hard409 evenack; publication snapshot-backed detail/filter after mutable title/grade edit; idempotent publish | PASS_SUBCASE frozen card/filter/facets không đổi khi mutable project/brief đổi; old publish preconditions giữ. Mixed epochs/selection/reconciliation vẫn dời. |
| P02 | PASS_SUBCASE missing-manifest publication requiresack; UNVERIFIED/LEGACY label; history retained; original live OFF/settings audit | PASS_SUBCASE legacy ack/OFF/history regression; card không auto VERIFIED. Shared rollback NOT_RUN. |
| P03 | PASS_SUBCASE real local request YouTube503/PUBLISH_TARGET_UNAVAILABLE, no publication writes | PASS_SUBCASE original YouTube503/no-write; real YouTube/RF08 ngoài scope. |
| AI01 | NOT_RUN/P6: no Java/Python grounding/schema/provider changes | PASS_SUBCASE exact-lock77 + 5operations real Java→Python fake transport, primary DRAFT profile/instruction/context parity, foreign refs/claims/bounds. Provider quality/expert/ba cấp thực tế PARTIAL. |
| AI02 | PASS_SUBCASE original validator/accept boundary regression only | PASS_SUBCASE accept chỉ log, explicit manual save path giữ; stale accept/in-flight409, timeout504, busy429/no repeat, invalid502, grounded invalid generation single-call. Không one-click apply/Create hoặc chuyên môn. |
| L01 | PARTIAL existing subject/grade item/filter snapshot consumption checked | PASS_SUBCASE frozen card/combined DB filters/paging/count/facets + FE real consumption; dashboard/full project reuse dời, production query scale không được chứng minh. |
| L02 | PARTIAL explicit legacy/unverified route and library labels/fallback implemented; currentpublication snapshot detail checked | PASS_SUBCASE legacy read/download/embed và unknown curriculum label; no backfill. Production conversion/broad export-migration acceptance NOT_RUN. |
| M01 | PASS_SUBCASE freshP5schema26 and originalP3schema25 separate | PASS_SUBCASE old test schemas25/26 rerun, no P6 migration; shared equivalence NOT_RUN. |
| M02 | PASS_SUBCASE unchangedfixtures25 + additiveV92000 in separatelocation once, source hashes preserve | PASS_SUBCASE old migrations/lock/package bytes giữ; không V93000 placeholder/reservation hoặc repair/outOfOrder. |
| M03 | PASS_SUBCASE syntheticwrong-parenttype fails new migration before newtables/public change | PASS_SUBCASE old synthetic prerequisite negative giữ; shared drift/preflight vẫn outside. |
| D01 | DOCUMENT_REVIEW: officialWord/BM/reports/diagram/native export untouched | DOCUMENT_REVIEW — official Word/BM/diagram/T12a và IDs không sửa; dependency P7 giữ. |
| E01 | PARTIAL all3briefs/profile/rubric remainDRAFT/UNVERIFIED; no expert feedback invented | PARTIAL — brief4/8/11, source/outcome/profile/rubric DRAFT/UNVERIFIED; không chuyên môn giả/LED/Toán11 decision. |

## AT21 — master namespace, tách riêng

Chỉ rerun static numeric timing subset P5; no extra safe inheritance/classifier change, no actual MP4/pixel/audio/TTS. Full AT21 vẫn PARTIAL. Evidence không cộng lại vào test count F01. AT04/AT17/AT19 technical AI fake-outcome/timeout/manual fallback/library paging tương ứng AI01/AI02/L01, không được cộng trùng assertion thành test mới. Full AT01–24/ba demo chưa nghiệm thu, dời scope P7.
