# C.1 — Xác minh P0

10/11/2026, Asia/Saigon. Đối chiếu evidence sau Gate 1. Quyết định đóng thuộc người dùng.

## Expected / actual

| Kiểm tra | Kết quả đối chiếu |
|---|---|
| BE | Log Maven ghi 401 reported, 17 skipped, 0 fail/error, tức 384 pass. |
| FE | Vitest 184 pass. |
| Worker | Node native 34 pass, gồm HTTP subtests. |
| E2E | Playwright 27 pass trên DB cô lập. |
| AI P0 | 69 contract pass trong env lệch hoặc thiếu 41/77 mục. Exact-lock tại P0 ghi NOT RUN. |
| Snapshot | BE f6c6f0b, FE 9fe67cf, worker 3344a29. Worker gốc 022ea0a là mốc riêng. |
| Live BE | f6c6f0b là quan sát P0 và R1.1. Trạng thái live chưa được đo lại trong C.1. |

## C-Q01 A — Gap dependency được bao phủ bằng evidence P6

Hai file lock P0/P6 trùng SHA-256 và đều có 77 pin. P6 ghi đủ 77/77 pin, 0 mismatch, pip check đạt, Windows/Python 3.12.3. Lượt P6 `ai-exact-baseline-native` có XML 69 ca, 0 fail/error/skip. Git diff `f6c6f0b..4fd7089 -- services/ai` rỗng, tức phần AI của snapshot đầu P6 khớp source P0 trước delta P6. 69 định danh ca XML baseline P6 đều nằm trong XML 85 ca cuối P6. P0 có log tổng hợp 69 ca, không có XML riêng trong thư mục audit P0 để đối chiếu danh tính từng ca trực tiếp.

**Disposition đề nghị:** Dùng lượt baseline P6 để giải gap dependency cho bộ AI contract local. Giữ kết quả lịch sử của lần P0 là NOT RUN đối với exact-lock. Evidence dùng fake/offline, không nâng thành xác minh provider thật, production hoặc rerun toàn bộ P0. P7.1 là evidence bổ sung cho snapshot P6 và không thay các denominator P0.

## Evidence và SHA-256

| File | SHA-256 |
|---|---|
| [SETUP_REPORT.md](<C:/Users/LeTran/FPT University/2026_FALL/STEMotion/STEMotion_BE/docs/stem/phases/phase-00/SETUP_REPORT.md>) | `abe6b09022171ce23e9061ce701828865ea42bcddc2159c71599364cebabdaa7` |
| [TEST_BASELINE.md](<C:/Users/LeTran/FPT University/2026_FALL/STEMotion/STEMotion_BE/docs/stem/phases/phase-00/TEST_BASELINE.md>) | `0a9d7d285f0e2d754047f16e38e1bef53d132df687b266c00c4ec4b1f1782f8e` |
| [BASELINE_MANIFEST.json](<C:/Users/LeTran/FPT University/2026_FALL/STEMotion/STEMotion_BE/docs/stem/phases/phase-00/BASELINE_MANIFEST.json>) | `1860a8848391b946688e1899844c783e30f4075fc1654e0550dee587c4285528` |
| [be-tests-native.log](<C:/Users/LeTran/FPT University/2026_FALL/STEMotion/.audit/stem-phase-2026-11-04/phase-00/be-tests-native.log>) | `afc084ae4025ce079e8677cb63913dcfb35017d86c6e8457f9be6edf147a9c84` |
| [fe-tests.log](<C:/Users/LeTran/FPT University/2026_FALL/STEMotion/.audit/stem-phase-2026-11-04/phase-00/fe-tests.log>) | `726f891e8d79f2fe96e8ab1e2509dc8fd704dbed00e5503f6234d78ced81ffbd` |
| [worker-tests-native.log](<C:/Users/LeTran/FPT University/2026_FALL/STEMotion/.audit/stem-phase-2026-11-04/phase-00/worker-tests-native.log>) | `1b3922ebff89bb913584780cb61363038806a1e25a08e312cbc48071d2956a46` |
| [e2e-playwright.log](<C:/Users/LeTran/FPT University/2026_FALL/STEMotion/.audit/stem-phase-2026-11-04/phase-00/e2e-playwright.log>) | `495ba0499198a0dd13d16bcccdeb799216d104aba955e27b0504675c3dee1ff8` |
| [ai-tests-native.log](<C:/Users/LeTran/FPT University/2026_FALL/STEMotion/.audit/stem-phase-2026-11-04/phase-00/ai-tests-native.log>) | `36ea930c2683566d191e4c29085c6684017e9419e4ddca79112095123187bc99` |
| [ai-dependency-check.json](<C:/Users/LeTran/FPT University/2026_FALL/STEMotion/.audit/stem-phase-2026-11-04/phase-00/ai-dependency-check.json>) | `259734191ecb009151f2b62b603d420c8011b3e28139a518b21cf990742fc761` |
| [render-live-readonly.json](<C:/Users/LeTran/FPT University/2026_FALL/STEMotion/.audit/stem-phase-2026-11-04/phase-00/render-live-readonly.json>) | `bbcb40b4bd152207fddc65fa319262e8f370852545e661257a992b8806155712` |
| [requirements.lock](<C:/Users/LeTran/FPT University/2026_FALL/STEMotion/.audit/stem-phase-2026-11-04/phase-00/isolated/STEMotion_BE/services/ai/requirements.lock>) | `58dddea67399405402933de681ca71e1be33bd5420e97d3bde03c4d7bf95b5ed` |
| [EXACT_LOCK_PREFLIGHT.json](<C:/Users/LeTran/FPT University/2026_FALL/STEMotion/.audit/stem-phase-2026-11-05/phase-03/STEMotion_BE/docs/stem/phases/phase-06/EXACT_LOCK_PREFLIGHT.json>) | `2b0f93e66bd361483cf1b8b56364c9a5d7160f5d52e4e27c4e06dbe3beb0a28b` |
| [EXACT_LOCK_INVENTORY.json](<C:/Users/LeTran/FPT University/2026_FALL/STEMotion/.audit/stem-phase-2026-11-05/phase-03/STEMotion_BE/docs/stem/phases/phase-06/EXACT_LOCK_INVENTORY.json>) | `06d4be9fe69f9b22bbd300062218f8b44196638c20e94a40328c300149478cd8` |
| [ai-exact-baseline-native/results.xml](<C:/Users/LeTran/FPT University/2026_FALL/STEMotion/.audit/stem-phase-2026-11-05/phase-03/STEMotion_BE/.audit/stem-p6/ai-exact-baseline-native/results.xml>) | `3a8aef972e36a06be48dfa94d309979907b2311ecfda9a680c8df465974ef15e` |
| [TARGET_CONFIRMATION_R1.md](<C:/Users/LeTran/FPT University/2026_FALL/STEMotion/.audit/stem-phase-2026-11-05/rollout-gate-01/TARGET_CONFIRMATION_R1.md>) | `56f7de6e2468ccb0218898dc8a3c438f826a8ac005969cf77600db1675b74560` |
