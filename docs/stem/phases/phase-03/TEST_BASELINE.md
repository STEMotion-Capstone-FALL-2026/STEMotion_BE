# P3 — Kết quả kiểm thử thực tế

05/11/2026. BE-only trên clone cô lập f6c6f0b. Source/test hashes tại [SOURCE_MANIFEST](SOURCE_MANIFEST.json); không commit mới. **Không đồng nghĩa nghiệm thu STEM end-to-end hoặc xác minh chuyên môn.**

| Lượt | Actual | Evidence / giới hạn |
|---|---|---|
| Preflight DB baseline native |8 tests pass |environment-preflight-native.log/json; DB mới,24 migrations cũ, không P3 schema lúc đó |
| P3 final handoff BE regression |**439 reported: 422 pass, 17 skipped,0 failures/errors;80 XML suites** |[p3-handoff-regression-native.log](p3-handoff-regression-native.log), [summary](HANDOFF_REGRESSION_SUMMARY.json) |
| Trong cùng lượt trên: P3 mới |**38 pass**: StemPostgresTest29, StemValidatorTest9 |Đã nằm trong439 reported, **không cộng thêm** |
| Schema / prerequisites |25 migrations pass; Hibernate baseline validate;9 STEM tables RLS flags |V1–22 +9000/9001 một lần +91000 test-only; không production equivalence |
| API workflow offline |PASS các subcase ở controller/service/transaction tests |Standalone MockMvc dùng real service/DB/role checks; fake render/mail/AI boundaries. Không phải live HTTP/browser/MP4 E2E |
| STRICT |Pure evaluator unit tests + từ chối settings STRICT |Không persist mode STRICT, không ACTIVE/VERIFIED fixtures, không positive scientific STRICT end-to-end |
| FE/worker/Python exact-lock/real AI/TTS/mail/render |**NOT RUN tại P3** |Không cài dependency hoặc thay lock, không lấp điểm mở P0 |
| Semantic/presentation/render hash, AT21, snapshots, publish/library |**NOT RUN tại P3** |P4/P5/P6; transport receipt digest không phải semantic hash |
| Shared DB/bridge/release |**NOT RUN / ngoài phạm vi** |Không preflight/rehearsal/shared connection |

17 skip giữ cùng loại với P0: SupabaseRegistrationTest1, SupabaseSchemaValidationTest1, SupabaseScriptFlowTest3; LocalAiDecisionE2eTest4, LocalAiUsageE2eTest4, LocalStudioE2eTest4. Không gọi skip là pass. Kết quả P0/P1/P2 không bị cập nhật; P0AI69pass vẫn chỉ env lệch41/77.

## Lỗi/lượt trước được giữ nguyên

- Clone Git/MSYS sandbox thất bại; retry đúng clone local qua cơ chế duyệt thông thường thành công. Không working-tree copy hoặc .env thật.
- `environment-preflight.log`: PG khởi động nhưng sandbox không xác nhận kết nối trong10s; không PASS. Native retry xanh. `compile-check.log`: runner sandbox kẹt ở Mockito, đã dừng đúng process tree P3 tạo, không gọi là pass.
- `p3-tests-native.log`:8unit pass,13 test setup error do username fixture mới không đúng constraint auth hiện hữu; sửa fixture, không nới constraint. r2:21pass.
- `p3-regression-native.log`: lỗi fixture Clip chưa có metadata thật theo trigger, và assertion RLS chưa xét root cause. Sửa test fixtures/assertion; không sửa trigger/RLS để xanh.
- `p3-required-cases-native.log`: assertion catalog phụ thuộc vị trí phần tử khi fixture khác thêm source; sửa chọn theo ID. Final required-cases37pass, không cộng lặp vào439.
- Lệnh handoff-regression đầu tiên chưa chạy do auto approval review gặp usage limit; sau người dùng yêu cầu tiếp tục, retry qua **cùng cơ chế** đã chạy thành công. Đây là lỗi cơ chế duyệt, không kết luận hành động unsafe hoặc test fail.

## Phạm vi bằng chứng

[CASE_COVERAGE](CASE_COVERAGE.md) ánh xạ52ca P2 bằng PASS_SUBCASE/PARTIAL/DOCUMENT_REVIEW/NOT_RUN, không biến52dòng thành52test. Test fixtures đều TEST_ONLY; source/outcome/policy/brief DRAFT/UNVERIFIED. Dummy CLIP metadata qa=PENDING/url localhost không phải MP4 thật, không phải chứng cứ khoa học. Những test Java AI trong BE regression là unit/fake có sẵn; không chạy Python/exact-lock/provider thật.

Runner [run_tests.py](run_tests.py) dùng Java17/Maven3.9.6 theo source/cache, Maven offline, env allow-list, .build-p3 riêng. Chỉ chạy trên clone đã duyệt; không tái sử dụng URL/env/server người dùng. [Cleanup](CLEANUP_VERIFICATION.json) xác nhận không còn PG test do phase tạo. Không nâng package để né lỗi.
