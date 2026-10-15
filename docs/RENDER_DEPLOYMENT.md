# Deployment guide — thực tế ngày 2026-10-11

## Trạng thái và branch

| Thành phần | Target / branch | Snapshot đã xác minh trước task này |
|---|---|---|
| Spring Boot BE | Render, `deploy/render-staging` (không phải main) | `e38e09e200bda00adca9b69d687b498dd41fbafd`; deploy succeeded; health/readiness 200 UP |
| React/Vite FE | Vercel, `main` | `e0485c506d4647bc6c8fcda57d0a961018fef271`; deploy success; https://stemotion.vercel.app/ 200 |
| Remotion worker | `main`, GitHub Actions CI | `ddba8cf5f47c368e82c8716537f8a90530d41d5f`; CI success; **chưa có VPS/deploy target** |
| PostgreSQL | Supabase | V14 đã apply thủ công 15:40:15 GMT+7 ngày 2026-10-11 |
| FastAPI AI | Source `STEMotion_BE/services/ai` | Chưa có bằng chứng runtime riêng đã deploy; không coi source là deployment |

Các SHA trên là snapshot từ lượt push/deploy trước, không phải code mới của Track 2–4. Task hiện tại không push/deploy hoặc truy cập production. Cloud profile tắt Flyway, Hibernate validate. Render dashboard đã auto-deploy ở lượt trước, nhưng `render.yaml` vẫn ghi `autoDeployTrigger: off`: kiểm tra setting dashboard thực tế trước release, không suy ra từ YAML. Không tự đổi setting trong task này.

## Schema / V14 thủ công

V14 **đã lên production**; không chạy lại SQL ALTER ADD COLUMN. Hồ sơ: [V14 rollout](V14_ROLLOUT_2026-10-11.md), [SQL log](rollout/V14__production_sql_2026-10-11.sql), [rollback có guard](rollback/V14__director_template_metadata.down.sql).

Quy trình bắt buộc cho migration tiếp theo hoặc môi trường mới:
1. Backup production schema/data ứng dụng, verify integrity/decrypt/restore scope; xác nhận backup xong trước khi apply. Không commit backup/key. Backup V14 hiện nằm local `STEMotion_BE/.local-backups/v14-20261011T083835Z/` và dùng DPAPI Windows account hiện tại, không portable cho teammate.
2. Review đúng SQL migration đã test up/down/reapply ở DB local; prepared rollback có guard. Log mọi SQL production với timestamp/stage/result, không log returned private rows/credentials. Không bật Flyway cloud để thay thế thủ tục này.
3. Preflight version/checksum/columns, lock/timeout có giới hạn; apply SQL và record Flyway history trong một transaction. Fail thì rollback/dừng theo quy trình đã duyệt, không thử SQL khác trên production.
4. SELECT verify cột/constraint/history, rồi smoke boot BE **local**, datasource loopback/schema mới, Flyway off/Hibernate validate. V14 thêm subject/asset_pack/slots trên remotion_templates; checksum -557826410.
5. Báo backup/apply/verify; đợi lệnh push riêng. Không coi migration xong là deployment xong.

## Release theo thứ tự đã duyệt

Chỉ thực hiện khi có lệnh push:
1. Liệt kê các commit BE local so với `origin/deploy/render-staging`; run tests. Push **deploy/render-staging**. Chờ Render deploy đúng SHA, warm-up rồi GET `/actuator/health` và `/actuator/health/readiness` → 200 UP. Cold start có thể timeout lần đầu; retry có giới hạn. Startup fail: báo ngay, rollback deployment trước trong Render dashboard rồi investigate; không tự sửa schema/env production.
2. BE verify xong mới push FE `main`; chờ Vercel đúng SHA và GET https://stemotion.vercel.app/ → 200. Vite env được bake lúc build, cần rebuild nếu đổi URL.
3. FE verify xong mới push worker `main`; verify GitHub Actions CI xanh. **CI không deploy VPS**. Worker smoke CI không chứng minh render video xuyên toàn bộ hệ thống.
4. Báo SHA/deployment/health từng repo. Không apply thêm migration hoặc đổi secret trong release không được duyệt riêng.

## Environment handoff (tên biến, không chứa giá trị secret)

BE bắt buộc: DB_URL/DB_USERNAME/DB_PASSWORD, JWT_SECRET, CORS_ORIGINS, SPRING_PROFILES_ACTIVE=cloud. Dùng Supabase JDBC SSL và transaction pooler compatibility theo [env handoff](BE_ENV_HANDOFF.md). AI embedded: GEMINI_API_KEY/GEMINI_MODEL; AI remote: AI_SERVICE_MODE=remote, AI_SERVICE_URL, AI_SERVICE_KEY trên cả BE và FastAPI. Không xóa embedded Gemini trước khi rollout remote kiểm chứng.

Render integration: RENDER_SERVICE_URL (worker public/private reachable từ BE), RENDER_SERVICE_KEY chung worker/BE, RENDER_CALLBACK_URL=https://stemotion-be.onrender.com/api/v1/render/callback. Worker xác thực compute bằng X-Api-Key; callback dùng X-Render-Api-Key. CORS_ORIGINS cần origin FE thực tế. FE duy nhất cần VITE_API_BASE_URL=https://stemotion-be.onrender.com/api/v1; không đưa JWT/provider/DB/shared keys vào Vite.

Worker cần Node/Chromium/ffmpeg, đồng bộ source FE compositions, queue Redis nếu dùng BullMQ, storage public/persistent, USE_TTS/TTS_PROVIDER/TTS_API_KEY/TTS_VOICE và callback config. Per-job voice override dùng thật; output contract 1080p30. Google API key dùng x-goog-api-key header, không query. FPT explicit mode fail closed nếu quota/network lỗi.

## VPS worker: pending, không có deployment trong tài liệu này

Chưa có VPS. Trước deploy thật cần chốt host/SSH, CPU/RAM/storage, DNS/TLS, Redis/storage durability và network callback; không coi Render Free BE là video worker. M02–M06 (callback delivery/timeout, retry attempt identity, outbox/race, artifact QA/timing, storage URL durability) đang defer theo lệnh, chưa thể tuyên bố render production reliable. YouTube OAuth, Google login và Whisper captions cũng chưa hoàn thiện. Không đưa placeholder vào báo cáo như use case đã chạy thật.

## Local tests an toàn

Opt-in AI E2E hard-bound 127.0.0.1:15432, không Supabase. `STEMOTION_LOCAL_E2E=true`, `STEMOTION_DB_SMOKE=false`; `mvn -Dtest=*,!LocalStudioE2eTest test` chạy unit/targeted AI và không re-run Track 1 toàn bộ. Process smoke boot dùng JAVA_HOME/bin/java.exe trực tiếp để có thể cleanup chính xác trên Windows. Không boot BE local với .env production mà không override datasource/provider credentials.
