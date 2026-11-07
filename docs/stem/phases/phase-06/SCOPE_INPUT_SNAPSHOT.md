# P6 — AI / library / exact-lock (T10/T11): thẻ phạm vi nháp

**06/11/2026, Asia/Saigon — SCOPE ĐÃ ĐƯỢC NGƯỜI DÙNG DUYỆT CÓ ĐIỀU KIỆN / CHƯA THỰC THI P6.**

**Cập nhật theo lệnh duyệt:** Q01 A, Q02 A, Q03 A, Q04 A, Q05 A, Q06 A, Q07 A, Q08 A, Q09 A, Q10 A. Người dùng yêu cầu bổ sung hash input §9 sau mốc 18:03 rồi **Bond kiểm lại trước khi soạn prompt thực thi**. Bảng §9 đã bổ sung; **Bond review còn chờ**, chưa có prompt thực thi hoặc quyền mở P6 implementation. Các phương án và trạng thái UNSELECTED trong nội dung nháp ban đầu bên dưới được giữ làm lịch sử; lựa chọn A của cả 10 câu tại cập nhật này là quyết định hiện hành.

Đầu ra của lượt này chỉ là thẻ nháp để Bond review rồi người dùng quyết định. Các ưu tiên dưới đây đều là đề xuất; **P6-Q01–Q10 đều UNSELECTED**. Duyệt nội dung thẻ không thay prompt thực thi riêng. Không code, SQL/migration, cài dependency, test/build/app, commit/push/merge/deploy hoặc mở P7/P8 trong lượt soạn. Không sửa input, hai clone hoặc hồ sơ chính thức.

## 1. Mục tiêu, thứ tự áp dụng và đường dẫn

P6 theo execution plan §3/§4 gồm T10: AI theo brief/profile tiểu học ở cả Java và Python; T11: thẻ học liệu, lọc/phân trang thư viện, dashboard và legacy. Mục tiêu đề xuất: AI gợi ý dựa đúng dữ liệu server được phép đọc, kết quả vẫn là draft không tin cậy; thư viện giúp giáo viên biết clip dành cho ai, học sinh làm gì tiếp và hoạt động nào nằm ngoài MP4. Không thay pipeline Writer → Reviewer → Producer → Reviewer → Publish.

Thứ tự: yêu cầu hiện tại và quyết định người dùng đã duyệt → execution plan → contract/source thực tế P3/fix/P4/P5 → master Nemo để đối chiếu định hướng. Master không phải thiết kế cuối đã duyệt. Handoff ghi trạng thái tại thời điểm bàn giao, không tự cập nhật theo commit sao lưu về sau.

| Ký hiệu | Root tuyệt đối / quyền trong lượt soạn |
|---|---|
| R | `C:/Users/LeTran/FPT University/2026_FALL/STEMotion/STEMotion_BE` — chỉ đọc tài liệu kế hoạch/P0/P2/P3 |
| B | `C:/Users/LeTran/FPT University/2026_FALL/STEMotion/.audit/stem-phase-2026-11-05/phase-03/STEMotion_BE` — clone BE hiện có, chỉ đọc |
| F | `C:/Users/LeTran/FPT University/2026_FALL/STEMotion/.audit/stem-phase-2026-11-05/phase-04/STEMotion_FE` — clone FE hiện có, chỉ đọc |
| A0 | `C:/Users/LeTran/FPT University/2026_FALL/STEMotion/.audit/stem-phase-2026-11-04/phase-00` — evidence P0, chỉ đọc |
| BK | `C:/Users/LeTran/FPT University/2026_FALL/STEMotion/.audit/stem-branch-backup-2026-11-06` — handoff sao lưu/incident, chỉ đọc |
| D | `C:/Users/LeTran/FPT University/2026_FALL/STEMotion/.audit/stem-phase-2026-11-05/phase-06` — thư mục riêng kế tiếp phase-05; chỉ viết `PHASE_06_SCOPE_DRAFT.md` này |

Các file tương đối bên dưới thuộc root đã chỉ rõ, là ứng viên cho prompt thực thi sau; chưa là quyền sửa. Không đặt đầu ra vào repo gốc hoặc thư mục bàn giao phase-03/04/05 của Codex/Antigravity.

## 2. Baseline kế thừa và đối chiếu source

### 2.1. Trạng thái phải giữ

| Hạng mục | Actual kế thừa / giới hạn |
|---|---|
| Phase | **P0/P1/P2/P3/P4/P5 PARTIAL**, chưa phase nào tự đóng hoặc được coi là merge vào main. P6 implementation NOT_RUN. |
| BE sao lưu | HEAD read-only `4fd7089fa3b2c0851487625517f6fdfe2a9cc64c`, nhánh `feat/stem-p3-isolated-20261105`; parent `f6c6f0bdcdcc6bbaf1f1246f86678d3249dea8e1`. Một commit sao lưu 141 file: P3 + fix A + P5. Không checkout về base. |
| FE sao lưu | HEAD read-only `4dfa65e210ba7bef38fd418943dd19e491743883`, nhánh `feat/stem-p4-isolated-20261105`; parent `9fe67cf7801b3e971b3c852c80f68214fd13bba7`. Một commit sao lưu 79 file: P4 + P5. |
| Working tree | B còn untracked `.build-p3/`; F còn untracked `docs/stem/phases/phase-04/takeover-codex/build/`. Đây là output đã loại khi sao lưu, không code mới cần dọn. Không gọi repo sạch hoàn toàn hoặc tự stash/reset/clean. |
| GitHub / main | BK ghi đã push hai nhánh lên org `STEMotion-Capstone-FALL-2026`, ls-remote khớp SHA; main/develop và working tree repo gốc không đổi ở lượt backup. Origin clone vẫn là repo local gốc, không GitHub. Lượt soạn này không fetch/pull hoặc kiểm remote/live lại. |
| P5 bàn giao 06/11 | **268 BE pass = 113 P5 mới + 41 bắt buộc + 114 regression; 227 FE pass = 216 cũ + 11 mới; 2 live P4/G06 pass**. Không chạy lại trong lượt này, không cộng lượt cũ vào P6. Delta P5 **17 BE / 9 FE**; khác tổng file commit backup. |
| Persistence | P3 TEST_ONLY V91000; P5 TEST_ONLY V92000 tại `src/test/resources/db/stem-p5/`, hai sidecar `stem_p5_snapshots`, `stem_p5_compatibility`. Context P5 có 26 migrations, P3 cũ 25; không SQL production. |
| P0 AI | 69 contract pass trong env sẵn có lệch lock; exact-lock **NOT_RUN**; **41/77** dependency lệch/thiếu. Đây là blocker validation AI P6, không kết luận provider/runtime đã được xác thực. |
| Contract | Tiêu thụ actual P5, giữ DTO/gate P3. G06 settings chỉ wire **`baseRevision`**, UI alias `modeRevision` cho **cùng counter workspace**; không counter riêng, không rename BE. |
| Inventory / hook | **13 SceneTypes / 12 BlockKinds**. FeedbackService đã có minimal hook P3; P5 mở rộng hook đó cho cả CLIP và COMPOSITION (GAP-01), không phải service chưa patch. |
| Mode / nội dung | OFF/ADVISORY; chưa STRICT, chưa activate. Primary learning context chỉ `IN_CLASS_SCREEN`, `GROUP_PRACTICE`; `DEPARTMENT_LIBRARY` là channel. Ba brief lớp 4/8/11, source/outcome/profile/rubric vẫn DRAFT/UNVERIFIED. Không chốt LED/Toán 11. |
| Shared | C01/C02/BR-01 còn mở, cloud Flyway OFF theo ledger cũ. V23/V24/V25+ vẫn đề xuất, không dùng fixture làm production hoặc chạy lại cutover. |
| BM04 | RF-09/10: **17/11/2026**; RF-11/12/13: **24/11/2026**; RF-08 YouTube: **29/11/2026**. Ngày trong hồ sơ, không tự gia hạn; YouTube vẫn ngoài P6. |
| GitGuardian | Báo 11 incident sau backup. **1 incident đã xác nhận false positive: SHA-256 AuthService.java**. Theo thông tin người dùng cung cấp trong prompt P6, **10 incident còn lại người dùng tự kiểm tra ngày 06/11; Bond chưa thấy kết quả chi tiết**. Không tự kết luận cả 11 false positive, không resolve/dismiss, không mở lại nhiệm vụ security trong P6. |

Các dòng UNCOMMITTED/base cũ trong P5_HANDOFF/CONTRACT_ACTUAL là lịch sử trước sao lưu; BK bổ sung trạng thái commit mới, không sửa lịch sử đó. Report 4 BE `7f207bb`, main lúc P0 `f6c6f0b`, live BE quan sát P0 `f6c6f0b` vẫn là ba mốc riêng; hai commit backup không phải production live.

### 2.2. Những gì source đã có và chưa có

