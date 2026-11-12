# P5 — Kết quả test bàn giao 06/11/2026

**Một lượt bàn giao cuối mỗi suite; không cộng các lượt lặp. Không MP4/browser/provider/shared/exact-lock.** Source hashes ở SOURCE_MANIFEST_P5.json; old test source/assertions/config/runner giữ bytes theo PRESERVATION_P5.json.

| Suite cuối | Actual | Log/output riêng dưới B/.audit/stem-p5 |
|---|---|---|
| BE handoff | **268 pass,0failure/error/skip;19 XML suites** | be-handoff/result.json, maven.log, build/surefire-reports |
| BE decomposition | **113P5 mới =92fingerprint +21full-context HTTP/PG**; **41bắt buộc =38 P3 + 3 blocker**; **114 existing regression** liên quan review/render/QA/publish/mapper | Không cộng38 lần hai trong41. Full439BE không chạy. |
| FE final | **227pass =216 cũ + 11 P5 mới**,40 files;0fail/pending | fe-final/unit-results.json, unit.log |
| FE static | Typecheck exit0, build exit0 | fe-final/results.json, typecheck.log, build.log; output/cache trong B audit, không FE original/deps |
| P4/G06 live nguyên bản | **2pass**, qua full Spring/JWT/owned localhost/embeddedPG26migrations, no mocked HTTP | p4-live-final/live-results.json, live.log, backend.log, http-audit.json, DB_AUDIT.json, result.json |

BE19 suites gồm original StemPostgresTest29 + StemValidatorTest9 + StemFullContextRegressionTest3; new StemFingerprintP5Test92 + StemArtifactP5Test21; existing ClipArtifactServiceTest5, CompositionServiceTest14, FrozenCompositionRenderTest4, PublicationFlowTest5, PublishAvailabilityTest1, RenderContractTest2, RenderRetryTest13, ReviewDecisionEventTest1, ClipQaServiceTest16, ReviewHistoryServiceTest1, WebFirstReviewTest9, ComposedSceneValidatorTest9, SceneMapperTest5, ScenePropsValidatorTest29. Source/assertions cũ không drop/skip/relabel.

New HTTP cases tách B01 CLIP và B02 COMPOSITION: decision/assessment/snapshot/job atomic, callback wrong hash/tamper/replay/old target/new retry attempt, publish correct snapshot/idempotency/legacy ack, snapshot-backed library sau mutable project edit, YouTube503no-write, QA FAIL hardOFF, send-back, Adminno-seat403, mode-race409, concurrent keyed review, source-version timing compatibility rồi semantic edit cần review, actual RLS denied role, immutability, byte cap/server claims, wrong-parent migration drift. Fake worker/callback không thành MP4 proof.

FE11newcases: ack/expectation whitelist, known mismatch no bypass, uncertain retry cùng key/no success giả, no server-owned claims, origin/mode/QA display, project-switch stale response, 400/403/404/409/429 recovery/input preservation. Không sửa P4 test source/config/runner. Existing PublishModal tests vẫn giữ behavior khi P5 surface không có; server mutation guard quyết cuối.

## Lỗi / retry đã giữ làm evidence, không tính vào số cuối

| Lượt | Kết quả / phân loại | Xử lý |
|---|---|---|
| compile-01 | Exit0 initial compile, chưa đủ xác nhận feature | Final Maven recompiles/source ở trên mới là handoff. |
| p5-first | Fingerprint90pass; Java HttpClient loopback Permission denied trước full context | Environment sandbox. Chạy đúng owned localhost suite bằng escalation được chấp nhận; không substitute shared/mock BE. |
| p5-localhost |90pure pass,11HTTP có3fail do new test kỳ vọng200 thay controller202enqueue/201publish | Sửa **new P5** assertions theo controller actual, không đổi endpoint behavior hoặc old assertions. |
| p5-second |90pure pass,18HTTP có2fail cùng lỗi500status read | New P5 service nhận detached owner proxy; reload project trong transaction. Old proxy blocker fix không bị đổi/disable. |
| fe/unit | Typecheck0; Vitest sandbox hết240s trước summary | NOT_COMPLETE/timeout, không đếm partial log như227pass. Owned process kết thúc; subsequent final run ngoài sandbox complete. |
| be-regression-01 |262pass/1fail: old RenderRetryTest nhãn tiếng Việt | Tôi đã làm sai encoding ở một lượt edit Python. Khôi phục đúng các dòng UTF-8 gốc RenderService/LibraryService; giữ test cũ; final old13retrytests pass. |
| be-final / fe-complete-01 |267BEpass /227 FE pass | Interim prior added P5 retry test/library type-final state; không cộng vào268/227 cuối. |

Không auto-review rejection còn tồn tại. Không cài/nâng dependency, đổi stack hoặc nới tests để né lỗi. Supporting orchestration mới nằm B/.audit/stem-p5, sanitized env chỉ dummy endpoints và fixture credentials TEST_ONLY; không load .env thật.

## NOT_RUN / PARTIAL có chủ đích

Real MP4/pixel/audio/renderer attestation, arbitrary visualPlan/layout/motion/asset proof; positive audio digest resolver/signed-URL inheritance; broad safe-theme inheritance; full13template/12block render acceptance; future hash-upgrade data migration; actual shared bridge/rollout, positive STRICT/verified expert cases; AI Java/Python grounding/exact-lock/P6; browser E2E; official Word/BM/diagram/T12a/chuyên môn. Unknown cases không inherit tự động. Không gọi pure hash specimens hoặc fake artifact manifest là những proof này.

Cleanup: CLEANUP_LOG_INVENTORY.json ghi12 owned PG starts/12shutdowns across actual rounds; CLEANUP_PROCESS_CHECK.json read-only xác nhận0process thuộc data dirs/audit runners. Live server exit0/OWNED_BACKEND_CLOSED. Không kill app người dùng hoặc xóa DB/evidence cũ.
