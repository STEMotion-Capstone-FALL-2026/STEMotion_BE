# C.1 — Xác minh P4

10/11/2026, Asia/Saigon. Đối chiếu evidence sau Gate 1. Quyết định đóng thuộc người dùng.

## Expected / actual

- Handoff takeover ghi **216 FE pass** và typecheck/build đạt. Hai ca live ban đầu FAIL vì BE trả 500. Evidence fix A của BE ghi **41 pass** trên 3 file source/test, và lượt live sau fix **2 pass** qua full Spring context cô lập. Hai lượt trước/sau giữ riêng.
- HTTP audit của `PUT …/stem-settings` ghi 200/409/200/200, body keys dùng `baseRevision`, FE dùng `modeRevision` làm alias nội bộ. 409 là stale revision trên cùng counter workspace. P7.1 lưu 2 live P4/G06 như denominator riêng.
- Backup FE `4ddba1d` và parent `4dfa65e` nằm ngoài ancestry của local main `9fe67cf` và remote main `88570f2` theo Git object có sẵn. Kiểm tra ancestry chỉ xác nhận hai commit, không xác nhận từng patch tương đương. Remote BE main đã đổi nên tích hợp BE phụ thuộc review ref riêng. Create flow nằm ngoài scope P4.

**Disposition đề nghị:** Có evidence implementation cô lập và integration G06 sau fix để trình theo C-Q04 A. Dời merge sang R4 và giữ blocker ref BE ở phần phụ thuộc.

## Evidence và SHA-256

| File | SHA-256 |
|---|---|
| [P4_HANDOFF.md](<C:/Users/LeTran/FPT University/2026_FALL/STEMotion/.audit/stem-phase-2026-11-05/phase-04/STEMotion_FE/docs/stem/phases/phase-04/takeover-codex/P4_HANDOFF.md>) | `96d48854911ae9deb66dcabe9072e5809b4e257afbd93d3c8e52f62b42bbb2d0` |
| [TEST_BASELINE.md](<C:/Users/LeTran/FPT University/2026_FALL/STEMotion/.audit/stem-phase-2026-11-05/phase-04/STEMotion_FE/docs/stem/phases/phase-04/takeover-codex/TEST_BASELINE.md>) | `7a993e9bf4e0aa4216fdf5e8128cd6065e485a39f5608caebb8da1ae772691f8` |
| [G06_CONTRACT_CONFLICT.md](<C:/Users/LeTran/FPT University/2026_FALL/STEMotion/.audit/stem-phase-2026-11-05/phase-04/STEMotion_FE/docs/stem/phases/phase-04/takeover-codex/G06_CONTRACT_CONFLICT.md>) | `01cd3689c702c26be3f6753edb08e77e314f6db7d59e4497ec58f23f2df6d441` |
| [BE_BLOCKER_FIX_HANDOFF.md](<C:/Users/LeTran/FPT University/2026_FALL/STEMotion/.audit/stem-phase-2026-11-05/phase-03/STEMotion_BE/docs/stem/phases/phase-04/be-blocker-fix/BE_BLOCKER_FIX_HANDOFF.md>) | `e6187c882f26df75afb0deb4f18fed318fecdc2bdaf1cb238d94bd6d9b712584` |
| [after.json](<C:/Users/LeTran/FPT University/2026_FALL/STEMotion/.audit/stem-phase-2026-11-05/phase-03/STEMotion_BE/docs/stem/phases/phase-04/be-blocker-fix/after.json>) | `d61d0185c4b65f5d91f6d52101fb114b3d843ebb50e7a5bd7279118e15658743` |
| [P4_RUN_RESULT.json](<C:/Users/LeTran/FPT University/2026_FALL/STEMotion/.audit/stem-phase-2026-11-05/phase-03/STEMotion_BE/docs/stem/phases/phase-04/be-blocker-fix/P4_RUN_RESULT.json>) | `bc477c462c5fde2200b23c9e2e516c7fce9a95251596a2bd7106ad4537ac8c7c` |
| [p4-q10-results.json](<C:/Users/LeTran/FPT University/2026_FALL/STEMotion/.audit/stem-phase-2026-11-05/phase-03/STEMotion_BE/docs/stem/phases/phase-04/be-blocker-fix/p4-q10-results.json>) | `9d5f62dfc9edf97eeaec526d8b0f46ec66100be273e92f3fda919df27902e2dd` |
| [p4-http-audit.json](<C:/Users/LeTran/FPT University/2026_FALL/STEMotion/.audit/stem-phase-2026-11-05/phase-03/STEMotion_BE/docs/stem/phases/phase-04/be-blocker-fix/p4-http-audit.json>) | `c7233ebc2e15a97649dd6b9c52ee2f8de33bf44127863cbe0e143648ede33508` |