| Bằng chứng tại B/F | Kết luận read-only cho scope P6 |
|---|---|
| B `AiDtos.GenerateScriptRequest`, `ApproachRequest`, `ScriptActionRequest` | Có topic/subject/grade/text, **chưa có target project/version/brief để server giải grounding**, chưa có stemContext. Không nói AI đang grounded. |
| B `AiController` | Existing `/api/v1/ai/script/generate`, `/approaches`, `/action`; `PUT /ai/requests/{requestId}/decision` chỉ ghi accept/ignore owned suggestion, **không save script**. Không tạo approve AI endpoint mới. |
| B `AiScriptService` / `AiGateway` | Generate/action có nhánh embedded Java và remote Python; có audit/requestId, lỗi provider và usage. Instruction Java hiện hướng secondary/high school. Không có nhánh profile tiểu học grounded đã xác nhận. |
| B `services/ai/app/models.py`, `prompting.py`, `prompts/system.txt` | `ScriptInput`/`AnalysisInput` dùng `extra="forbid"`; prompt dùng dữ liệu request, system cũng secondary/high school. Thêm field chỉ ở Java sẽ phá remote contract. |
| B `AiGateway.remoteFailure` | Đã map upstream 422 → public 400 AI_INVALID_INPUT, cùng 429/502/503/504. Không copy quy tắc STEM “không422” thành tuyên bố nội bộ Python không có422; API BE vẫn giữ envelope/status hiện hữu. |
| B `LibraryController`, `PagedResponse` | List/detail/filters đã có. List nhận subject/gradeLevel/page/size; array body và X-Total-Count/X-Total-Pages/X-Page/X-Size khi paging, no paging trả array legacy. Không thiết kế endpoint phân trang từ số 0. |
| B `LibraryService` | P5 snapshot path gọi `snapshotItems`, lấy tất cả publication rồi filter/page trong Java. Nhánh không có StemArtifactService dùng repository query khác. Vì vậy chưa có DB-side STEM filters/paging trên snapshot; comment cũ trong service không thay bằng chứng code branch. |
| B `LibraryDtos`, `StemArtifactService.publicationMetadata` | P5 đã trả title/subject/grade/duration/count/owner/workspace, verificationStatus/snapshotId; **chưa trả đầy đủ objectives/tasks/context/source cho learning card**. Frozen payload có brief/evidence/editions để khảo sát projection, không đọc current brief dán lên clip cũ. |
| F `libraryService.ts`, `MediaLibraryView.tsx` | Service hiện subject/grade, chưa truyền page/size hoặc đọc tổng trang. View dùng subject và local search, không đủ bộ lọc STEM/card mới. |
| F `scriptService.ts`, `WriterStudio.tsx` | Có AI generate/action/approach/decision và kiểm scene schema; UI nêu accept không tự đổi script. Không suy “bấm Accept đã persist grounded brief” từ thẻ P4. Cần phân biệt decision log với explicit draft save/submit. |

### 2.3. Mơ hồ / xung đột không tự giải

| ID | Chênh lệch | Cách trình quyết |
|---|---|---|
| P6-GAP-01 | Master nói AI từ catalog “đã kiểm”, nhưng cả ba mẫu/catalog đang DRAFT/UNVERIFIED. | Cho fake grounding từ dữ liệu DRAFT có nhãn; không dùng để tuyên bố curricular validation. Nguồn VERIFIED thật là dependency chuyên môn, Q10. |
| P6-GAP-02 | Master đề xuất AI nhận `briefId`; DTO hiện không có target/ownership binding. | Trình envelope refs/revisions do server resolve, không tin client stemContext; khóa exact contract trước code, Q02/Q09. |
| P6-GAP-03 | T11 gồm dashboard; P5 chỉ consumer metadata tối thiểu, chưa learning card. | Q04 chọn card/filter và có nhận dashboard hay dời backlog riêng. Nếu dời dashboard, T11 vẫn PARTIAL; không ngầm gán P7 nhiệm vụ code. |
| P6-GAP-04 | Master “mọi style/timing không đổi hash”, map order_no, enum context rộng và ID UC/FR cũ khác actual đã chốt. | Giữ P3 ID map, P5 conservative proof, hai primary enum, registry hiện hữu. Không triển khai lại các đề xuất master bị thay thế. |
| P6-GAP-05 | T10 chỉ nói “AI theo brief, profile tiểu học”; không chỉ rõ operation, write path, limits/JSON/error/CAS. | Q02/Q08/Q09 phải chọn operation và contract-stage gate. Không mở toàn bộ storyboard/metaphor/alignment/provider rewrite vì chữ AI. |
| P6-GAP-06 | Exact-lock là nợ P0 nhưng T10/T11 không có task/số thời gian chuẩn bị venv riêng. | Q01 quyết P6.0; không dùng ước lượng 2–4/2–3 ngày trong master để cam kết lịch gồm exact-lock/provider/MP4. |
| P6-GAP-07 | “Tái dùng” có thể là đọc/xuất thẻ hoặc clone toàn project với ID/evidence mới. | Ưu tiên đọc/xuất/link học liệu; project clone/Create vẫn ngoài scope. Muốn copy draft cần route hiện hữu + mapping/CAS được duyệt riêng. |

## 3. Phạm vi P6.1–P6.8 đề xuất

Tất cả kết quả P6 dưới đây là **mục tiêu sau duyệt, hiện NOT_RUN**. Ưu tiên trong thẻ không tự chọn các câu hỏi §8.

### P6.1 — Exact-lock, đề xuất cổng P6.0

- Input: lock tại **B/services/ai/requirements.lock**, requirements/acceptance hiện hữu, Python theo source và evidence P0 41/77. Trước thực thi so hash lock B với lock P0; nếu khác phải inventory lại, không giữ 41/77 như số đo hôm nay.
- Đề nghị tạo venv/cache/output **mới riêng cho P6** sau prompt thực thi cho phép; không tái dùng hoặc sửa `video-quality-venv`, Python global hoặc venv người dùng. Cài đúng phiên bản trong lock, không upgrade/downgrade lock để né resolver. Dependency thiếu wheel/không hỗ trợ platform/network thì PARTIAL + blocker, trình hướng riêng; không tự đổi OS/runtime hoặc bổ sung package ngoài lock.
- Tiêu chí gate đề nghị: xác nhận interpreter/version/OS; đủ 77 mục pinned đúng version, **0 missing/mismatch**; phân biệt package bootstrap/ngoài lock; dependency consistency check không lỗi; log nguồn cài/cache và hash lock. Runtime smoke/import và toàn bộ suite AI hiện hữu chạy trong venv đó, fake provider/network guard, kết quả thực không fail/error; mọi skip giải thích. 69 là số baseline cũ, không phải số test bắt buộc phải khớp bằng cách bỏ test mới.
- Gate tách **dependency equivalence** và **AI suites**: lock đạt nhưng suite lỗi chưa được gọi validation AI đạt; suite đạt trên env lệch lock cũng chưa đạt exact-lock. Trình phương án sửa lỗi ngoài phạm vi thay vì che bằng mock module để import pass.
- Nếu gate chưa đạt: chặn claim Java↔Python runtime parity, AI01 remote end-to-end, AI02 acceptance qua remote và provider validation. Có thể soạn contract/read source và, nếu Q01 chọn song song, làm library/Java fake độc lập; ghi kết quả đúng env, không đóng AI01/AI02 hoặc P0. Q01 dời exact-lock thì AI validation vẫn BLOCKED/PARTIAL.

### P6.2 — AI grounding / suggestions / validation

Đề nghị Q02 chọn **generate + analysis action hiện hữu trong Writer trên project/draft đã có brief**, hai runtime Java/Python cùng contract; không mở Create flow. Approaches/storyboard/metaphor/Whisper/TTS không mặc định nhận chỉ vì có code hiện hữu. Phạm vi operation cuối cần prompt thực thi nêu rõ; các operation chưa chọn vẫn giữ legacy behavior và không quảng bá grounded.

| Thành phần | Thiết kế đề xuất cần review, chưa triển khai |
|---|---|
| Input public BE | Additive target refs project/scriptVersion/brief và expected revisions trên operation được chọn; client không được gửi trusted source status, policy/rule body hoặc grounding blob. Actor từ principal. Request không có target đi route legacy rõ nhãn “chưa grounded”, hoặc từ chối trên operation grounded theo lựa chọn contract; không âm thầm lấy latest global brief. |
| Server context | Authorize project/workspace/Writer role → load đúng draft/brief pin/catalog editions → resolve grade/subjects/outcomes/context/objectiveKey/activityKey/safety/coverage từ server. Phát hiện source/outcome ngoài scope/không tồn tại, DRAFT/UNVERIFIED hiển thị rõ. Không nhận isValidStemTopic/PASS/VERIFIED do AI làm authority. |
| Prompt parity | Cùng context schema/version và specimen cho embedded Java + remote Python; thống nhất missing/null/unknown/limits, cập nhật Python extra=forbid có chủ đích. Nội dung user/source trích dẫn là dữ liệu không phải lệnh; không cho prompt injection đổi policy/activate hoặc gọi URL từ model. |
| Tiểu học | Profile DRAFT theo grade đã resolve: ngôn ngữ vừa sức, nhiệm vụ quan sát/đo đơn giản, vật liệu/rủi ro/giám sát được khai báo; không dùng công thức/quy tắc an toàn do Codex tự coi là chuẩn Bộ. Các mẫu 4/8/11 chỉ test plumbing/labels, không acceptance sư phạm. |
| Output | Giữ scene result tương thích + envelope gợi ý có refs objectiveKey/activityKey và cảnh báo/uncertainty, requestId/context binding/version; **không tạo nguồn hay outcome mới có trạng thái verified**, không trả review decision có hiệu lực. Không tạo fingerprint trusted từ client/model. Exact schema/nullability/allowlist là gate contract trước code. |
| Validation | Deterministic structure/ref/scope/CAS dùng validator BE hiện hữu cho manual lẫn AI trước persistence/submit; AI terms/grade/extract là gợi ý/advisory tách khỏi quyết định STEM và media QA. Không gọi AI trên autosave hoặc bắt AI PASS để manual save. Không ghi “AI xác minh chương trình/khoa học”. |
| Accept / save | Giữ `PUT /ai/requests/{id}/decision` là quyết định accept/ignore suggestion. Chỉ explicit lưu draft qua write path hiện hữu mới đổi dữ liệu, cùng quyền/revision/schema/evidence checks; không auto-apply sau response. Nếu target đổi khi provider đang chạy: response stale hoặc apply409 giữ input, không dùng context cũ để save lên bản mới. Thời gian gọi provider nằm ngoài transaction/lock DB. |
| Failure / fallback | Key thiếu, timeout, 429, malformed schema hoặc Java/Python mismatch phải có lỗi rõ, không mất draft; manual write/review vẫn dùng được. Retry thủ công có kiểm request/audit, không autosave retry/provider fan-out phát sinh chi phí. |
| Provider / key | Q03 quyết fake/offline, thật hoặc hybrid. **Codex không tạo/cấp API key; người dùng cấp riêng** nếu có lệnh gọi thật. Người dùng/owner được chỉ định quyết provider/model, quota, tài khoản chi trả và ngân sách. Không mặc định key đã có, không tìm key trong env người dùng, không ghi key/prompt nhạy cảm vào evidence. |

