# C.1 — Xác minh P5

10/11/2026, Asia/Saigon. Đối chiếu evidence sau Gate 1. Quyết định đóng thuộc người dùng.

## Expected / actual

| Hạng mục | Kết quả đối chiếu |
|---|---|
| BE | 268 pass, chọn 19 suites. Full 439 NOT_RUN. |
| FE | 227 pass, gồm 216 cũ và 11 mới. |
| Live P4/G06 | 2 pass riêng. |
| GAP-01 | FeedbackService đã có minimal hook từ P3, P5 mở rộng guard/lineage. |
| GAP-05 | 13 SceneTypes và 12 BlockKinds, có extraBlocks theo contract. |
| Migration | V92000 nằm trong test resources, 2 bảng sidecar, TEST_ONLY. |
| F09 | Có evidence DTO và semantic mutation ở các phase sau. MP4/per-leaf rendering vẫn UNKNOWN. |
| F10 | Có evidence 12 kinds, extraBlocks và ref negatives. Rendered visibility/pixel/audio vẫn UNKNOWN. |
| Backup | P5 BE `4fd7089` và FE `4dfa65e` khớp parent của hai backup P6 hiện trên GitHub và handoff backup P5. |

**Disposition đề nghị:** Ghi P5 technical scope ở mức evidence cô lập, giữ F09/F10 PARTIAL. Theo C-Q05 B, chuyển phần kiểm chứng media còn thiếu sang scope riêng, đề nghị R3 rehearsal sau khi có scope và approval. Giữ bảo vệ lineage và mismatch hard 409. Release vẫn NOT_READY.

## Evidence và SHA-256

| File | SHA-256 |
|---|---|
| [P5_HANDOFF.md](<C:/Users/LeTran/FPT University/2026_FALL/STEMotion/.audit/stem-phase-2026-11-05/phase-03/STEMotion_BE/docs/stem/phases/phase-05/P5_HANDOFF.md>) | `0c0fa20a7066002701e221870ffcab641f81e0deb6f286a6cc9a17553a091a8e` |
| [TEST_SUMMARY_P5.json](<C:/Users/LeTran/FPT University/2026_FALL/STEMotion/.audit/stem-phase-2026-11-05/phase-03/STEMotion_BE/docs/stem/phases/phase-05/TEST_SUMMARY_P5.json>) | `8b5ac142f9c63175c284fc9ae5e8ed4c1c95af79a1c9a858534d19a64b3c61dd` |
| [CONTRACT_ACTUAL_P5.md](<C:/Users/LeTran/FPT University/2026_FALL/STEMotion/.audit/stem-phase-2026-11-05/phase-03/STEMotion_BE/docs/stem/phases/phase-05/CONTRACT_ACTUAL_P5.md>) | `87e0c1395b49afdb42368004a498d031d6e031b3d611c0448924c6a648554c64` |
| [V92000__stem_p5_artifact_snapshots_test_only.sql](<C:/Users/LeTran/FPT University/2026_FALL/STEMotion/.audit/stem-phase-2026-11-05/phase-03/STEMotion_BE/src/test/resources/db/stem-p5/V92000__stem_p5_artifact_snapshots_test_only.sql>) | `c3146f168a51e8581597e7a2d14cb799b10df0af2ae43977e05c420a3178642f` |
| [CASE_COVERAGE_P7.md](<C:/Users/LeTran/FPT University/2026_FALL/STEMotion/.audit/stem-phase-2026-11-05/phase-07/CASE_COVERAGE_P7.md>) | `12595a3d587faefd81996615f80e646033a117c8cd6c454d1eb4169810321557` |
| [BACKUP_HANDOFF.md](<C:/Users/LeTran/FPT University/2026_FALL/STEMotion/.audit/stem-branch-backup-2026-11-06/BACKUP_HANDOFF.md>) | `94f6b2b90ced1d3db6a9cdc5dd7c93c03bf60875bac46b03d2c46760785a2b1b` |
