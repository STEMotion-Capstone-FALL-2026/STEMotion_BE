# C.1 — Xác minh P3

10/11/2026, Asia/Saigon. Đối chiếu evidence sau Gate 1. Quyết định đóng thuộc người dùng.

## Expected / actual

- Regression bàn giao có **422 pass / 17 skip / 0 fail hoặc error**, 439 reported trên 80 XML suites theo summary. 38 ca STEM P3 nằm trong denominator đó. Phạm vi là standalone MockMvc/service transaction và PostgreSQL cô lập.
- V91000 nằm trong `src/test/resources/db/stem-p3/`. Schema manifest đánh dấu test-only. G06 dùng wire `baseRevision` của counter workspace. Fix live HTTP về sau có evidence P4 riêng.
- Backup BE cô lập `9c7fdf5` và parent P5 `4fd7089` còn hiện diện. Kiểm tra ancestry cho thấy cả hai không nằm trong local `main` `f6c6f0b`. GitHub backup ref khớp. GitHub BE `main` hiện `837c200`, khác mốc R1 và thiếu commit object trong repo local. Chưa xác nhận ancestry hoặc thay đổi tương đương trên remote BE main.

**Disposition đề nghị:** Evidence implementation cô lập khớp phần được đối chiếu. C-Q04 A vẫn là cách xét phần implementation. Giữ blocker riêng cho kết luận về merge remote BE và cho tích hợp R4. Chưa có kết luận về rollout production.

## Evidence và SHA-256

| File | SHA-256 |
|---|---|
| [P3_HANDOFF.md](<C:/Users/LeTran/FPT University/2026_FALL/STEMotion/.audit/stem-phase-2026-11-05/phase-03/STEMotion_BE/docs/stem/phases/phase-03/P3_HANDOFF.md>) | `e2260959fff6f8a7cf92ca8f87ff8bd0ec9d7aff745d2b4bc5dc795af20ef13a` |
| [HANDOFF_REGRESSION_SUMMARY.json](<C:/Users/LeTran/FPT University/2026_FALL/STEMotion/.audit/stem-phase-2026-11-05/phase-03/STEMotion_BE/docs/stem/phases/phase-03/HANDOFF_REGRESSION_SUMMARY.json>) | `d0c911f5cb3c68f1471a0f74f704aed51a4d759ccb70667f263469d6e19f8b19` |
| [SCHEMA_TEST_MANIFEST.json](<C:/Users/LeTran/FPT University/2026_FALL/STEMotion/.audit/stem-phase-2026-11-05/phase-03/STEMotion_BE/docs/stem/phases/phase-03/SCHEMA_TEST_MANIFEST.json>) | `be687fe13c76ece629647dbd463805afa54a1fde8d6705bd6fbe3ff70b824c0b` |
| [CONTRACT_DELTA.md](<C:/Users/LeTran/FPT University/2026_FALL/STEMotion/.audit/stem-phase-2026-11-05/phase-03/STEMotion_BE/docs/stem/phases/phase-03/CONTRACT_DELTA.md>) | `bb3a7b85e95badea9f10dcac227ab15802330adb8e0a96584767764e0bbf0674` |
| [V91000__stem_p3_test_only.sql](<C:/Users/LeTran/FPT University/2026_FALL/STEMotion/.audit/stem-phase-2026-11-05/phase-03/STEMotion_BE/src/test/resources/db/stem-p3/V91000__stem_p3_test_only.sql>) | `013f9998e636eb4fb98d294888c498420a74780eeed138f1fdd34285df4b33cd` |
| [FIELD_MAPPING_P6.md](<C:/Users/LeTran/FPT University/2026_FALL/STEMotion/.audit/stem-phase-2026-11-05/phase-03/STEMotion_BE/docs/stem/phases/phase-06/FIELD_MAPPING_P6.md>) | `c2bc80cc347860439fc1f3804a0fa7371ddc18b218cb144c9f69d40738abd1fd` |
