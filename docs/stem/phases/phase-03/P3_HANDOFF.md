# P3 — Bàn giao backend tối thiểu STEMotion STEM track

**05/11/2026, Asia/Saigon — PARTIAL / BÀN GIAO LOCAL, CHƯA TỰ ĐÓNG P3. DỪNG TRƯỚC P4.**

Đã thực thi phạm vi BE-only được người dùng duyệt Q01–Q11 trên clone và PostgreSQL test cô lập. Có code, migration test, contract delta và test evidence; không production, không xác minh chuyên môn, không bật STEM end-to-end. Các phần coverage còn thiếu được ghi riêng, không lấy suite xanh để đóng toàn phase.

## 1. Vị trí mã và cách review

- Clone: `C:/Users/LeTran/FPT University/2026_FALL/STEMotion/.audit/stem-phase-2026-11-05/phase-03/STEMotion_BE`.
- Base/HEAD: **f6c6f0bdcdcc6bbaf1f1246f86678d3249dea8e1**; nhánh local **feat/stem-p3-isolated-20261105**. **Không có commit mới**, không push/deploy. Repo gốc đang dirty giữ nguyên, không merge/copy code ngược về đó.
- [Diff review](PATCH_P3.diff):17 file source/test trong scope,11 mới +6 sửa; gồm cả file untracked, không build artifacts. [Source hashes](SOURCE_MANIFEST.json) cho đúng source local đã test. Build output `.build-p3` riêng vẫn được giữ.
- [Contract delta](CONTRACT_DELTA.md), [inventory13/12](SCENE_TYPE_INVENTORY.md), [test ledger](MIGRATION_TEST_LEDGER.md), [migration hashes](MIGRATION_INVENTORY.json), [schema evidence](SCHEMA_TEST_MANIFEST.json), [test baseline](TEST_BASELINE.md), [52-case coverage](CASE_COVERAGE.md), [regression summary](HANDOFF_REGRESSION_SUMMARY.json).

Clone tạo từ Git objects bằng clone local không hardlink, không sao filesystem working tree hoặc `.env` thật. Đã xác nhận fresh target chỉ có.git trước materialization, đúng SHA, nhánh riêng và trạng thái sạch trước feature code. PG preflight native chạy thành công trước code sâu. Environment failure ban đầu không được gọi là PASS.

## 2. Expected / actual P3.1–P3.9

