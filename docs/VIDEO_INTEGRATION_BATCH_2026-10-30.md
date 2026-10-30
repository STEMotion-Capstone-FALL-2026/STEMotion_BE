# Batch BE/config Vbee + AI — 30/10/2026

## Đã triển khai local

- ai-service thêm Anthropic Messages adapter với output schema, kiểm refusal/truncation và usage.
- Giữ OpenAI/Gemini; thêm task routing storyboard/review bằng env, không fallback provider ngầm.
- OpenAI temperature trở thành optional để tránh gửi tham số không phù hợp với reasoning model.
- Readiness xét key/model của các provider được chọn; không gọi paid API khi health/import.
- BE thêm `POST /api/v1/scripts/{projectId}/ai/storyboard`, feature flag mặc định off,
  quyền project WRITER/PRODUCER, version/revision trước gọi, ID/order/type kiểm lại,
  sourceChanged sau gọi, audit requestId/model/usage. Không lưu script hoặc render tự động.
- Prompt embedded/remote hướng học thuật, không ép chọn hình gần nhất; Writer vẫn điều khiển.
- A-6: log save không baseRevision và cờ DRAFT_REQUIRE_BASE_REVISION mặc định false.
- Worker thêm Vbee API v1 batch, notification ack không đổi trạng thái, authenticated polling,
  URL audio HTTPS/host allowlist/no redirects, giới hạn bytes/thời gian, ffprobe duration.
- Cache Vbee theo text/app/voice/speed; cache preview/sync theo provider/config thay vì text-only.
- Dispatcher preview/sync/render chọn Vbee khi cấu hình, chấp nhận underscore trong voiceCode;
  unknown provider báo lỗi, không tự đổi sang free TTS.
- Sync worker không còn nuốt lỗi TTS rồi bịa duration từ số từ và trả success.
- Env examples, plan tổng BE, hướng dẫn setup process/key/model/voice và rollout cập nhật.

## Phạm vi chưa hoàn tất

Nhập key chưa tự làm video đẹp hơn: chưa triển khai FE Style Spec/Academic renderer mới,
UI storyboard/voice catalog, scene-spec renderer, SVG generation, vision QC pipeline hoặc Manim.
Đây là Q1–Q6 trong plan, có owner/dependency/acceptance rõ.

Chưa có persisted audio approval artifact/config snapshot theo job; cache kỹ thuật không thay
workflow duyệt audio. Retry job sau đổi default provider có thể dùng config mới: cần Q3 để khóa snapshot.
Chưa có quota enforcement theo tháng/reserve-reconcile, chưa mua hoặc gọi API trả phí.
BE compute timeout hiện 180 giây; Vbee batch nhiều cảnh chậm có thể vượt timeout toàn request.
Khi đó BE không ghi kết quả sync, cần đọc trạng thái/cache trước retry; job async compute là bước Q3,
không giải quyết bằng kéo dài transaction DB. Render job vốn chạy async, khác request sync của studio.

Phần B membership/approval/lease/contribution/Leader dashboard chưa được code trong batch này;
policy còn mở giữ ở trạng thái cần team chốt. Không tự chạy migration production hoặc đổi quyền hiện tại.

## Kiểm chứng

- Worker: full `npm test` **34 tests pass**, gồm HTTP auth/notification, failure không tạo duration giả,
  FPT/Google, Vbee mocks/cache/no fallback, captions, job dedup; TypeScript build pass.
- ai-service: full `pytest` **69 tests pass**, gồm alignment và real audio decode bên cạnh provider/contract/deployment;
  không tải Whisper weights hoặc gọi model thật. Venv test riêng nằm trong `.audit/video-quality-venv`.
- BE sau A-8: full `mvn test` exit 0: **375 tests discovered, 358 pass, 17 skipped**, không failure/error.
  `PostgresConcurrencyTest` 7 tests chạy Postgres embedded thật và pass.
  17 bài smoke Supabase/local E2E được skip theo cờ môi trường hiện có; chưa xác minh production E2E.
- Một số lần chạy trong sandbox không khởi tạo worker/test runner được; các kết quả pass dùng
  lần chạy local có quyền cần thiết. Worker HTTP test tăng cửa sổ chờ startup từ 8 lên 45 giây để
  chạy ổn định trên Windows; không thay startup production.

Chưa paid smoke test với Vbee/Claude/OpenAI, chưa render MP4 bằng provider mới, chưa E2E production.
Các test mock chứng minh luồng/error/contract, không chứng minh chất lượng giọng hoặc hình ảnh thật.

## Bàn giao

Plan: `docs/BE_COMPLETION_VIDEO_PLAN_2026-10-30.md`.
Setup: `docs/VIDEO_PROVIDER_SETUP_2026-10-30.md`.
Provider secrets ở ai-service/worker, không phải FE. Không sửa `.env` thật trong batch này.
Bàn giao trên nhánh feature/video-providers-a8-2026-10-30 (BE), feature/vbee-tts-2026-10-30 (worker).
Không merge main/deploy; thay đổi local có sẵn của FE và BE `.gitignore` không nằm trong batch commit.