Ưu tiên fake/offline với real BE↔localhost Python route và model fake ở ranh giới provider, không mock thay BE server; test contract không bằng chất lượng model thật. Provider thật chỉ sau key/budget/operation/data scope được duyệt tường minh, exact-lock đạt và có lệnh thực thi cho phép. Không gọi provider trong lượt soạn.

### P6.3 — Thư viện, learning card và legacy

Ưu tiên Q04-A: **learning card + combined filters + paging DB-side + read/export hiện hữu**; dashboard summary hoặc project-copy không mặc định đi kèm. Nếu người dùng muốn đủ T11 dashboard, chọn Q04-B và ghi exact DTO/view read path cho project draft vs publication, tránh trộn mutable current data với metadata đã công bố.

- Card đề xuất: cấp/lớp, môn chính/phối hợp, outcome, primary/secondary context có role, channel, mục tiêu, việc học sinh làm sau xem, hoạt động trong MP4/ngoài MP4 (`coverage_scope`), điều kiện/an toàn/giới hạn, source/edition và provenance review. Hiển thị “chưa được thẩm định” khi UNVERIFIED; tên Reviewer/decision kỹ thuật không thành chuyên gia xác nhận. Chỉ expose field công bố phù hợp; không trả toàn bộ snapshot/audit actor nội bộ.
- Metadata lấy **snapshot PUBLICATION gắn đúng publication/clip**, cùng phiên bản card; API list/detail/export/filter/count phải nhất quán. Không đọc current brief/catalog/project để lấp trường lịch sử thiếu. Payload P5 đã đóng băng nhưng `publicationMetadata()` mới expose tập nhỏ; cần projection đọc thêm, không UPDATE snapshot cũ để thêm field.
- Filters đề xuất: educationLevel, grade, subjectId/outcomeId theo edition, usageContext có primary/secondary semantics, channel riêng; giữ subject/gradeLevel cũ và tương thích alias được đặc tả. Mọi giá trị multi-filter áp cùng tập publication trước paging/count, không FE lọc page rồi tính tổng. Không đưa DEPARTMENT_LIBRARY vào primary enum.
- Query đề nghị đọc JSON frozen payload trong sidecar/PUBLICATION bằng server query có tham số, JOIN/EXISTS refs không nhân đôi row; cùng predicate cho data/count/facets, stable sort + unique tie-breaker. Không tải toàn bộ thư viện vào Java rồi subList hoặc N+1 đọc snapshot từng card. Khảo sát plan trên DB test sau duyệt; chưa hứa hiệu năng production/index sẵn có. Không tăng quyền xem hoặc đổi scope thư viện hiện hữu khi thêm query.
- Paging giữ array body + headers hiện hữu và bounded PageParams; FE đọc metadata phân trang bằng adapter hiện hữu hoặc additive helper đã review. Empty/last page/combined filters/sort/publishedAt tie/null/bad filter có contract rõ trước code. Không tự thay toàn bộ API bằng envelope mới.
- `LEGACY_UNMAPPED`/missing snapshot vẫn browse/detail/export theo guard cũ, nhãn rõ, không suy grade từ “Đại chúng/Cơ bản/Nâng cao/EdTech”, không tự gán outcome/context. Missing curriculum fields là unknown, không match filter có giá trị cụ thể; có thể chọn bộ lọc legacy riêng. Không ép legacy clip có đủ STEM để tải/xem. Mismatch artifact đã biết vẫn không được acknowledgement bypass ở publish.
- Tái dùng ưu tiên mở/xuất/link học liệu có provenance; nếu copy một phần vào draft có sẵn phải quyết explicit action/ownership/CAS/new IDs/evidence, không copy approval/VERIFIED. Full project clone/Create/backfill lịch sử dời phạm vi riêng, không ghi đã có tính năng đó.

### P6.4 — Từng gap PARTIAL P5: nhận hay dời đề xuất

Q05 quyết bảng này; chữ “nhận” chỉ nói phạm vi ứng viên, không chuyển trạng thái thực đạt. Dời không đồng nghĩa tự giao P7 code/render; cần scope riêng hoặc P8 nếu đúng shared/release.

| Gap P5 riêng | Đề xuất P6 | Bằng chứng cần có để nhận phần đó là đạt / phần vẫn mở |
|---|---|---|
| MP4 proof thực tế | Dời | Muốn nhận cần exact renderer/worker/input/output bytes, local fixture hợp lệ, media observations và provenance; fake callback không đủ. Hiện NOT_RUN. |
| Pixel/visibility proof F06 | Dời | Cần render-frame/clipping/overlap/minus visibility đối chứng trên version xác định; không inference từ hash. PARTIAL giữ. |
| Actual audio proof | Dời | Cần decoded local audio/transcript/token/coverage check có oracle; không TTS thật hoặc lời model thay proof. |
| F09 — đủ 13 template valid DTO | Nhận **contract/schema specimens và negative unsupported handling** nếu Q05 duyệt | Mỗi SceneType có DTO hợp lệ qua BE/FE validators, semantic mutation đổi hash hoặc UNKNOWN đúng contract. Không đóng full F09 validDTO-render/pixel acceptance nếu không render. |
| F10 — đủ 12 block + extraBlocks | Nhận **contract/reference specimens** nếu duyệt | Tách từng kind/extraBlocks, refs/cue/unknown và invalid cases. Không gọi 12 simple mutation fixtures là full rendered visibility acceptance. Full F10 PARTIAL. |
| Arbitrary geometry proof | Dời | Cần classifier/geometry/renderer proof review riêng; input hiện UNKNOWN/no auto-inheritance. |
| Arbitrary cue / reveal proof | Dời | Cần ordering/visibility oracle, không chỉ duration hợp lệ. |
| Arbitrary motion / relation proof | Dời | Cần chứng minh mũi tên/quan hệ/count/draw giữ nghĩa; không blanket decorative. |
| Arbitrary asset identity/meaning proof | Dời | Cần local bytes identity + mapping ý nghĩa; không fetch arbitrary URL hoặc tin caption AI. |
| Arbitrary audio semantic proof | Dời | Digest bằng nhau chưa chứng minh nội dung phù hợp chương trình; không cấp science approval. |
| Broad safe-theme inheritance F07 | Dời | Cần whitelist + visibility/meaning equivalence evidence trước mở coverage. P5 static subset giữ nguyên. |
| Safe-decrease timing inheritance F02 | Dời | Cần proof không cutoff nội dung/label/audio; không mở rộng từ safe-increase của P5. |
| Independent artifact checksum / real renderer B03 | Dời | Chỉ checksum server đo bytes artifact tin cậy mới là bằng chứng bytes, vẫn khác pixel/audio correctness. Optional callback assertion giữ correlation/UNKNOWN; worker không tự được mở. |
| Positive audio digest resolver F13 | Dời | Cần resolver nguồn tin cậy, signed URL refresh same-content/different-content cases và giới hạn I/O; chưa có nên không inherit. |
| Hash-version upgrade F14 | Nhận regression **không auto-inherit khi version khác**, dời positive migration bridge | Evidence mismatch-version→review/UNKNOWN, history giữ; không update captured hash hoặc auto-migrate approval. |
| Production retirement / mixed feature epochs / reconciliation F14/P01/P02 | Dời shared/rollout plan riêng P8 | Cần owner, eligibility/withdrawal policy, rehearsal/rollback, target approval. P6 không tự sửa dữ liệu production. |

Không đổi thuật toán/classifier/version P5 để đáp ứng AI/library. Nếu phát hiện bug thuộc P5, dừng phần phụ thuộc, trình defect/scope sửa riêng; không gộp vào “hoàn thiện P6” hoặc chỉnh test cũ cho xanh.

### P6.5 — Contract / migration

Ưu tiên Q09-A: **không migration mới**, tận dụng snapshot P5/brief/edition hiện có và thêm read projection/query có kiểm quyền. Đây là phương án cần khảo sát, không khẳng định schema hiện tại chắc chắn đủ/index tốt. Nếu DB query buộc thêm index/table/projection thì trình schema/DDL proposal riêng trước code SQL phụ thuộc.

Nếu Q09-B chọn migration: chỉ additive TEST_ONLY, đề nghị dải **V93000–V93009**, location mới `B/src/test/resources/db/stem-p6/`, số/file cụ thể do Bond review và **người dùng xác nhận ledger trước tạo SQL**. Chưa reserve số, chưa tạo placeholder V93000. Không sửa V1–V22/V9000/V9001/V91000/V92000/checksums/history hoặc dùng V23 production; không sửa snapshot UPDATE/DELETE protection/RLS để query tiện hơn. RLS/backend-only/FK thực tế/feature-off startup phải kiểm trên DB disposable nếu được duyệt.

Contract-stage đề nghị phải review trước implementation phụ thuộc:

