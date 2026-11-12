# Gói sửa BE blocker — kết quả test riêng

05/11/2026, Asia/Saigon. Phương án A đã được duyệt; chỉ local, chưa commit. Không ghi đè số liệu/log P3/P4.

| Suite | Before sửa | After sửa / lượt bàn giao cuối | Evidence |
|---|---|---|---|
| New StemFullContextRegressionTest | **3fail,0error,0skip**: GET settings, POST create, legacy submit đều500NPE đúng workspace/create/lock | **3pass**, real main Spring/PET/CGLIB/JWT/HTTP/embeddedPG | before.log/json, after.log/json + isolated Surefire XML |
| P3 gốc | Baseline38pass giữ nguyên, không dùng như kết quả mới | **38/38pass**,29StemPostgresTest+9StemValidatorTest;0failure/error/skip; names/source giữ nguyên | after.json, TEST_GATE_RESULTS.json |
| Tổng cùng lượt BE after | Không cộng lượt before vào count | **41pass =38cũ +3mới**,0skip | .audit/be-blocker-fix/after/surefire-reports; after.log BUILD SUCCESS |
| Existing FE P4 live source → BE sau sửa | P4 baseline2FAILED vẫn giữ nguyên | **2/2pass**,1file: P4-Q10 script flow + G06 | p4-q10-results.json, p4-q10.log |
| DB state sau live flow | DB test mới |1assessment/1approved decision/1approval receipt; settings revision3;3mode audit dùng baseRevision,0modeRevision;0verified assessment;25migrations | P4_DB_AUDIT.json |
| Before/after same new regression source | SHA256 `95763fb4234d062043de978fef23df1a9245b463b8f53e897bd45ecfa3c6cfa3` | Cùng SHA256, không sửa assertions/source sau lượt đỏ | REGRESSION_SOURCE_BEFORE.json, BEFORE_GATE.json, SOURCE_MANIFEST.json |
| Bảo toàn/cleanup | Snapshot trước code | PASS theo phạm vi cho phép;4DB contexts đóng, không còn owned PID/data dir process | PRESERVATION_CHECK.json, CLEANUP_CHECK.json |

41BE và2live là hai suite riêng; không cộng chúng vào439reported/38P3baseline hoặc216FEbaseline, không đếm lượt lặp. Chỉ chạy B-Q04A: **không full BE439 regression, không FE216 Unit/Component rerun**, không live video CLIP/COMPOSITION bổ sung, không render/AI/exact-lock/browser E2E.

## Before phải đỏ đúng lỗi

Trước production edit, đã chạy mới StemFullContextRegressionTest trên source P3 nguyên trạng. Full context/PET advisor,25migrations, fixture JWT đều setup thành công; cả3failures là assert200 nhận500. before.log giữ nguyên NPE `this.db.jdbc`/`this.db.json` tại StemService:98/46/33. BEFORE_GATE.json xác nhận source hai production files chưa đổi trước lượt đỏ. Gate dừng nếu lỗi khác không bị kích hoạt vì không có setup/compile/test errors trong lượt này.

## BT01–BT09

| Gate | Expected | Actual / evidence cuối | Kết quả |
|---|---|---|---|
| BT01 |Full main context, feature-on, PET proxy/advisor thật,25migrations, SELECT1 |Before/after boot asserts không bỏ PET; same test source; `AopUtils.isAopProxy` + PersistenceExceptionTranslationAdvisor active, db.schema25 |PASS |
| BT02 |GETsettings200/fresh OFF revision0, shape revision/policyIds/defaultPolicyId |Before500NPE→After200 theo cùng test settingsReadThroughRealProxyReturnsFreshWorkspaceMode; không modeRevision trong settings response |PASS |
| BT03 |POSTcreate200/rev0/null primary/UNVERIFIED, same-key retry không duplicate |Before500NPE→After200; repeat body identical, DBbrief count1; test createThroughRealProxyIsNullableIdempotentAndRetainsGuards |PASS |
| BT04 |Read/catalog/frameworks/legacy draft giữ200; submit hook hếtNPE; negatives400/403/404/409 |New full-context test read/legacy submit; negative unknown field400, admin without Writer seat403, missing version404, stale brief409; live Admin without Reviewer403 |PASS |
| BT05 |38P3tests gốc pass đủ,0skip mới |29Postgres+9Validator pass; testcase-name sets bằng baseline XML; hash2source P3test không đổi |PASS |
| BT06 |Original P4 source đọc-only qua real BE; incomplete draft, reference400, UI sửa/save, submit, Reviewer atomic approval |Two tests original unchanged; HTTPaudit ghi400reference/unassigned policy, save200, submit200, Admin approve403/Reviewer200; final DB1assessment/1decision/1receipt |PASS |
| BT07 |FE alias→wire baseRevision; assign mode, stale409 không partial/default thuộc assignment |Settings PUT statuses200/409/200/200; base revisions0/0/1/2, modesADVISORY/OFF(stale)/OFF/ADVISORY; audit revision3/3events,0wire modeRevision |PASS |
| BT08 |Existing copy/evidence/rollback/active seat/idempotency24h/limits giữ |38P3 tests gồm các case này pass/source unchanged; không rewrite SQL/rules/connection; no snapshot/hash proof claimed |PASS |
| BT09 |Only approved source changes, FE/P3/P4 baselines preserved, owned DB/process closed |756existingBEfiles checked: chỉ2approvedproduction files đổi; FEclone319/originalBE695/originalFE201 hashes giữ; four shutdown markers, no owned process remaining |PASS |

## Runner / môi trường

Java17.0.18 + Maven3.9.6 wrapper distribution cached, Maven offline theo pom/source. Build mới `.audit/be-blocker-fix/before` và `after`; không đụng `.build-p3` hoặc Surefire/log baseline. Cùng stem-p3-test/E2eServer.Config/fresh embeddedPG16.2/V1–22+V9000/V9001+V91000 test-only; datasource/mapper/transactions giữ. E2e fakes, email off, localhost127.0.0.1/port0; env allow-list, không .env thật.

P4 orchestration mới thuộc BE, đã review trước execution tại ORCHESTRATION_REVIEW.md. Vitest source FE/cloned config/runner cũ giữ nguyên; config BE native loader, envDir=false, cacheDir BE riêng/fsModuleCache off, deps hiện có, output JSON/log BE mới. HTTPobserver chỉ chuyển tiếp fetch thật và ghi key names/status, không mock request/response hay log token/password. Java Temp launcher đóng Spring context qua stdin STOP rồi EmbeddedPGclose; không kill app người dùng/DB khác.

## Lỗi/giới hạn còn lại

- Before3fail là chứng cứ regression mong muốn, không còn trong after. **Không còn FAIL/NOT_RUN trong BT01–BT09 của gói sửa**.
- Preflight assertion đầu dùng Windows path separators khác POSIX; đã chuẩn hóa và ghi đủ38callsite trước production edit. Không có caller ngoài phạm vi, không thay source để né gate.
- Warning Flyway hỗ trợ PG16 và index/column tồn tại được giữ nguyên; không sửa migration hoặc checksums để làm sạch log.
- P5 hash/projector/timing/AT21/snapshot/render/publish và P6 AI/library vẫn ngoài phạm vi; shared/C01/C02/production rollout/official curriculum/expert verification chưa làm. New pass không tự đóng P3/P4.