| Mục | Expected theo phạm vi | Actual / evidence | Giới hạn |
|---|---|---|---|
| P3.1 Domain/persistence | Topic/outcome/policy/brief, revision/version/provenance, CRUD DRAFT/MVP |7 logical catalog kinds versioned trong stem_catalog, typed DTO/domain records + JDBC repositories; brief/context/tasks/objectives/contributions aggregate JSONB, evidence riêng; referenced edition không overwrite, archive DRAFT có CAS; public chỉ read/gán MVP |Không public catalog CRUD đầy đủ, official curriculum seed, activation/verification. Mapping vật lý9table đã ghi trước SQL, không tự coi logical entity count là DDL count |
| P3.2 Migration test |M-B test-only, prerequisites/fixture transition/type/FK/RLS, fresh DB |V91000 trong test location riêng; V1–22 và9000/9001 giữ nguyên, áp một lần;25migrations, PG16.2, Hibernate baseline validate; drift wrong-type bị chặn; RLS9tables và explicit client grants vẫn không cho đọc/ghi |Không production SQL/reservation, không shared preflight/bridge/rehearsal; schema manifest là executed testDDL + observed assertions, không live schema dump |
| P3.3 Evidence/copy |ID map trong transaction, không copy approval, reorder/xóa/chèn/retry/rollback |ScriptService copy trả scene list + oldId→newId theo chính object đã lưu; copied evidence mới NEEDS_REVIEW, copiedFrom; keyed changeTarget retry, explicit retired evidence audit, orphan gốc được giữ; các test copy/map/rollback/reorder/insert/delete đã chạy |Không semantic fingerprint proof của reorder; phần đó P5. Legacy unkeyed retry theo contract cũ bị từ chối sau transition, không nhận receipt mới |
| P3.4 Submit/approve/Reviewer |Validator chung, existing approval enrich, active seat và atomic decision, minimal video hook |ScriptService submit/approve/requestChanges hooks; FeedbackService common guard trước CLIP/COMPOSITION; assessment nối đúng một existing decision trong cùng transaction; active REVIEWER không Admin bypass; outer note+assessment idempotency; legacy approval không tự có assessment |Rubric/assessment DRAFT/UNVERIFIED, chưa current scientific approval. Full render/snapshot/artifact/publish protection P5; không approve endpoint mới |
| P3.5 Mode/policy |Một mode workspace, pin policy, CAS/audit, OFF giữ lịch sử |OFF/ADVISORY settings, modeRevision CAS, allowed/default policy, old pin không backfill; legacy conflict cần explicit selection; unavailable pin có finding, không tự đổi brief |Không persist STRICT; không sửa nhãn/snapshot/publication lịch sử; actual rollout label tests P5/P8 còn NOT RUN |
| P3.6 Context |Một primary nullable draft, secondary có điều kiện, channel tách, legacy raw/suggestion |Typed context/channel, draft primary:null, field findings, rawLegacy preserved, legacySuggestion không persist tự động; DEPARTMENT_LIBRARY chỉ gợi ý channel; Writer chọn bằng CAS |P2 enum khởi đầu giữ IN_CLASS_SCREEN/GROUP_PRACTICE. PRE_CLASS/HOME_SELF_STUDY còn raw/giả thuyết; variants mâu thuẫn cần người viết/chuyên môn, chưa máy kết luận. Coverage full secondary variant còn PARTIAL |
| P3.7 Contract trước code |Exact DTO/idempotency/limits/errors/CAS/quyền và inventory13/12 |Contract delta/ledger/inventory đã ghi trước feature tương ứng, có bổ sung chi tiết trong cùng bước theo Q10; actual-byte cap262144, depth16, arrays/text/page/rate bounded,24h scoped receipts;400details/409/403/429; strict unknown/server-owned fields; historical parent02979f4^ xác nhận12 +COMPOSED=13 |Transport receipt digest không semantic hash. Worker opaque inputProps không có SceneType enum riêng; adapter/projector validation P4/P5. Không sửa Java/Python AI context contract thực thi P6 |
| P3.8 Tests |BE unit/API/transaction/schema và regression fake/local, không đếm52ca thành52test |**439 reported =422pass +17skip,0fail/error;80 XML suites. Trong đó38test P3 pass:29PostgreSQL/API/transaction +9unit**, cùng một lượt handoff, không cộng thêm |Standalone MockMvc + service transaction test, không live HTTP/JWT/browser/real MP4E2E mới. Subcases thiếu hoặc dời phase khác được ghi PARTIAL/NOT_RUN |
| P3.9 Fixtures |TEST_ONLY, không P1 seed thật, không VERIFIED giả |Synthetic users/seats/workspaces/scenes/catalog/policy/brief, DRAFT/UNVERIFIED; local dummy Clip metadata qa=PENDING cho trigger/FK branch test, fake render |Không dữ liệu người dùng thật, ý kiến chuyên môn, nguồn chính thức/linh kiện/kit giả, không tạo “verified hợp lệ” để test đạt |

## 3. Chi tiết triển khai đáng chú ý

Miền mới nằm ở `src/main/java/com/stemotion/domain/stem/`. Repository JDBC dùng cùng datasource/transaction với JPA workflow hiện hữu; không thêm unconditional JPA entities làm baseline validate đòi schema STEM trên profile cũ. Feature beans chỉ enable bởi **app.stem.enabled=true trong test profile cô lập**, mặc định absent/disabled; không sửa application/production config. Catalog dùng discriminator/version rows với parent/source refs và service kind checks; đây là persistence của logical Topic/TopicVersion/SourceEdition/Outcome/Profile/Policy/Rubric, không tuyên bố mọi bảng curriculum đã hoàn chỉnh. Grade/subject/outcome checks hiện ở metadata DRAFT và validator, chưa official curriculum catalog ba cấp.