1. AI target schema/version, required/optional/null/legacy route, exact actors/ownership/limits và response suggestion refs/provenance. Giữ constructor compatibility hoặc ghi migration contract rõ; đồng bộ extra=forbid Python và generated/checked contract specimens Java/Python/FE. Đặt finite limits có số từ source hoặc trình rõ số mới, không tự gọi là chuẩn giáo dục.
2. Request/response stale/CAS khi context đổi; accept decision log khác persist draft; không trao model quyền đổi brief/source/status. Gọi AI ngoài transaction; recheck lúc apply/save.
3. Library field matrix: frozen path → public DTO → filter/count → FE card/export, missing/legacy semantics, authorization, stable sorting, alias old params, headers và limits. Current draft dashboard (nếu chọn) có origin riêng, không alias publication snapshot.
4. Không tin client/AI source VERIFIED/PASS, không thêm policy activate hoặc public snapshot/nonce mutation endpoint. Error envelope BE 400/details,403/404/409/429 theo actual; provider mappings 502/503/504 giữ, retry chỉ khi được đặc tả. Không hardcode mã lỗi mới trong FE trước khóa contract.
5. Hướng AI audit/requestId/logging: giữ trace kỹ thuật cần thiết, không dump key hoặc nguyên prompt/source nhạy cảm vào handoff. Hash ghi rõ là file checksum, fixture ghi TEST_ONLY; không tự đánh dấu tất cả secret alerts false positive hoặc sửa evidence lịch sử để dập alert.

### P6.6 — Kế hoạch kiểm thử khi có prompt thực thi

**Hiện tất cả P6 NOT_RUN; không chạy test/build/app trong lượt soạn.**

| Nhóm | Mục tiêu đề xuất / oracle | Điều không được nhận là đạt |
|---|---|---|
| Exact-lock | Gate P6.0 như P6.1; import/runtime và pytest suite AI hiện hữu + mới theo lock, fake provider/no real network | 69 pass env cũ không thay exact-lock; install thành công không thay suite pass |
| AI01 Java/Python | Same server-resolved context specimens, primary/secondary/channel/grade/profile parity, foreign/out-of-scope refs, forbidden claims, null/unknown/limits; real localhost BE↔Python transport | Mock BE/Python thay runtime chỉ là unit, không full AI01 |
| AI02 / accept | Manual vs AI invalid refs qua cùng draft validator; explicit save có CAS/seat, no auto save/approval, in-flight target edit409; malformed/timeout/429/manual fallback | Accept log không phải save; fake model không chứng minh output khoa học đúng |
| AI advisory | G04 findings chỉ advisory, không hard AI PASS gate; no AI call autosave, no retry storm; no server-owned/VERIFIED/ACTIVE mutation | Không bật STRICT để test “happy path” giả |
| Library L01 | PostgreSQL integration kết hợp level/grade/subject/outcome/context-role/channel + paging/count/facets; mutable current metadata đổi không đổi published card; stable sort, empty/last page, no duplicate JOIN rows | Chỉ FE filter hoặc page trước filter không đủ PASS |
| Library L02 | Snapshot thiếu/legacy/null browse/detail/export tương thích, nhãn đúng; known artifact mismatch không được publish bypass | Không backfill/cấp current STEM eligibility để làm legacy test qua |
| FE | Vitest/RTL unit/component + integration real BE clone, card/task outside MP4/labels, combined filter/page, stale request response giữ UI, lỗi giữ input, AI unavailable manual vẫn làm | Không browser Playwright/real model/MP4 nếu chưa có lựa chọn scope riêng |
| Gap specimens | Q05 chọn mới nhận F09/F10 DTO/reference coverage và F14 version mismatch; ghi từng scene/block/supported subcase | Full F09/F10, F06/pixel/audio/AT21 không chuyển PASS bằng DTO tests |
| Mandatory regression | **Rerun đủ 268 BE + 227 FE + 2 live P4/G06 trên source cuối P6**, cùng case cũ không sửa assertion/config/runner để né lỗi; thêm AI/library regression vùng ảnh hưởng | Không cộng 41 thêm lần nữa vào268, không cộng216 thêm vào227, không cộng 2live vào FE nếu suite riêng |

268 BE gồm113+41+114, 227 FE gồm216+11; số test mới P6 chỉ ghi sau chạy thực. Regression AI hiện hữu và library/controller/repository/FE paging helper tests có thể ngoài tập 268, phải deduplicate theo suite/test ID. Ghi source/lock hash cuối, suite selection/count/pass/fail/error/skip, pre-existing/environment/new defect, fake/real, log và NOT_RUN riêng. Full BE439, browser acceptance ba cấp, provider thật, MP4 và exact-lock blocked không được coi là pass ngầm. Nếu source đổi sau một lượt final thì rerun đúng gate cần thiết theo prompt, không cộng lượt lặp để tăng số.

### P6.7 — Môi trường và bảo toàn

Ưu tiên Q06-A: **reuse B/F tại commit backup**, không re-clone main vì main thiếu P3/fix/P4/P5; không cần merge để test integration local. Chờ merge riêng là lựa chọn Q06-C, không quyền agent merge.

Sau duyệt mới preflight status/HEAD/branch/hash/diff kể cả untracked; tách build/cache tồn tại và delta P6, không clean/stash/reset hoặc ghi đè work mới. Nếu source SHA/owner khác baseline hoặc cần checkout/fast-forward, dừng phần phụ thuộc xin hướng; không tự kéo main. Node engines/lock/Java17/Maven wrapper/Python theo source, không global install/upgrade package để né lỗi.

AI venv, output/build/cache/log mới riêng P6 ở location được prompt thực thi duyệt, không ghi lên P3/P4/P5 evidence hoặc node_modules junction của FE trỏ repo gốc. Embedded PostgreSQL disposable, E2eServer/fake mail/render/TTS, localhost port trống và env allow-list; không `.env` thật, DB/server người dùng/shared/staging, mail/Brevo/YouTube/AI/TTS thật. Provider thật chỉ nếu Q03 + lệnh riêng mở rõ. Không sửa worker/Remotion/assets để lấp media proof. Chỉ dừng PID/data-dir do runner P6 tạo; không kill app người dùng.

### P6.8 — Dependency / rollback

- Cần Bond review, người dùng chọn Q01–Q10 và prompt thực thi riêng. Nếu câu nào thiếu thì phần phụ thuộc giữ blocked, không coi ưu tiên là đồng ý.
- AI runtime phụ thuộc exact-lock, context/operation contract và key/budget nếu chọn provider thật. Library không phụ thuộc có key AI; P6 có thể bàn giao PARTIAL từng nhánh nếu Q01 cho độc lập, không đóng toàn AI/library chỉ vì một nhánh đạt.
- T11 frozen read phụ thuộc snapshot actual P5 và quyền/query đúng lifecycle. Dashboard/copy/migration chưa duyệt là phần thiếu scope, không tự thêm file mới. P3–P5 chưa merge không chặn thử local; nghiệm thu/merge là quyết định khác.
- Chuyên môn/source/currency còn mở: không cản negative/technical fixtures DRAFT, nhưng cản curricular/VERIFIED claim. Không tự liên hệ giáo viên hoặc giả feedback. C01/C02/BR-01/shared rollout và production live không được suy từ test local.
- Rollback về sau chỉ **delta P6** theo input/source manifest tại 4fd7089/4dfa65e cộng mọi work mới đã ghi; giữ nguyên P3/fix/P4/P5 và backup refs. Không revert/reset commit backup, force-push, xóa untracked build hoặc restore đè work người khác. Nếu có uncommitted delta chỉ selective phục hồi sau kiểm owner/hash; chưa có quyền chạy rollback trong lượt này.
- DB test có thể dừng đúng context/disposable instance do P6 tạo; không down/drop/repair shared/migration history. Giữ snapshots, compatibility/history/publication không rewrite; OFF không bypass known artifact mismatch/media FAIL hoặc xóa review. Dọn venv/output chỉ khi đúng owned path được xác nhận, không dọn env người dùng.

## 4. File/service ứng viên và đầu ra sau duyệt

Danh sách là **allowlist đề xuất để review**, không chỉ dẫn sửa ngay. Contract-stage phải khóa exact files cho phương án đã chọn; phát sinh component/query/helper ngoài bảng cần nêu rõ trước phần phụ thuộc. Không rewrite kiến trúc.

| Root / surface | File ứng viên | Mục đích / ranh giới |
|---|---|---|
| B AI | `src/main/java/com/stemotion/domain/ai/{controller/AiController.java,dto/AiDtos.java,service/AiScriptService.java,service/AiSchemas.java}`, `src/main/java/com/stemotion/integration/client/AiGateway.java` | Target resolution/grounding/profile/schema/transport; keep existing request audit/decision/provider errors. |
| B context | `src/main/java/com/stemotion/domain/stem/{StemService.java,StemRepository.java,StemDtos.java}`; ứng viên helper mới `StemAiContextResolver.java` nếu trách nhiệm cần tách | Read đúng brief/edition/role/revision, giữ private/accessor fix A. Không đổi assessment/activation/mode wire. |
| B Python | `services/ai/app/{models.py,prompting.py,schemas.py,validation.py,main.py}`, `app/prompts/{system,generate,resegment,grade,extract,terms}.txt`, selected `app/contracts/*.json` | Chỉ operation chọn Q02, parity + bounds. `providers.py` read-only trừ Q03 thực sự cần adapter fake test; không provider rewrite theo thói quen. Lockfiles input read-only, không upgrade. |
| B library | `src/main/java/com/stemotion/domain/library/{controller/LibraryController.java,dto/LibraryDtos.java,service/LibraryService.java}`, `domain/production/repository/PublicationRepository.java`, `domain/stem/StemArtifactService.java`; ứng viên helper query mới trong domain/library | Frozen card read/filter/count/facets/paging. Không sửa snapshot capture/hash hay publish gate để chỉ phục vụ card. Query native mới phải review parameterization/visibility/count. |
| B persistence | Không migration trong ưu tiên Q09-A. `src/test/resources/db/stem-p6/` chỉ khi Q09-B và gate số/location được duyệt | Không alter V91000/V92000 hoặc production schema/config. |
| F | `src/services/{scriptService,libraryService}.ts`, `src/types/stem.ts`, `src/components/views/{WriterStudio/WriterStudio.tsx,MediaLibrary/MediaLibraryView.tsx}`; ứng viên component card mới trong `src/components/stem/` | Grounded suggestions display/binding + card/filter/page, không Create. Exact card filename sau quyết Q04, không coi file chưa tồn tại là baseline. |
| F conditional | `src/services/apiClient.ts` chỉ nếu helper hiện hữu không đọc được paging headers; `src/components/views/ProjectDashboard/` và corresponding BE project DTO/service chỉ nếu Q04-B | Header compatibility cần review; dashboard exact path/API chưa khóa, là scope gap phải chốt trước sửa. Không mở toàn bộ ProjectDashboard directory mặc định. |
| Tests / runners tương lai | B tests domain/ai, library, stem, e2e mới; `services/ai/tests/`; F test mới cạnh components/services được sửa; owned P6 orchestration | Giữ test source/config/runner P4 và P5 nguyên bản; reuse read-only, bổ sung P6 runners/config riêng. Nếu cần đổi assertion cũ do contract break phải trình lại. |

