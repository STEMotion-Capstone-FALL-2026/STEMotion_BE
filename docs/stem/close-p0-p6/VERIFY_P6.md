# C.1 — Xác minh P6

10/11/2026, Asia/Saigon. Đối chiếu evidence sau Gate 1. Quyết định đóng thuộc người dùng.

## Expected / actual

| Evidence | Kết quả đối chiếu |
|---|---|
| BE | 360 pass, gồm 268 mandatory, 55 P6 và 37 affected. |
| FE | 258 pass, gồm 227 cũ và 31 P6. |
| AI | 85 pass trong XML, 69 cũ và 16 P6. Exact-lock 77/77, 0 mismatch, Windows/Python 3.12.3. |
| Live P4/G06 | 2 pass, báo riêng. |
| Live P6 FE/BE/Python | 2 pass, báo riêng vì nằm trong một ca orchestration BE. |
| Backup | GitHub đọc trực tiếp khớp BE `9c7fdf53195aeb8fcc4d05676a7180870504e409` và FE `4ddba1da34e4f403b0c13c8733cc1621a0f9a2ed`. |
| Logs và audit | Tệp local đã kiểm có maven log, Vitest JSON, AI XML, HTTP/DB audit P4 và P6 live results dưới `.audit/stem-p6` trong clone BE. Danh sách đường dẫn/hash bên dưới. Handoff backup ghi runtime evidence bị loại khỏi commit. |

P7.1 củng cố năm denominator trên với caveat BE Maven BUILD SUCCESS/JUnit 360 pass và runner aggregation FileNotFoundError. 24 AT đều PARTIAL. Không cộng các lượt rerun.

**Disposition đề nghị:** Giữ log/audit local-only theo C-Q06 B. **BLOCKER cho signoff scope P6** vì thẻ local `5dfc852e…` khớp snapshot P7, còn §9 yêu cầu bản Bond `b03138f2…`. Cần bản đối chiếu hoặc quyết định dùng phiên bản cụ thể. Giữ phần merge phụ thuộc review ref BE, scope R4 và các điều kiện release.

## Evidence và SHA-256