## Đối chiếu feedback Nemo — 30/10/2026

- Đã sửa thiếu sót link audio: HTTP 401/403/410 tải audio dẫn tới GET cùng requestId lấy link mới
  tối đa một lần, không POST thêm; có test refresh thành công và hết giới hạn.
- Mock polling đúng COMPLETED/PROCESSING/FAILED + audioLink; test từ chối SUCCESS/audio_link của callback.
- VBEE_SPEED riêng 0.25–1.9, không dùng TTS_SPEED của FPT. Setup đã ghi token expiry/rotate,
  hostname allowlist chỉ là ứng viên, phải xác nhận trong smoke test tài khoản thật.
- Storyboard audit dùng RequestType.ANALYSIS sẵn có; task="storyboard" chỉ route provider.
  Không thêm enum request_type hay migration DB trong batch này.
- Setup đã ghi AI_PRICING_JSON ở Spring BE phải map tên model provider thực trả về và giá đã xác minh;
  cost null không phải miễn phí. Chưa có giá mới của tài khoản nên không tự điền giá giả.
- Đã kiểm ignore .env, ai-service .env, .audit và output/tts-cache. Không có audio/cache/venv mới
  trong status của hai repo. Audio acceptance fixture vốn đã tracked trong BE được giữ nguyên.
- A-8 đã được người dùng giao xử lý tại đây: NarrationToolsService chụp ScriptVersion ID trước
  worker, truyền vào transaction ghi draft/composition; khóa dòng current version và so identity
  cùng revision trước mutation. Nếu current biến mất khi chờ lock, 409 thay vì fallback unlocked.
  Kiểm lại lifecycle trên version đã khóa: draft phải DRAFT/CHANGE_REQUESTED, composition phải APPROVED.
  Version mismatch trả STALE_REVISION kèm baseVersionId/currentVersionId. Không đổi request FE,
  không thêm migration, không giữ lock qua TTS. Contract/design đã cập nhật trạng thái push cũ.
  Regression gồm version mới cùng revision cho draft/composition, identity chụp trước worker,
  và Postgres thật kiểm dữ liệu giữ nguyên khi từ chối. Kết quả full suite sau A-8 ghi ở mục Kiểm chứng.
- Các key từng được báo lộ cần operator rotate riêng trước paid smoke; batch không xác nhận đã rotate,
  không đọc/ghi key thật, không tự deploy. Giữ paid flag false tới khi chốt voice/model/budget.
- Bounded parallel Vbee theo scene chưa triển khai: cần giới hạn toàn worker và phối hợp quota;
  vấn đề sync 180 giây vẫn là giới hạn Q3, không coi cache hoặc link refresh đã giải quyết được.

## Gói bàn giao GitHub

Người dùng đã yêu cầu push batch hoàn tất. Hai nhánh độc lập để review/tích hợp, không merge main.
A-8 đã code/test tại đây; Nemo không cần triển khai lại. Nhãn chưa push/local trong roadmap
là trạng thái tại lúc lập plan; trạng thái publication xem nhánh và commit Git hiện tại.

## Preflight trước merge main — 30/10/2026

Người dùng tiếp tục yêu cầu merge BE/worker vào main để CI/CD BE tự chạy; worker được teammate
cập nhật trên máy Tailscale sau. Các ghi chú "chưa merge/deploy" ở trên là trạng thái batch trước yêu cầu này.

- origin/main BE vẫn 7f207bb, worker vẫn 7e5aeea khi fetch trước merge; không có conflict remote.
- Batch không thay đổi migration, entity mapping, JDBC dependency, Dockerfile hoặc profile cloud.
- Profile cloud tắt Flyway, Hibernate ddl-auto=validate; không chạy migration production trong preflight.
- Chạy SupabaseSchemaValidationTest opt-in với DB Singapore từ .env local, cloud profile,
  Flyway=false, ddl-auto=validate, Hikari read-only=true và pool tối đa 1: **PASS**.
- URL được kiểm: Singapore transaction pooler 6543, sslmode=require, prepareThreshold=0.
  Đây là kiểm tra credentials/schema trong .env local, không phải đọc lại Environment hiện tại của Render.
- Không phát hiện lỗi kết nối/schema/startup cần sửa trước merge. Không đổi DB_URL, password,
  config infrastructure hay schema để phòng một lỗi giả định. Nếu Render dùng env khác,
  log deploy vẫn là nguồn xác nhận cuối; giữ SPRING_PROFILES_ACTIVE=cloud hiện có.
- Chưa chạy paid provider smoke hoặc triển khai worker tại nhà. Paid flags giữ nguyên mặc định off.