Đầu ra **dự kiến khi P6 được thực thi**, chưa tạo trong lượt này: contract actual/delta + field mapping; exact-lock report/package inventory; grounded context specimens và Java/Python parity; library API/card/filter/page map; source/diff manifests; §8 từng ca/subcase; test/log cuối; lỗi/blocker/cleanup; handoff expected/actual theo P6.1–8, feature/model/fake-vs-real limits, commits0 nếu không có duyệt sao lưu riêng. Không thêm artifact ngoài file thẻ này ở lượt soạn.

## 5. Mapping §8 P3 / §6 P4 / §4 P5 → P6, tách đủ 52 ca

Namespace P2, trigger gốc trong CONTRACT_REVIEW_CASES; trạng thái P5 là evidence kế thừa tại CASE_COVERAGE_P5. **Mục tiêu P6 không phải kết quả đã đạt.** PASS_TECH/SUBCASE đề nghị không đóng full ca khi thiếu chuyên môn/shared/renderer. Không gộp AI01 với AI02, L01 với L02, F09 với F10 hoặc các ca có trạng thái khác nhau.

| Ca | Baseline P5 | P6 đề nghị / trạng thái phần còn mở |
|---|---|---|
| I01 | DOCUMENT_REVIEW registry | Giữ ID/UC/FR candidate; trace AI/library tới UC có sẵn, không cấp số hoặc sửa official reports. DOCUMENT_REVIEW. |
| R01 | PARTIAL refs/status snapshot | AI chỉ resolve editions server; card giữ source/provenance UNVERIFIED. PASS_TECH ref/label nếu test; source currency/expert còn PARTIAL. |
| C01 | PASS_SUBCASE typed context | Test roundtrip grounding/card primary/secondary/conditions; full sư phạm variant PARTIAL. |
| C02 | PASS_SUBCASE channel không primary | AI/filter channel DEPARTMENT_LIBRARY tách learning context; regression enum guard, mục tiêu PASS_TECH. |
| C03 | PASS_SUBCASE draft/null/bounds | AI request thiếu brief theo legacy route rõ; null card không bịa; reject duplicates/invalid bounds deterministic. Full task safety PARTIAL. |
| C04 | PASS_SUBCASE raw legacy không backfill | AI không tự map legacy; thư viện hiển thị raw/label đúng. Human mapping PARTIAL. |
| C05 | PARTIAL references/safety | AI task refs/source foreign invalid, no auto safety verdict; kỹ thuật refs nhận, chuyên môn vẫn PARTIAL. |
| G01 | PASS_SUBCASE null/legacy/default-off | Regression manual draft/preview không đòi AI/brief đầy đủ; AI unavailable không chặn manual. Không renderer acceptance mới. |
| G02 | PASS_SUBCASE OFF/ADVISORY labels | AI/card không gán current verified; integrity gate giữ. Không positive VERIFIED/STRICT. |
| G03 | PASS_SUBCASE pure STRICT negative | Rerun regression evaluator; no persisted STRICT/activate. Full STRICT live NOT_RUN. |
| G04 | PASS_SUBCASE AI WARN evaluator; grounding NOT_RUN | Nhận advisory-vs-hard-gate end-to-end fake grounded test; AI model accuracy/chuyên môn PARTIAL. |
| G05 | PASS_SUBCASE frozen-input QA | Regression QA/evaluatedAgainst; card không trộn QA với STEM/science. Pixel/audio proof NOT_RUN. |
| G06 | PASS_SUBCASE CAS + 2 live | Rerun nguyên bản; grounded target đổi mode/counter phải stale, wire settings chỉ baseRevision. Shared rollout NOT_RUN. |
| A01 | PASS_SUBCASE active seat | AI accept không bypass REVIEWER; direct assessment Admin no-seat giữ403, rerun. |
| A02 | PASS_SUBCASE cross-workspace | Grounding foreign brief/edition/project request bị403/404 theo quyền; query library không mở visibility. Mục tiêu PASS_TECH. |
| A03 | PASS_SUBCASE schema/CAS | AI apply dùng write path đúng400 absent/409stale, forbid server claims, atomic. Mục tiêu PASS_TECH. |
| A04 | PASS_SUBCASE target/hash/race | Context đổi trong provider call và stale library response; apply409 giữ input/no write, no prompt dưới transaction. Broad renderer proof PARTIAL. |
| V01 | PASS_SUBCASE copy-map/no approval copy | Regression mapping/version; read/export reuse không copy review. Full project clone ngoài scope. |
| V02 | PASS_SUBCASE reorder identity/hash | AI suggestion không remap evidence bằng order; rerun identity/hash. Pixel order proof NOT_RUN. |
| V03 | PASS_SUBCASE insert identity | Nếu explicit save scene mới, đúng schema/IDs và không bịa evidence; không Create/auto-apply. |
| V04 | PASS_SUBCASE orphan/history | AI suggestion không xóa orphan im lặng; rerun old evidence handling. Chuyên môn orphan vẫn PARTIAL. |
| V05 | PASS_SUBCASE copy rollback | Regression failed draft apply không partial; không shared rollback. |
| F01 | PASS_SUBCASE static safe timing | Giữ TITLE_HERO/OUTRO static subset; không dùng AI claim mở safe timing. Full renderer/AT21 PARTIAL. |
| F02 | PASS_SUBCASE decrease cutoff negative | Rerun no inherit khi thiếu proof; positive safe-decrease dời. |
| F03 | PASS_SUBCASE token/unit/negative | AI mutation caption không normalize mất “không”/unit/minus; regression. No real ASR/TTS. |
| F04 | PASS_SUBCASE cue hashed/UNKNOWN | AI cue refs không trusted proof; cue rendering dời, PARTIAL phần visual. |
| F05 | PASS_SUBCASE motion raw/UNKNOWN | Giữ no auto-inherit; motion/relationship proof dời. |
| F06 | PARTIAL geometry/visibility | Regression UNKNOWN/hard boundary, không pixel suite; full PARTIAL. |
| F07 | PARTIAL broad theme | Giữ conservative unknown; không safe-theme expansion. PARTIAL. |
| F08 | PASS_SUBCASE unknown raw preserved | Output/accept không strip field để giả supported/current; invalid schema reject, old raw draft không mất. Mục tiêu PASS_TECH. |
| F09 | PARTIAL 13 scene projector specimens | Q05 có thể nhận valid DTO/schema/refs specimens từng scene, full render acceptance vẫn PARTIAL. |
| F10 | PARTIAL 12 blocks/extraBlocks | Q05 có thể nhận từng kind hợp lệ/ref/cue schema; không 13th block, full visibility/render vẫn PARTIAL. |
| F11 | PASS_SUBCASE canonical invariance | Rerun P5 canonical; context envelope serialization không thay semantic canonical. No algorithm redesign. |
| F12 | PASS_SUBCASE finite/precision/guards | Grounded request/output bounds + existing negative cases, no symbolic equivalence. New limits cần contract gate. |
| F13 | PARTIAL audio missing proof | Giữ UNKNOWN/no inherit, no resolver/audio thật. Positive signedURL digest proof dời. |
| F14 | PARTIAL version/history | Nhận no auto-inherit version mismatch + immutable library refs regression. Positive upgrade/production withdrawal dời. |
| B01 | PASS_SUBCASE real HTTP CLIP/fake media | Mandatory regression đúng current clip/decision; không thêm MP4 claim. |
| B02 | PASS_SUBCASE real HTTP COMPOSITION/fake callback | Mandatory regression approval→job→snapshot→publish; không mở worker. |
| B03 | PASS_SUBCASE retry/callback binding | Rerun old-attempt/mismatch/nonce guards; independent checksum/real bytes NOT_RUN. |
| B04 | PASS_SUBCASE send-back | Regression send-back không cần AI PASS/STEM PASS; không bật STRICT. |
| P01 | PASS_SUBCASE publication snapshot/preconditions | Card/filter/count/export immutable sau mutable brief/project edit; mục tiêu PASS_TECH frozen consumer. Mixed epochs/reconciliation PARTIAL. |
| P02 | PASS_SUBCASE legacy ack/OFF/history | AI/library không nâng labels khi OFF, captured provenance giữ; no verified fixtures. Shared rollback NOT_RUN. |
| P03 | PASS_SUBCASE YouTube503 | Rerun mandatory no writes; không nhận RF-08 hoặc OAuth/YouTube thật. |
| AI01 | NOT_RUN grounding/exact-lock | Nhận Java/Python context parity + primary profile + ownership/schema/runtime fake; chỉ PASS_TECH nếu exact-lock gate và transport đạt. Provider quality/chuyên môn riêng. |
| AI02 | PASS_SUBCASE validator; full accept NOT_RUN | Nhận AI/manual explicit save same gates, no auto-save/verified; stale/invalid refs/timeout/manual fallback. Full remote claim blocked nếu exact-lock chưa đạt; Create không mở. |
| L01 | PARTIAL old subject/grade snapshot consumer | Nhận frozen learning card + combined filters/context-role/channel + DB paging/count và FE consumption nếu Q04-A/B. Mục tiêu PASS_TECH phần đó; dashboard dời thì T11 vẫn PARTIAL. |
| L02 | PARTIAL legacy read/export labels | Nhận legacy/null/missing curriculum browse/detail/export negative/no backfill; mục tiêu PASS_TECH synthetic acceptance. Production conversion/shared browse chưa được chứng minh. |
| M01 | PASS_SUBCASE fresh P5 26/P3 25 | Không migration nếu Q09-A; nếu B test fresh location mới được duyệt. Shared equivalence NOT_RUN. |
| M02 | PASS_SUBCASE fixture/hash preservation | Không sửa/promote test migrations; registry V93000 chỉ candidate nếu cần. No production numbering/repair/outOfOrder. |
| M03 | PASS_SUBCASE wrong type synthetic | Nếu migration mới: negative disposable prerequisites; shared drift/preflight scope riêng. Không IF NOT EXISTS che mismatch. |
| D01 | DOCUMENT_REVIEW/native export thiếu | Chỉ trace phần P6, không Word/BM/diagram/T12a hoặc official ID changes; P7 dependency giữ. |
| E01 | PARTIAL cả ba cấp | Không chuyên môn giả; profile/card AI DRAFT/UNVERIFIED. Theo Q10 quyết review thật riêng, không đóng E01 bằng fake outputs. |