Rule máy hiện là technical PRODUCT_RULE kiểm structure/references/completeness, không interpreter khoa học cho content_constraints hoặc rubric chưa được thẩm định. Topic constraints lưu có version/provenance; không chạy thêm một bộ luật topic cạnh policy. Không ngưỡng ít nhất hai môn hoặc bằng chứng “bốn chữ là đạt STEM”. Thiếu nguồn/lớp/task/context/safety vẫn lưu draft với finding; wrong shape/reference/ownership/revision được từ chối theo contract.

Chỉ sửa6file hiện hữu trong scope: WorkspaceAccess, GlobalExceptionHandler, ScriptDtos, ScriptService, FeedbackService và FeedbackController. Không patch RenderService/ClipQaService/PublishService/LibraryService, FE/worker, prompt/schema Python hoặc auth cutover. Video tests dùng artifact metadata tổng hợp đúng FK/trigger và render mock; không render/snapshot implementation P5.

C07 trước mắt bảo thủ NEEDS_REVIEW khi mutation, không timestamp/draftRevision làm semantic hash và không claim AT21. Copy mới không mang validation/assessment/approval sang phiên bản mới. OFF/ADVISORY legacy được phép theo guard workflow cũ nhưng không có CURRENT_VERIFIED_STEM. System Admin không có active Reviewer seat vẫn không ghi assessment STEM; request note-only legacy không tự trở thành STEM assessment.

## 4. Kiểm thử, lỗi và giới hạn thực tế

[TEST_BASELINE.md](TEST_BASELINE.md) liệt kê các lượt thật, lỗi môi trường/fixture/assertion và lần retry. Lượt bàn giao **p3-handoff-regression-native** BUILD SUCCESS trên đúng source cuối. Không cộng các lần chạy lặp vào439.17skip là opt-in cũ, không pass. Baseline P0 giữ nguyên384BEpass/17skip và AI69contract env lệch41/77; không viết lại các số cũ.

[CASE_COVERAGE.md](CASE_COVERAGE.md) giữ52case IDs P2 với actual subcase/limit. Các phần chưa có chứng cứ độc lập đầy đủ gồm full context-secondary variants, UI preview/autosave mới, semantic/hash/audio/timing/COMPOSED projector, artifact/job/callback/snapshot/publish/library, Java/Python grounding, live HTTP/browser E2E mới, shared-equivalence/rollout. Chưa chạy không gọi PASS. Pure STRICT evaluator unit xanh không chứng minh production STRICT hoặc nội dung đúng.

Một lỗi auto approval review do usage limit đã chặn lượt chạy cuối đầu tiên trước thực thi. Sau người dùng yêu cầu tiếp tục, đã retry **cùng cơ chế** và chạy thành công; không bypass approval. Không còn action bị chặn bởi lỗi này ở bàn giao.

## 5. Baseline, bảo toàn và cleanup

| Mốc | Giá trị / ý nghĩa |
|---|---|
| Report4 tự ghi |BE7f207bb, FEaa4d722, worker7e5aeea; khác main/live |
| Checkout gốc kiểm kê P0 / bảo toàn P3 |BEf6c6f0b, FE9fe67cf, worker022ea0a; không checkout/pull/fast-forward repo gốc |
| Worker snapshot test P0 / inventory read-only P3 |3344a29, chỉ đọc git object; không build/test/patch worker P3 |
| Live quan sát P0 |BEf6c6f0b qua Render read-only ngày04/11; không kiểm tra live lại P3 |

[INPUT_BASELINE.json](INPUT_BASELINE.json) và [PRESERVATION_REVIEW.json](PRESERVATION_REVIEW.json): input docs/reports không đổi; tracked files không nhạy cảm, HEAD và status của3repo gốc giữ nguyên. .gitignore/asset/local work không bị ghi. Không hash/đọc secret/env thật để tạo bằng chứng; không suy status là chứng thực nội dung mọi untracked asset.

[CLEANUP_VERIFICATION.json](CLEANUP_VERIFICATION.json): kiểm read-only25data directories đã ghi trong P3logs, **không còn PostgreSQL thuộc các directory này**. Chỉ dừng runner sandbox P3 bị kẹt do chính lượt tạo; không kill app người dùng hoặc xóa baseline P0. Logs/build artifacts của clone giữ để review. Không có shared/deployment cần rollback.