| File | SHA-256 |
|---|---|
| [P6_HANDOFF.md](<C:/Users/LeTran/FPT University/2026_FALL/STEMotion/.audit/stem-phase-2026-11-05/phase-03/STEMotion_BE/docs/stem/phases/phase-06/P6_HANDOFF.md>) | `6bfee19f66de9f5ef7b9ff9af456dd03a46f662b4f4792c70b13d1623e660331` |
| [TEST_BASELINE_P6.md](<C:/Users/LeTran/FPT University/2026_FALL/STEMotion/.audit/stem-phase-2026-11-05/phase-03/STEMotion_BE/docs/stem/phases/phase-06/TEST_BASELINE_P6.md>) | `55aca1cc9706a4abc1defabe025a0fb1c05440b9cc9a4fb4e561020fc44080da` |
| [TEST_SUMMARY_P6.json](<C:/Users/LeTran/FPT University/2026_FALL/STEMotion/.audit/stem-phase-2026-11-05/phase-03/STEMotion_BE/docs/stem/phases/phase-06/TEST_SUMMARY_P6.json>) | `2de8017e317f587e529e8d6ecb58a6f2c1165d713521c0860ff435fb8397f48c` |
| [EXACT_LOCK_INVENTORY.json](<C:/Users/LeTran/FPT University/2026_FALL/STEMotion/.audit/stem-phase-2026-11-05/phase-03/STEMotion_BE/docs/stem/phases/phase-06/EXACT_LOCK_INVENTORY.json>) | `06d4be9fe69f9b22bbd300062218f8b44196638c20e94a40328c300149478cd8` |
| [CONTRACT_P6.md](<C:/Users/LeTran/FPT University/2026_FALL/STEMotion/.audit/stem-phase-2026-11-05/phase-03/STEMotion_BE/docs/stem/phases/phase-06/CONTRACT_P6.md>) | `4139b1fdbe938d78f129d9e4ef28bfa4ce7eae83653d3264ab5deb486a815445` |
| [FIELD_MAPPING_P6.md](<C:/Users/LeTran/FPT University/2026_FALL/STEMotion/.audit/stem-phase-2026-11-05/phase-03/STEMotion_BE/docs/stem/phases/phase-06/FIELD_MAPPING_P6.md>) | `c2bc80cc347860439fc1f3804a0fa7371ddc18b218cb144c9f69d40738abd1fd` |
| [PHASE_06_SCOPE_DRAFT.md](<C:/Users/LeTran/FPT University/2026_FALL/STEMotion/.audit/stem-phase-2026-11-05/phase-06/PHASE_06_SCOPE_DRAFT.md>) | `5dfc852ee17fe4d26fb5e9a44ae0f1b62de2de8085095dddac42f5feeb80addf` |
| [BACKUP_HANDOFF.md](<C:/Users/LeTran/FPT University/2026_FALL/STEMotion/.audit/stem-p6-backup-2026-11-07/BACKUP_HANDOFF.md>) | `756aa56a0691267d9feff61927ff158bb17229c2ae5172ff79f81bbebd983c3d` |
| [be-final-complete/maven.log](<C:/Users/LeTran/FPT University/2026_FALL/STEMotion/.audit/stem-phase-2026-11-05/phase-03/STEMotion_BE/.audit/stem-p6/be-final-complete/maven.log>) | `a3d9c84617c80a39ca69fa26fc052adfb9412f62e2ac55448fa755a7349ec6cc` |
| [fe-handoff-final/unit-results.json](<C:/Users/LeTran/FPT University/2026_FALL/STEMotion/.audit/stem-phase-2026-11-05/phase-03/STEMotion_BE/.audit/stem-p6/fe-handoff-final/unit-results.json>) | `689684da145b14a4d13b88590f42c32c053d460c8c2120dc171da87c6cd70c93` |
| [ai-final-complete/results.xml](<C:/Users/LeTran/FPT University/2026_FALL/STEMotion/.audit/stem-phase-2026-11-05/phase-03/STEMotion_BE/.audit/stem-p6/ai-final-complete/results.xml>) | `48877dbce7d45fc33798c3ee0522b6d27a3ded5d626c297b9e77853e975050fb` |
| [p4-live-final-complete/live-results.json](<C:/Users/LeTran/FPT University/2026_FALL/STEMotion/.audit/stem-phase-2026-11-05/phase-03/STEMotion_BE/.audit/stem-p6/p4-live-final-complete/live-results.json>) | `eec2ceac1177404c6c13f8c2ea34bb1023d90904f710813a0b5db20038a2444b` |
| [p4-live-final-complete/DB_AUDIT.json](<C:/Users/LeTran/FPT University/2026_FALL/STEMotion/.audit/stem-phase-2026-11-05/phase-03/STEMotion_BE/.audit/stem-p6/p4-live-final-complete/DB_AUDIT.json>) | `0360227c645ca3171b4aff053330b13ffb028ca3696e142a1a9d4d9ea2e426cb` |
| [p4-live-final-complete/http-audit.json](<C:/Users/LeTran/FPT University/2026_FALL/STEMotion/.audit/stem-phase-2026-11-05/phase-03/STEMotion_BE/.audit/stem-p6/p4-live-final-complete/http-audit.json>) | `d7df278ad228b8ba74677d0cb47cd1328f0e159e532cd71d1a7d07bf40c28636` |
| [fe-live-be-final-complete/results.json](<C:/Users/LeTran/FPT University/2026_FALL/STEMotion/.audit/stem-phase-2026-11-05/phase-03/STEMotion_BE/.audit/stem-p6/fe-live-be-final-complete/results.json>) | `6df3321513d8648fd84be832912613f28ae7961f8770e871c19d3062bfed7619` |