**AT21 (master namespace, tách riêng khỏi 52 ca):** chỉ regression positive static subset của P5 và negative unsafe/UNKNOWN; không tăng supported timing/theme/audio, không MP4/pixel/TTS thật theo ưu tiên Q05/Q08. Full AT21 vẫn PARTIAL, không cộng count trùng evidence F01. **AT04** (AI outcome giả), **AT17** (AI timeout/manual fallback), **AT19** (combined library paging) là các mục tiêu kỹ thuật P6 tương ứng AI02/AI01/L01; không cộng chúng lần nữa như test độc lập nếu cùng assertion. Nghiệm thu cả AT01–24/ba demo vẫn P7 sau scope riêng.

## 6. Thứ tự thực thi dự kiến và gate bàn giao — chưa chạy

1. Nếu người dùng duyệt thẻ và có prompt thực thi: snapshot local B/F/lock/source, xác nhận chosen Q và phương án environment. Không clean dirty output.
2. Q01-A: exact-lock P6.0 trước validation AI; contract/read-only planning và library độc lập có thể chuẩn bị theo phạm vi được chọn. Lỗi lock không được tự thay lock hoặc báo AI PASS.
3. Khóa contract AI operation/refs/revisions/claims/parity và library frozen field→query→card/headers; trình migration gate nếu phát sinh. Không code phần phụ thuộc khi target/limits/số migration chưa rõ.
4. Thực thi đúng AI/library subset được duyệt; labels DRAFT/UNVERIFIED, no auto-save/autosave AI, no STRICT/activate.
5. Chạy test mới + regression cuối268/227/2 và AI suite đúng lock; no mock thay BE clone. Thiếu môi trường hay authority thì NOT_RUN/blocker; không nhận DONE toàn ca chỉ từ unit.
6. Handoff actual/expected/files/source SHA/lock/package inventory/test logs/counts/cleanup/fake-vs-real/ngoài kế hoạch/dependency; Bond/người dùng nghiệm thu riêng. Không tự mở P7/P8 hoặc commit/push/merge.

Không có bước nào trong danh sách trên được chạy bởi việc soạn thẻ. Lượt hiện tại chỉ đọc nguồn và tính checksum tài liệu, không software verification/test suite.

## 7. Ranh giới cứng và phần dời

- Không P7 nghiệm thu/hồ sơ FR-38/Word/BM/diagram/T12a, không P8 shared/release/rollout; không merge/push/deploy vào main hoặc nhánh khác. Lệnh backup trước không cấp quyền commit/push trong P6.
- Không STRICT/activate toàn hệ thống; Q07 chỉ hỏi giữ mode hay cần đề xuất scope riêng, không thể override ranh giới này bằng lựa chọn mơ hồ.
- Không liên hệ giáo viên/chuyên gia, tạo feedback giả, tự chốt LED/Toán 11 hoặc nâng source/outcome/brief/rubric/catalog VERIFIED/ACTIVE. Nếu Q10 cần chuyên môn, người dùng/nhóm thu phản hồi thật qua scope riêng; Codex chỉ ghi dependency.
- Create flow, project clone toàn bộ, catalog CRUD/activation, LMS/student account, worker/Remotion rewrite, media proof rộng, hash migration/production reconciliation, AI provider/key setup ngoài lựa chọn duyệt đều không tự nhận.
- Không sửa code BE/FE/Python, runner/config/SQL, cài package, gọi network/provider/DB/test/build trong lượt này; không sửa bằng chứng cũ cho khớp kế hoạch. Thiếu input ghi gap, không tự suy thiết kế cuối.

## 8. P6-Q01–Q10 — tất cả UNSELECTED

Phương án **ưu tiên đề xuất** là khuyến nghị để review, không phải lựa chọn đã khóa. Người dùng quyết từng câu sau Bond review; không dùng im lặng hoặc “đã duyệt P5” thay câu trả lời P6.

| Câu hỏi | Phương án và hệ quả | Ưu tiên đề xuất / chưa chọn |
|---|---|---|
| **P6-Q01 — Exact-lock** | **A:** làm cổng P6.0 trước validation AI; library/contract độc lập chỉ tiếp theo điều kiện P6.1. **B:** làm song song AI code/library, nhưng final AI claim vẫn blocked tới lock đạt; có nguy cơ làm lại khi deps khác. **C:** dời exact-lock; P6 chỉ contract/fake env-labelled/library, AI01/02 runtime giữ PARTIAL. | **A**, phân biệt môi trường thật với69contract cũ. Chưa chọn; chưa cho cài dependency hoặc chạy suite. |
| **P6-Q02 — AI grounding** | **A:** generate + existing analysis action ở Writer của project đã có brief, server-resolved context/profile và advisory validation; approaches/storyboard/metaphor/Create dời. **B:** chỉ analysis/gợi ý, dời grounded generation; T10 PARTIAL. **C:** mở thêm approaches và các operation được liệt kê rõ, cần schema/FE/test allowlist lớn hơn; Create vẫn ngoài. | **A**, có lát cắt T10 testable, giữ scope draft hiện hữu. Cần chọn exact operation, response refs và accept/save semantics trước code. |
| **P6-Q03 — Provider AI** | **A:** fake/offline provider, real local Java/Python transport; không cần key thật, không chứng minh chất lượng provider. **B:** provider thật với user cấp key/model/quota/ngân sách và lệnh gọi riêng; có chi phí/dữ liệu gửi ra ngoài. **C:** hybrid fake regression + real smoke hữu hạn sau gate, cùng yêu cầu key/budget của B; không có key thì smoke NOT_RUN. | **A** cho P6 kỹ thuật. Người dùng/owner được chỉ định chịu trách nhiệm cấp key và tài khoản chi phí nếu B/C; Codex không tạo key, chưa biết key có sẵn hay không. |
| **P6-Q04 — Library** | **A:** frozen card + server filters/paging/count + read/export/link reuse, legacy label; dashboard/full project clone dời quyết riêng, T11 còn PARTIAL dashboard. **B:** A + dashboard summary tối thiểu, cần chốt exact project DTO/view/origin; không mix draft và publication. **C:** BE filters/read DTO trước, dời card/FE, L01 end-to-end PARTIAL. Legacy không tự map ở mọi phương án. | **A**, hoàn thiện nhu cầu giáo viên trên artifact đã có và không mở Create. Nếu muốn đủ T11 dashboard cần chọn B, không suy dashboard đã được duyệt. |
| **P6-Q05 — Gap P5** | **A:** giữ media proof UNKNOWN; nhận F09/F10 validDTO/reference specimens và F14 negative version regression, không nhận full render/audio/upgrade bridge; bảng P6.4 áp từng gap. **B:** dời toàn bộ gap media/projector, P6 thuần AI/library, F09/F10 giữ nguyên. **C:** xin scope media proof riêng có renderer/worker/files/toolchain/budget; chưa thực thi bằng thẻ này. | **A**, củng cố contract AI output mà không giả MP4 proof. Mọi gap render dời vẫn PARTIAL; C cần scope và lệnh riêng, không blanket nhận mọi gap. |
| **P6-Q06 — Môi trường** | **A:** reuse B/F ở4fd7089/4dfa65e, giữ output dirty, baseline mới trước edit; không chờ merge. **B:** clone mới từ đúng hai commit backup sau duyệt location, không từ main trống P3–P5; thêm setup/storage. **C:** chờ quyết merge P3–P5 riêng, dừng phần triển khai phụ thuộc; không agent tự merge. Re-clone từ main hiện tại không phải baseline tương đương. | **A**, đã có source tích hợp được backup; không cấp quyền checkout/pull/re-clone trong lượt soạn. |
| **P6-Q07 — STRICT/activate** | **A:** giữ OFF/ADVISORY, DRAFT/UNVERIFIED; chỉ negative evaluator regression, không activate. **B:** soạn điều kiện readiness cho STRICT như dependency riêng, không bật trong P6; rollout thực P8 phải duyệt. Yêu cầu bật ngay cần scope ngoài thẻ vì §7 cấm. | **A**, thiếu expert/source/shared gates. Không mở setting STRICT hoặc tạo VERIFIED fixture. |
| **P6-Q08 — Mức test** | **A:** exact-lock + local AI fake transport/accept và library integration, mandatory268BE/227FE/2live + tests mới, F09/F10 subset nếu Q05; no provider/MP4/browser thật. **B:** A + real provider smoke hữu hạn theo Q03 B/C; không tự mở renderer. **C:** unit/component-only trước, transport/library E2E kỹ thuật NOT_RUN, bàn giao PARTIAL. Muốn MP4/renderer thật phải chọn scope riêng Q05-C. | **A**, mục tiêu PASS_TECH AI01/AI02/L01/L02 và G04, phần khác regression/PARTIAL như §5; không full52ca/AT01–24 PASS. |
| **P6-Q09 — Migration/contract** | **A:** ưu tiên không migration, additive coordinated DTO/read query trên frozen P5 payload; khảo sát đủ mới code. **B:** additive TEST_ONLY nếu cần persistence/index; đề nghị V93000–V93009, dùng số/location nào do Bond review và user xác nhận ledger trước SQL. **C:** contract-only, dời DB/query-dependent phần chưa chốt. | **A**, không cần tạo schema chỉ để thêm card; chưa đảm bảo query/index đủ. Nếu cần B phải quay lại gate; không sửa V91000/V92000/V23 hoặc tạo placeholder. |
| **P6-Q10 — Chuyên môn** | **A:** kỹ thuật dùng DRAFT/UNVERIFIED, ghi câu hỏi và dependency chuyên môn P7; không contact hoặc fake response. **B:** cần phản hồi thật như prerequisite phần nội dung do người dùng/nhóm cung cấp; thiếu thì blocked riêng, không giả VERIFIED. **C:** user tổ chức review song song, AI/library kỹ thuật vẫn DRAFT tới xác nhận theo quy trình riêng. | **A** cho scope kỹ thuật, không nhận curricular validation/ba cấp verified. Không tự gọi sẵn có chuyên gia, không nâng status vì test pass. |