[OUTPUT_MANIFEST.json](OUTPUT_MANIFEST.json) hash package trừ chính manifest và log test đang không còn ghi; source hashes riêng trong SOURCE_MANIFEST. File source trong clone chưa commit, phải review diff; không coi HEAD f6c6f0b chứa code mới.

## 6. Trạng thái chuyên môn và dependency

| Cấp / mẫu | Trạng thái còn nguyên | Phần cần bổ sung |
|---|---|---|
| Tiểu học / lớp4 dự kiến giữ đá |PARTIAL — DRAFT/UNVERIFIED |G-SOURCE/G-CURRENCY/G-EXPERT; lớp/outcome/context/rubric/an toàn chưa chốt |
| THCS / lớp8 dự kiến cách nhiệt |PARTIAL — DRAFT/UNVERIFIED |Căn cứ/hiệu lực/chuyên môn, task/biến đo và mapping môn |
| THPT / lớp11 dự kiến LED DC |PARTIAL — DRAFT/UNVERIFIED |Các gap trên +G-TECH kit/datasheet/an toàn, G-MAPPING Toán11 riêng/Công nghệ/Tin học chưa chốt |

D01–D05, A và (a), hướng C03–C08/CTX-01/BR-01 được giữ theo thẻ/lệnh duyệt. C03 candidate UC43/44 và FR61–68 chưa gán chính thức; authFR56–60 vẫn đề xuất. C01/C02/cloudFlywayOFF/sharedbridge/reservation production vẫn chưa đóng. Native drawio export/source chính thức và FR38/Word/BM/T12a chưa đồng bộ, cần duyệt file/thao tác riêng. Không sửa input để hợp thức hóa code.

P0AI exact-lock **NOT RUN**,41/77 lệch/thiếu, vẫn blocker P6; không rerun Python/AI để lấp gap. Không liên hệ giáo viên/người khác, không tạo phản hồi giả, không nâng P1 lên VERIFIED/ACTIVE, không dùng GDTX/dự thảo/bài tổng hợp thay căn cứ đúng.

BM04 không đổi owner/gia hạn/nhận hoàn tất: RF09/10 **17/11/2026**, RF11/12/13 **24/11/2026**, RF08YouTube **29/11/2026**.

## 7. Phạm vi ngoài kế hoạch / rollback / đề nghị tiếp theo

Không có patch feature ngoài file/service đã duyệt. Các chi tiết kỹ thuật được chốt trong Q10 gồm JDBC/JSONB physical mapping, dải test91000–91009 và auth fixtures dùng một lần; không ngụ ý DDL production. Contract delta ban đầu liệt kê rộng context rồi đối chiếu giữ đúng2mã P2 trước bản bàn giao; nhật ký clarification được giữ, không thêm context từ master vào sản phẩm. Runner/log/hash/patch là evidence phụ trợ trong gói phase03, không phase mới hoặc code ở repo gốc.

Rollback local: dừng dùng feature/test profile, giữ code/diff/log để review; nếu cần revert thì chỉ đúng phần P3 trên clone theo yêu cầu. Không reset/stash/clean repo gốc, không down/drop shared, không sửa checksum/history, không xóa review/evidence/publication. OFF là rollback gate, không schema rollback/certification.

Đề nghị Bond review contract/physical mapping và diff17file, các PARTIAL trong52case, trước khi người dùng quyết bước tiếp theo. P4 chỉ được đề nghị theo một thẻ/prompt riêng, cần bảo toàn FE dirty assets và dùng contract cuối P3. P5 chịu trách nhiệm projector/AT21/snapshot/artifact gates; P6 AI/library; shared/release cần phạm vi riêng. Nếu muốn hoàn thiện thêm coverage P3 còn thiếu, trình phần bổ sung cụ thể, không tự coi phase đã đóng.

**Bàn giao P3 PARTIAL. Không tự đóng P0/P1/P2/P3, không commit/push/deploy, không mở P4. DỪNG.**
