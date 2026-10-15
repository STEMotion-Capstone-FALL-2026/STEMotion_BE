# Báo cáo tiến độ cho mentor — STEMotion, 2026-10-11

## Kết luận và chuẩn đối chiếu

Core workflow/Auth/DB/API đã có kiểm thử local; BE và FE đã deploy ở lượt trước. **Chưa có bằng chứng pipeline tạo video production hoàn chỉnh**, vì Remotion worker chưa có VPS. Lần này sửa AI audit/accept-ignore, contract 1080p30 và TTS key transport; tất cả vẫn local, chưa push. Phase B đã có demo local 60s narration tiếng Việt bằng Edge theo duyệt mới của anh, với transitions và nhạc CC0; FPT vẫn HTTP 429. Không coi Edge demo là production TTS.

Đối chiếu hồ sơ đăng ký FA26SE054 (5 trang, đọc lại toàn bộ), ba diagram Phase 0 baseline 2026-10-10 và bốn quyết định được duyệt. Báo cáo audit cũ chỉ là lịch sử; trạng thái ở đây dựa trên code/tests hiện tại. Proposal yêu cầu FastAPI/LangChain/GPT: service source trong `STEMotion_BE/services/ai`, Gemini làm provider ban đầu đã được duyệt, không xem đó là lỗi chưa được chốt.

## Ma trận yêu cầu — trạng thái sau fix

| Yêu cầu đề cương | Local / bằng chứng | Git & production / giới hạn |
|---|---|---|
| Pipeline studio: viết → review → production → clip review → publish | API-level E2E core PASS; enqueue stub/callback/Clip/publication thật trên DB local | BE/FE live bản trước; worker chưa deploy; chưa E2E video production |
| RBAC/JWT, admin cấp account | Register denyAll, provisioning ADMIN; local role gates PASS | Bản security đã push trước; không gọi production auth trong task này. Google OAuth còn thiếu |
| Scene editor/version/feedback | Stable IDs/comment preservation, chặn xóa scene có feedback; archive project/history | Baseline local E2E PASS; không re-run toàn bộ sau điều chỉnh |
| AI segmentation/grade/concepts/terms; human decisions | Gateway audit mọi logical call thành công/thất bại, real usage metadata nullable; owner/Admin decisions/idempotency, FE accept/ignore | M01 **local only**; remote generic FastAPI source có, runtime rollout riêng chưa verify |
| Template/asset-assisted production | Shared allowlist registry, adapter duration_sec→frames, Phase A/B physics/chemistry packs | Cinematic demo local; Composition độc lập/asset catalog DB/AI Director chưa hoàn chỉnh |
| MP4 1080p30/queue/live progress | M14 clamp/rescale/worker reject + voice sử dụng thật; 15 BE tests, worker contract tests PASS | Contract fix local only; Redis/VPS durable reliability chưa verify |
| Vietnamese TTS/captions | Strict FPT audio measured path/timing tests; CC0 music/ducking; demo Edge mới 60s đã render/verify theo duyệt | FPT vẫn 429; Whisper/regen-caption placeholder/defer |
| Automated pre-publish QA | Metadata/heuristic QA + clip/publication flow có test | Chưa đo overflow/contrast/caption sync trên artifact thật; không ghi là full QA |
| Internal library/export/LMS | Canonical publication + legacy adapter, usable escaped embed fixture, filtered library | Local E2E baseline PASS; không publish thử production |
| Publish YouTube | Endpoint từ chối rõ 503 khi chưa có credentials/upload thật | Chưa làm YouTube Data API; không có mock success được coi là publish |
| Admin users/templates/queue/cost | APIs/users/metadata/queue baseline PASS; ai_requests có usage thật qua stub metadata | Cost dashboard chưa nối dữ liệu; host metrics không phải VPS cluster telemetry |
| CI/CD/Docker/hosting | Build/tests PASS; BE deploy branch riêng, cloud Flyway off, V14 manual documented | BE Render/FE Vercel live bản trước; worker CI-only, AI runtime riêng chưa xác minh |
| NFR <15s AI, <5min render, 300 jobs/day, 200 users, cross-browser | Chưa có acceptance benchmark/load/browser matrix phù hợp | Không tuyên bố đạt SLA; local preview render không thay thế worker-pool benchmark |
| Deliverables 10+ beta clips + docs | OpenAPI BE 61 endpoints/AI snapshot, deployment guide và test report đã refresh | Chưa có bộ 10 beta clips production; user manual/remaining use cases cần bổ sung sau |

## Bốn quyết định Phase 0

1. Workspaces/workspace_members/status_histories và dual script/video states được giữ. Không refactor bỏ workspace.
2. ReviewDecision dùng explicit columns trên append-only status_histories; local E2E đã kiểm tra review/reason/history. Không tạo bảng audit JSON-note mới trong task này.
3. Publications hiện UNIQUE(project_id,platform), legacy publish_records adapter giữ API/LMS. Clip entity đã có và binding review/publication; chưa tự đổi unique sang clip_id, cần migration phase riêng.
4. Token count tách input/output/total theo metadata thực; thiếu dữ liệu là NULL. Monetary cost/currency chưa có pricing thì NULL, không hiển thị fabricated zero. M01 tests xác minh unknown values.

## Defer theo lệnh và blockers

- M02–M06: callback mất/timeout, retry attempt identity, dispatch race/outbox, artifact QA/timing, storage durability/expiring URL. Không làm vì đã được chỉ định đợi VPS; rủi ro vẫn tồn tại dù M14 contract pass.
- Composition độc lập, asset_media/scene_assets use case, cost dashboard, YouTube/Google OAuth, Whisper và Visual C/D/E ngoài phạm vi lần này.
- FPT free quota vẫn 429. User đã duyệt Edge cho demo local, nên blocker artifact demo được giải quyết: MP4 narrated 60s đã render và verify từng scene. Production TTS/quota chưa được giải quyết; không tự fallback.

## Tiến độ phase (ước lượng phạm vi, không phải acceptance production)

| Phase | Trạng thái | Ước lượng |
|---|---|---:|
| Phase 0 tài liệu/quyết định | Đã duyệt baseline và bổ sung workspace | 100% |
| P0 security/account/credential | Fix account/secret trước đã thực hiện; task này không rotate thêm | 100% phạm vi đã duyệt trước |
| Phase 1 schema | AI/publication/history/Clip có; V14 đã manual rollout; compositions/assets defer | ~90% phạm vi hiện được duyệt |
| Phase 2 AI service | FastAPI generic/LangChain provider + gateway audit/decisions; hooks/runtime rollout còn thiếu | ~60% |
| Phase 3 render/use cases | Core API/worker/contract có; VPS/reliability/real E2E và P2 còn thiếu | ~40% |
| Visual Phase A | Demo/registry/adapter đã duyệt | 100% |
| Visual Phase B | Assets/motion/accuracy/transitions/music/tests + demo Edge narrated 60s đã giao local; chờ anh duyệt demo | 100% phạm vi demo local, chưa acceptance production |
| Visual C/D/E | Theo lệnh chưa làm | 0% |
| Track 4 docs lần này | OpenAPI/deploy/test/mentor docs refresh hoàn tất local | 100% phạm vi docs lần này |

Bằng chứng chi tiết: [test report](TEST_REPORT_2026-10-11.md), [M01](TRACK2_M01_2026-10-11.md), [M14](TRACK2_M14_2026-10-11.md), [Track 3](../../STEMotion_FE/docs/TRACK3_2026-10-11.md). Không dùng test stub để khẳng định provider/runtime production đã hoạt động.