Điểm Bond/người dùng cần làm rõ ngoài chọn chữ: operation grounded cụ thể; contract refs/limits/CAS và legacy route; dashboard có vào T11 lần này; nghĩa của tái dùng; có mở schema/index và số TEST_ONLY; có nhận DTO-only F09/F10; provider/key/model/budget nếu muốn gọi thật; bằng chứng 10 incident người dùng đã kiểm nếu cần review bảo mật. Thiếu câu trả lời không tự điền bằng ưu tiên của Codex.

## 9. Input đã đọc / đối chiếu và checksum

Checksum là SHA-256 của **bytes file local trong lượt soạn**, không credential hay semantic fingerprint, không chứng nhận chuyên môn. Phần này kèm trong thẻ, không tạo manifest riêng. Read-only git xác nhận B/F HEAD/parent/branch/status ở §2; không chạy test để xác minh lại các số kế thừa. Danh sách tiếp theo ghi file input, phần đã đọc và hash tương ứng.

Hash bổ sung lúc **06/11/2026 18:28:42 UTC+07:00**, sau mốc 18:03. Đây là hash quan sát khi bổ sung, không phải bản chụp checksum đã có ở lượt đọc trước; chưa dùng để khẳng định không có concurrent edit giữa hai lượt. Với file chỉ đọc đoạn/locator, checksum vẫn định danh toàn file và cột nội dung nêu đúng phạm vi đọc.

