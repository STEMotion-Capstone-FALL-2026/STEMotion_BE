# P5 - Mapping stage 1: 52 cases + AT21

05/11/2026. ALL P5 EXECUTION NOT_RUN. No tests/app/build/DB in stage1. The following planned mapping is inherited from the scope approved by the latest execution prompt; targets are not PASS results. P3/P4 prior evidence remains historical; blocker-fix script/G06 live pass does not cover video artifact flows.

## Mapping §8 P3 / §6 P4 → P5, tách từng ca

Nguồn trigger: P2 CONTRACT_REVIEW_CASES; actual kế thừa: P3/P4 CASE_COVERAGE, cộng evidence fix chỉ cho script/G06 được chỉ rõ. Cột “mục tiêu” là đề xuất **sau duyệt**. Tất cả thực thi P5 hiện NOT_RUN. PASS kỹ thuật/subcase không chứng nhận nội dung hay đóng full ca còn specialist/shared/MP4.

| Ca | Baseline kế thừa / gap | Phần P5 đề nghị kiểm và mục tiêu bàn giao |
|---|---|---|
| I01 | DOCUMENT_REVIEW | Giữ candidate registry; document check, không cấp UC/FR mới. |
| R01 | Source/catalog subcase; chuyên môn thiếu | Snapshot giữ refs/digests/status UNVERIFIED; PASS kỹ thuật, provenance thật vẫn PARTIAL. |
| C01 | PARTIAL variants | Regression typed context khi snapshot; không đóng toàn bộ variant/sư phạm. |
| C02 | Primary/channel subcase đã có | Regression channel không thành primary trong snapshot/consumer. |
| C03 | PARTIAL duplicate/cardinality | Regression draft/null/secondary; full specialist variants vẫn PARTIAL. |
| C04 | Legacy suggestion subcase, human mapping thiếu | Raw legacy không tự map khi snapshot/publish; legacy label đúng. |
| C05 | PARTIAL refs, human safety | Giữ orphan/task refs; conflict chuyên môn vẫn PARTIAL, không tạo PASS giả. |
| G01 | Draft UI/BE subcase; render gap | Regression legacy/null draft, preview/save không STEM completeness gate; artifact claim tách. |
| G02 | OFF/ADVISORY subcase; script live sau fix có evidence | Video workflow thiếu brief findings advisory theo Q07/Q12; publish integrity không biến thành scientific STRICT. |
| G03 | Pure negative STRICT evaluator only | Rerun pure tests nếu affected; workspace STRICT positive live vẫn NOT_RUN/ngoài scope. |
| G04 | AI warning không hard gate subcase | Regression warning vs integrity error; AI grounding P6 NOT_RUN. |
| G05 | QA giữ source; QA display mới P4 NOT_RUN | PASS kỹ thuật guard FAIL mọi mode và đúng frozen input; FE display tùy Q01/Q09. New gate chưa duyệt vẫn NOT_RUN. |
| G06 | P3 CAS + 2 live fix có G06 | Rerun settings alias wire chỉ baseRevision; thêm race mode→video/publish no partial write. Shared rollout vẫn NOT_RUN. |
| A01 | Active-seat subcase/script live403 | Live VIDEO assessment Admin no-seat403 cả hai nhánh; gate publish không Admin bypass. |
| A02 | Inactive/cross-workspace subcase | Live ownership/seat negative trên target/job/snapshot; no leak/write. |
| A03 | Brief CAS/schema subcase | Regression existing DTO; snapshot mutations/revisions không partial write. |
| A04 | PARTIAL revision; hashes thiếu | PASS kỹ thuật stale semantic/presentation + target CAS/race; không tin client hashes. |
| V01 | Copy-map/retry subcase | Regression new version không copy approval/hash eligibility; IDs bind đúng. |
| V02 | Identity pass, reorder semantic thiếu | PASS kỹ thuật reorder đổi semantic trong khi evidence vẫn theo ID. |
| V03 | Insert identity subcase | Regression chèn scene không nhận nhầm evidence/hash target. |
| V04 | Orphan subcase | Regression orphan/retirement không cascade, snapshot history không mất. |
| V05 | Rollback subcase | Regression map lỗi rollback cùng snapshot/hash writes nếu thêm. |
| F01 | AT21 proof NOT_RUN | PASS kỹ thuật safe timing subset: semantic+presentationContract giữ, renderInput đổi, compatibility link; pixel/audio real tùy Q11. |
| F02 | Notice only, hash NOT_RUN | PASS negative duration cắt evidence → stale/UNKNOWN, không preserve assessment tự động. |
| F03 | Notice only | PASS caption token “không”/unit/minus đổi semantic. |
| F04 | Notice only | PASS cue/reveal order đổi contract; không chỉ kiểm duration dương. |
| F05 | Notice only | PASS motion count/draw/slide đổi nghĩa hoặc UNKNOWN, không blanket safe. |
| F06 | ID guard only | PASS negative hidden/clipping/relationship hoặc UNKNOWN; arbitrary pixel proof còn NOT_RUN nếu fake. |
| F07 | Theme tool/ID guard, proof thiếu | PASS safe-classified theme subset; theme mã hóa nghĩa phải stale/UNKNOWN. Không claim mọi theme safe. |
| F08 | Inventory/raw giữ, classifier thiếu | PASS unknown subtree không bị bỏ hash, draft legacy được giữ, auto-inheritance fail closed. |
| F09 | 13 scene inventory, mutation proof thiếu | PASS technical matrix đủ 13 SceneTypes theo actual schema; unsupported fields nêu rõ UNKNOWN. |
| F10 | COMPOSED inventory, “13 block” conflict | Q08 user confirmed **12 block actual** + extraBlocks; count gap resolved. Projector mutation coverage still NOT_RUN in P5 stage1; no invented 13th block. |
| F11 | Canonical invariance NOT_RUN | PASS object-key/set invariance; ordered step/scene reorder đổi; timestamp/revision không semantic. |
| F12 | Limits UI/BE subcase đã có; canonical thiếu | Rerun limits; PASS unit/number/LaTeX/case/dấu không normalize mất nghĩa, reject non-finite; không mở symbolic equivalence parser. |
| F13 | Audio/timing hash NOT_RUN | PASS same digest signed URL vs changed/missing digest; transcript/token/proof missing → UNKNOWN; audio real NOT_RUN nếu fake. |
| F14 | Pin immutable subcase, snapshot thiếu | PASS new edition không rewrite history, transition mới revalidate; production withdrawal/rollout P8 NOT_RUN. |
| B01 | CLIP branch minimal/fake P3; P4 video live NOT_RUN | PASS full-context HTTP **có clip current**, decision đúng clip/input/snapshot, không enqueue thay clip. MP4 thực tùy Q11. |
| B02 | COMPOSITION minimal/fake P3; P4 video live NOT_RUN | PASS full-context HTTP **chưa MP4**, decision/snapshot/job/afterCommit atomic, fake completion→publish lineage. |
| B03 | Callback/input proof NOT_RUN | PASS wrong job/attempt/input, late/duplicate/conflicting completion, retry frozen input, no current-artifact confusion. Attestation limit ghi riêng. |
| B04 | Send-back UI/regression subcase | PASS real local HTTP reason/permission khi STEM findings fail; không bật STRICT workspace để test. |
| P01 | Publication gate NOT_RUN | PASS clip cũ/brief hoặc composition mới → đúng conflict/eligibility, snapshot không lấy mutable metadata, race atomic. |
| P02 | OFF history subset, publication thiếu | PASS OFF không xóa/certify; new publication label đúng, historical snapshot immutable; display rollback Q07/Q12, shared rollout vẫn NOT_RUN. |
| P03 | YouTube503 DOCUMENT_REVIEW | PASS regression local request returns503/PUBLISH_TARGET_UNAVAILABLE, no publication/record/status writes; real YouTube ngoài scope. |
| AI01 | NOT_RUN/P6 | Không đổi Java/Python grounding/exact-lock. Regression core nếu affected, không nhận full PASS. |
| AI02 | Accept boundary PARTIAL/P6 | Không mở AI/Create flow; manual validator regression không thay AI acceptance proof. |
| L01 | Library filters NOT_RUN/P6 | P5 chỉ snapshot consumption subset theo Q12; full new filter/card ca vẫn PARTIAL/NOT_RUN P6. |
| L02 | Legacy publication NOT_RUN/P6 | P5 test legacy read/export compatibility subset nếu Q12 duyệt; không giả backfill curriculum. Full browse acceptance P6. |
| M01 | Fresh test PASS subset/shared thiếu | Nếu migration P5 được duyệt, fresh test-only bootstrap; shared equivalence vẫn NOT_RUN. |
| M02 | P3 25 migrations subcase | Regression fixture một lần + ledger location mới; không cấp23/24 dưới9001/91000, không repair/outOfOrder. |
| M03 | Synthetic wrong-type subcase/shared thiếu | Test disposable drift fail/no partial DDL nếu scope migration; shared preflight vẫn NOT_RUN. |
| D01 | Native export chưa xác nhận | Không sửa diagram/report/T12a; DOCUMENT_REVIEW dependency P7. |
| E01 | Ba cấp PARTIAL/chuyên môn thiếu | Assert no auto promotion/verified label; specialist approval/source/LED/Math11 vẫn PARTIAL/external. |

**AT21 (namespace master, riêng với P2.F01):** đề nghị gate kỹ thuật riêng gồm safe sync + unsafe clipping/caption/cue/arrow đối chứng, cùng target CAS và artifact QA mới. Chỉ ghi PASS_SUBCASE khi có proof đúng supported subset; không dùng một test duration tăng để gọi toàn AT21 hoặc mọi renderer PASS. Không cộng AT21 vào 52 ca P2 hoặc vào test count nếu đó là cùng evidence.


Stage2 mandatory regression: original 38 P3 + 3 blocker sources/assertions unchanged, 216 FE and 2 live P4/G06; run on final source only after migration gate. Prompt Q11 explicitly requires 216 FE, so prior B-Q04 A restriction from the blocker-fix package is not interpreted as cancelling this new P5 requirement. Full439 BE is not automatically authorized/required. F06/AT21 pixel/audio end-to-end NOT_RUN under Q11 A fake render.