| # | Input / phần đã đọc, đối chiếu | SHA-256 bytes file |
|---|---|---|
| 1 | [Pasted text.txt](<C:/Users/LeTran/.codex/attachments/a34f94a0-61a3-4f3c-ab87-e409d5d28b70/Pasted text.txt>) — Yêu cầu soạn P6: toàn văn | `fd16622463a3a2d4e4b5a6c1020220d9ab59cccf420bd3ef79e22c7d14bb109b` |
| 2 | [PHASE_EXECUTION_PLAN_2026-11-04.md](<C:/Users/LeTran/FPT University/2026_FALL/STEMotion/STEMotion_BE/docs/stem/PHASE_EXECUTION_PLAN_2026-11-04.md>) — Execution plan: toàn văn, trọng tâm §3/§4 P6 và gate | `78de965b8dd44c40845eee15a470aa804dd86c9d85dd474330cd018ea111d22e` |
| 3 | [STEM_MASTER_PLAN_2026-11-04.md](<C:/Users/LeTran/FPT University/2026_FALL/STEMotion/STEMotion_BE/docs/stem/STEM_MASTER_PLAN_2026-11-04.md>) — Master: T10/T11, §9/§11/§13/§14 và dependency; đề xuất đối chiếu | `04484527a25247ff1d85b4775a9231bc87d1f262d3425a58ca5b595a0fd6ed33` |
| 4 | [P5_HANDOFF.md](<C:/Users/LeTran/FPT University/2026_FALL/STEMotion/.audit/stem-phase-2026-11-05/phase-03/STEMotion_BE/docs/stem/phases/phase-05/P5_HANDOFF.md>) — P5: toàn văn, trạng thái trước sao lưu | `0c0fa20a7066002701e221870ffcab641f81e0deb6f286a6cc9a17553a091a8e` |
| 5 | [CONTRACT_ACTUAL_P5.md](<C:/Users/LeTran/FPT University/2026_FALL/STEMotion/.audit/stem-phase-2026-11-05/phase-03/STEMotion_BE/docs/stem/phases/phase-05/CONTRACT_ACTUAL_P5.md>) — P5: toàn văn actual contract | `87e0c1395b49afdb42368004a498d031d6e031b3d611c0448924c6a648554c64` |
| 6 | [CASE_COVERAGE_P5.md](<C:/Users/LeTran/FPT University/2026_FALL/STEMotion/.audit/stem-phase-2026-11-05/phase-03/STEMotion_BE/docs/stem/phases/phase-05/CASE_COVERAGE_P5.md>) — P5: 52 ca tách riêng + AT21, actual/limits | `ed5902f78ebe3388e23e59f65b54eb29cb42717f67be41dc491f1f53285d795a` |
| 7 | [STAGE_1_HANDOFF.md](<C:/Users/LeTran/FPT University/2026_FALL/STEMotion/.audit/stem-phase-2026-11-05/phase-03/STEMotion_BE/docs/stem/phases/phase-05/STAGE_1_HANDOFF.md>) — P5: toàn văn design history và các gate đã trình | `655effd1e06087f8d4e137e720ac5d6e09ad096867e79089a1748c9ca01f4436` |
| 8 | [PHASE_05_SCOPE_DRAFT.md](<C:/Users/LeTran/FPT University/2026_FALL/STEMotion/.audit/stem-phase-2026-11-05/phase-05/PHASE_05_SCOPE_DRAFT.md>) — P5: phạm vi, §4 mapping và Q01–Q12/GAP-01/GAP-05; không dùng DRAFT làm actual | `6551783011bfff779c87e09d6d96791392730e55bc8f32cc11e782087e52fc9a` |
| 9 | [SETUP_REPORT.md](<C:/Users/LeTran/FPT University/2026_FALL/STEMotion/STEMotion_BE/docs/stem/phases/phase-00/SETUP_REPORT.md>) — P0: toàn văn, exact-lock PARTIAL và bootstrap | `abe6b09022171ce23e9061ce701828865ea42bcddc2159c71599364cebabdaa7` |
| 10 | [TEST_BASELINE.md](<C:/Users/LeTran/FPT University/2026_FALL/STEMotion/STEMotion_BE/docs/stem/phases/phase-00/TEST_BASELINE.md>) — P0: toàn văn, 69 contract pass không thay exact-lock | `0a9d7d285f0e2d754047f16e38e1bef53d132df687b266c00c4ec4b1f1782f8e` |
| 11 | [ai-dependency-check.json](<C:/Users/LeTran/FPT University/2026_FALL/STEMotion/.audit/stem-phase-2026-11-04/phase-00/ai-dependency-check.json>) — P0: toàn bộ 77 entries/41 mismatch inventory | `259734191ecb009151f2b62b603d420c8011b3e28139a518b21cf990742fc761` |
| 12 | [ai-tests-native.json](<C:/Users/LeTran/FPT University/2026_FALL/STEMotion/.audit/stem-phase-2026-11-04/phase-00/ai-tests-native.json>) — P0: runner result/log locator, không rerun | `86b59543692863ccf13131efc07ae6b4e0abfd23ecaf03b3ad420704a066b51f` |
| 13 | [BACKUP_HANDOFF.md](<C:/Users/LeTran/FPT University/2026_FALL/STEMotion/.audit/stem-branch-backup-2026-11-06/BACKUP_HANDOFF.md>) — Backup: toàn văn commit/remote/protected refs/exclusions | `94f6b2b90ced1d3db6a9cdc5dd7c93c03bf60875bac46b03d2c46760785a2b1b` |
| 14 | [INCIDENT_TRIAGE.md](<C:/Users/LeTran/FPT University/2026_FALL/STEMotion/.audit/stem-branch-backup-2026-11-06/INCIDENT_TRIAGE.md>) — GitGuardian: toàn văn lịch sử triage; trạng thái mới theo prompt người dùng | `95a856c82651087a25864dc83dff5467c17159dcc0e45179d353e2d86843a4d7` |
| 15 | [PHASE_03_SCOPE.md](<C:/Users/LeTran/FPT University/2026_FALL/STEMotion/STEMotion_BE/docs/stem/phases/PHASE_03_SCOPE.md>) — P3: §8 mapping và validator/AI/schema boundaries liên quan | `ed92cfcbc53bf9aec45193efb37a360d8eb4ca4ce9641d82b475c9e36de63100` |
| 16 | [PHASE_04_SCOPE_DRAFT.md](<C:/Users/LeTran/Downloads/PHASE_04_SCOPE_DRAFT.md>) — P4: §6 mapping, scope FE/AI/legacy liên quan; đúng file được chỉ định | `1168656798008c64636949c07bda986aea0f17aa70058d769422ddda1d778aed` |
| 17 | [CONTRACT_REVIEW_CASES.md](<C:/Users/LeTran/FPT University/2026_FALL/STEMotion/STEMotion_BE/docs/stem/phases/phase-02/CONTRACT_REVIEW_CASES.md>) — P2: trigger 52 ca, trọng tâm AI01/AI02/L01/L02/F09/F10 | `45a662f5588a175f59e6a8b378d28bf7268db6ff5c2b608aeff4c53b5a646a6b` |
| 18 | [AiDtos.java](<C:/Users/LeTran/FPT University/2026_FALL/STEMotion/.audit/stem-phase-2026-11-05/phase-03/STEMotion_BE/src/main/java/com/stemotion/domain/ai/dto/AiDtos.java>) — Source: toàn văn DTO, chưa project/brief grounding refs | `6d0fab994a6fe7bef0a6c8c284fb80f7b6164881f652a17bbf183665b9e99279` |
| 19 | [AiController.java](<C:/Users/LeTran/FPT University/2026_FALL/STEMotion/.audit/stem-phase-2026-11-05/phase-03/STEMotion_BE/src/main/java/com/stemotion/domain/ai/controller/AiController.java>) — Source: toàn văn endpoint và accept/ignore log | `00bd24ec91e91ba47892f791de727aee0a92b3c4cb01d2eaf75ea7cabc99a7f2` |
| 20 | [AiScriptService.java](<C:/Users/LeTran/FPT University/2026_FALL/STEMotion/.audit/stem-phase-2026-11-05/phase-03/STEMotion_BE/src/main/java/com/stemotion/domain/ai/service/AiScriptService.java>) — Source: instruction/generate branch đầu file và locators action/approaches/schema/remote | `ff7f3da247c0ca91811d6013b244c5de9f414de0eb467a2b6c530e73411c8a1b` |
| 21 | [AiGateway.java](<C:/Users/LeTran/FPT University/2026_FALL/STEMotion/.audit/stem-phase-2026-11-05/phase-03/STEMotion_BE/src/main/java/com/stemotion/integration/client/AiGateway.java>) — Source: toàn văn embedded/remote route, audit và lỗi | `55e99e72d8304ee930831ffc1fb680dabb0662c6598f3357d210b070e034be8c` |
| 22 | [models.py](<C:/Users/LeTran/FPT University/2026_FALL/STEMotion/.audit/stem-phase-2026-11-05/phase-03/STEMotion_BE/services/ai/app/models.py>) — Source: toàn văn inputs, bounds và extra=forbid | `f2f97ea863b0b498dc027eddd5c88d3324d71ca356ef6270460d0501eefa8569` |
| 23 | [schemas.py](<C:/Users/LeTran/FPT University/2026_FALL/STEMotion/.audit/stem-phase-2026-11-05/phase-03/STEMotion_BE/services/ai/app/schemas.py>) — Source: toàn văn schema conversion/strict/null compatibility | `c733622f49636dcaa2bab78a694de67ac139a00027137a3a4f71ba27692a4a4d` |
| 24 | [prompting.py](<C:/Users/LeTran/FPT University/2026_FALL/STEMotion/.audit/stem-phase-2026-11-05/phase-03/STEMotion_BE/services/ai/app/prompting.py>) — Source: toàn văn prompt construction | `14807695fab33177fa2a8bcbfce45e05c9c5bfe497584947254931482faeaaa1` |
| 25 | [system.txt](<C:/Users/LeTran/FPT University/2026_FALL/STEMotion/.audit/stem-phase-2026-11-05/phase-03/STEMotion_BE/services/ai/app/prompts/system.txt>) — Source: toàn văn profile secondary/high school hiện hữu | `10c8c846a3ffd0415115c2f55ac2f3080c0d6eb07a33720334a1986d955c1d60` |
| 26 | [providers.py](<C:/Users/LeTran/FPT University/2026_FALL/STEMotion/.audit/stem-phase-2026-11-05/phase-03/STEMotion_BE/services/ai/app/providers.py>) — Source: locators provider classes/task routing/env names; không đọc key runtime | `b181cfe536a7a69c30bb2c639c7e13a07a7a6648c3991d2b28e4d37cbc1bc1c9` |
| 27 | [requirements.lock](<C:/Users/LeTran/FPT University/2026_FALL/STEMotion/.audit/stem-phase-2026-11-05/phase-03/STEMotion_BE/services/ai/requirements.lock>) — Lockfile: đọc các pin đầu file, định danh toàn file bằng checksum; không cài/chạy | `58dddea67399405402933de681ca71e1be33bd5420e97d3bde03c4d7bf95b5ed` |
| 28 | [test_contract.py](<C:/Users/LeTran/FPT University/2026_FALL/STEMotion/.audit/stem-phase-2026-11-05/phase-03/STEMotion_BE/services/ai/tests/test_contract.py>) — Source test: phần đầu fake provider/auth/usage; không chạy | `652e1af26d599eea90465272393c6212fcdc78c841f959c7073ce5b95c2479c0` |
| 29 | [LibraryController.java](<C:/Users/LeTran/FPT University/2026_FALL/STEMotion/.audit/stem-phase-2026-11-05/phase-03/STEMotion_BE/src/main/java/com/stemotion/domain/library/controller/LibraryController.java>) — Source: toàn văn query params/list/detail/filters | `edf96742edfca73b9eda5a920ffb9b5c9048bbe3e394bbeae8918ccbfce593d8` |
| 30 | [LibraryDtos.java](<C:/Users/LeTran/FPT University/2026_FALL/STEMotion/.audit/stem-phase-2026-11-05/phase-03/STEMotion_BE/src/main/java/com/stemotion/domain/library/dto/LibraryDtos.java>) — Source: toàn văn item/legacy constructor | `47123aac0dd79f5dbe99761a844c64026804133de2232c4c2e157b608a7c80fe` |
| 31 | [LibraryService.java](<C:/Users/LeTran/FPT University/2026_FALL/STEMotion/.audit/stem-phase-2026-11-05/phase-03/STEMotion_BE/src/main/java/com/stemotion/domain/library/service/LibraryService.java>) — Source: toàn văn snapshot path và in-memory filter/page | `8e5472a1b90600a5c19fa9702ade35f9bbd7326c0e71cb6052f1a4bd42e55a39` |
| 32 | [PagedResponse.java](<C:/Users/LeTran/FPT University/2026_FALL/STEMotion/.audit/stem-phase-2026-11-05/phase-03/STEMotion_BE/src/main/java/com/stemotion/core/web/PagedResponse.java>) — Source: toàn văn array + pagination headers | `bbbade8a61b7c9ec7990ce256da8ef2f2eeb36a1d933029480cec5210e4cdfa5` |
| 33 | [PublicationRepository.java](<C:/Users/LeTran/FPT University/2026_FALL/STEMotion/.audit/stem-phase-2026-11-05/phase-03/STEMotion_BE/src/main/java/com/stemotion/domain/production/repository/PublicationRepository.java>) — Source: đầu file query/filter/count và locators hiện hữu | `3fc2904fd514a0e1fe087880b48d5c34f5ab905453268679c695a9dedc6edd87` |
| 34 | [StemArtifactService.java](<C:/Users/LeTran/FPT University/2026_FALL/STEMotion/.audit/stem-phase-2026-11-05/phase-03/STEMotion_BE/src/main/java/com/stemotion/domain/stem/StemArtifactService.java>) — Source: capture brief/editions/publicMetadata và publicationMetadata/status locators | `5b2ffbbc9d9d78d31bf661e995baacc3c0fc936159c054c56e6d480a8d506ee6` |
| 35 | [libraryService.ts](<C:/Users/LeTran/FPT University/2026_FALL/STEMotion/.audit/stem-phase-2026-11-05/phase-04/STEMotion_FE/src/services/libraryService.ts>) — Source: toàn văn filters/list/detail types và request | `ded3b1638a5f3545ae1e4a752658e55c7a8217ff063197ca380a44689dcec8c8` |
| 36 | [scriptService.ts](<C:/Users/LeTran/FPT University/2026_FALL/STEMotion/.audit/stem-phase-2026-11-05/phase-04/STEMotion_FE/src/services/scriptService.ts>) — Source: AI operation/decision/generate/accept scene validation locators | `2a18c03b472b5dcee612d419274ed21f3e9b875b6bab0c7835d22d9859a56fef` |
| 37 | [WriterStudio.tsx](<C:/Users/LeTran/FPT University/2026_FALL/STEMotion/.audit/stem-phase-2026-11-05/phase-04/STEMotion_FE/src/components/views/WriterStudio/WriterStudio.tsx>) — Source: AI accept/decision UI locators, không tự save | `8c72fc534f3ec00f5142c3625f1c19c2e66fb85bdde510e51d05900e0493f34c` |
| 38 | [MediaLibraryView.tsx](<C:/Users/LeTran/FPT University/2026_FALL/STEMotion/.audit/stem-phase-2026-11-05/phase-04/STEMotion_FE/src/components/views/MediaLibrary/MediaLibraryView.tsx>) — Source: subject fetch/local search/card locators | `a73e5b135d1390bf83a040b505a56dc62f6327b9dcddb04e38aab0d258c10a62` |
| 39 | [MediaLibraryView.test.tsx](<C:/Users/LeTran/FPT University/2026_FALL/STEMotion/.audit/stem-phase-2026-11-05/phase-04/STEMotion_FE/src/components/views/MediaLibrary/MediaLibraryView.test.tsx>) — Source test: existing retry/race/filter metadata locators; không chạy | `e1987eda0418aa065e74f5f20ce2747b0888043408ff997c686b1b3cc5b6756d` |

## 10. Bàn giao và dừng

**Chỉ file thẻ này được tạo/bổ sung. Người dùng đã chọn A cho Q01–Q10 có điều kiện; §9 đã bổ sung hash. Chờ Bond kiểm lại trước khi soạn prompt thực thi riêng. Không P6 code/SQL/test/build/app/dependency install/commit/push/merge/deploy, không thay clone/source hoặc đóng P0–P5. DỪNG.**
