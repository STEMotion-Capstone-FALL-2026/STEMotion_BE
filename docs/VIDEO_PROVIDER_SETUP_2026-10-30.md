# Setup Vbee + OpenAI / Claude cho STEMotion

Batch 30/10/2026; bàn giao trên nhánh feature/video-providers-a8-2026-10-30. Chưa deploy hoặc paid smoke test.
Đọc cùng `BE_COMPLETION_VIDEO_PLAN_2026-10-30.md`; việc thêm key chỉ kích hoạt adapter,
không có nghĩa FE đã có renderer mới, Style Spec, Manim hoặc vision QC hoàn chỉnh.

## 1. Đặt key đúng process

| Chạy ở đâu | File local / môi trường deploy | Cần cấu hình |
|---|---|---|
| Spring BE | `STEMotion_BE/.env` / Render Environment | AI_SERVICE_MODE, AI_SERVICE_URL, AI_SERVICE_SHARED_KEY, VIDEO_STORYBOARD_ENABLED |
| Python ai-service | `STEMotion_BE/services/ai/.env` / env container | AI_PROVIDER, task routing, OPENAI_API_KEY hoặc ANTHROPIC_API_KEY, model IDs |
| Node worker | `STEMotion_RENDER/.env` / env container | USE_TTS, TTS_PROVIDER, VBEE_APP_ID/ACCESS_TOKEN/VOICE_CODE, worker public URL |
| FE | Không đặt provider secret | Gọi API BE bằng session/JWT có sẵn |

Không đưa key vào VITE_*, source, chat hoặc tài liệu. Không cần gửi key cho AI.
Các file `.env.example` đã cập nhật; không copy đè `.env` đang có DB/JWT/shared keys.
Giữ nguyên callback/shared-key đang hoạt động và chỉ thêm biến được mô tả dưới đây.

## 2. Claude Opus + OpenAI (chọn một hoặc route theo task)

API key OpenAI và Anthropic riêng nhau. Kiểm tra API account/quota/model access;
không giả định thuê bao ứng dụng chat cung cấp đúng API quyền hoặc số dư.

Trong **ai-service**:

```dotenv
# Single-provider đơn giản nhất: mọi task dùng Claude.
AI_PROVIDER=anthropic
ANTHROPIC_API_KEY=
ANTHROPIC_MODEL_DRAFT=claude-opus-5-5
ANTHROPIC_MODEL_LIGHT=claude-opus-5-5
ANTHROPIC_API_VERSION=2023-06-01
ANTHROPIC_MAX_OUTPUT_TOKENS=8192

# Chỉ điền khi muốn dùng OpenAI. Model phải có quyền trên API account của bạn.
OPENAI_API_KEY=
OPENAI_MODEL_DRAFT=
OPENAI_MODEL_LIGHT=
OPENAI_TEMPERATURE=

# Optional: storyboard dùng Claude, grade/review dùng OpenAI.
# Để rỗng nếu chỉ dùng Claude; routing bật thì mọi provider được chọn phải đủ key.
AI_STORYBOARD_PROVIDER=
AI_REVIEW_PROVIDER=
```

Model Claude ở trên là ID trong tài liệu chính thức đã tra ngày 30/10; vẫn phải thử quyền tài khoản.
Không dùng tên “ChatGPT Opus” làm model ID. Không tự thay OPENAI_MODEL_* bằng tên không được API hỗ trợ.
Nếu muốn chỉ dùng OpenAI: AI_PROVIDER=openai, điền OPENAI_MODEL_DRAFT/LIGHT và key.
Không tự sửa các key Gemini cũ; chúng không được gọi khi provider/route không chọn Gemini.

Task routing:

- `generate`, `storyboard` → MODEL_DRAFT của provider được chọn.
- `grade`, `extract`, `terms`, `resegment` → MODEL_LIGHT (giữ semantics adapter hiện có).
- AI_STORYBOARD_PROVIDER chỉ áp dụng `storyboard` mới.
- AI_REVIEW_PROVIDER chỉ áp dụng `grade`; không tự chạy review sau mọi generation.
- `approaches` và generic analysis qua AiGateway được gửi task grade; schema/prompt vẫn là tác vụ gốc,
  không biến output thành điểm số. `grade` ở đây còn là nhóm routing analysis cũ, cần lưu ý khi định giá.

Ở **Spring BE**:

```dotenv
AI_SERVICE_MODE=remote
AI_SERVICE_URL=http://localhost:8000
AI_SERVICE_SHARED_KEY=
VIDEO_STORYBOARD_ENABLED=false
DRAFT_REQUIRE_BASE_REVISION=false
```

URL localhost chỉ dùng khi BE và ai-service cùng máy. Render dùng HTTPS tunnel/host thực của
ai-service; Docker BE dùng `http://ai-service:8000`. Shared key phải khớp ở cả hai process.
Compose đang override shared key từ BE `.env`, nên cấu hình cùng một giá trị riêng tư ở nơi đó.
Đừng chỉ đặt OPENAI/ANTHROPIC key trên Render BE rồi kỳ vọng container ai-service nhận được.

`/health` là liveness; `/ready` còn cần Whisper weights/offline preflight hiện có.
Readiness không gọi model trả phí, không chứng minh key/model/credits hợp lệ.

## 3. Vbee TTS

Tạo app API tại https://studio.vbee.vn/apps theo tài liệu chính thức.
Lấy App ID, access token và voice code của giọng được account cấp quyền. Voice code khác tên hiển thị
và khác FPT `banmai`. Ví dụ trong docs: `hn_female_ngochuyen_full_48k-fhg` (ứng viên, cần nghe A/B).
Ghi ngày hết hạn token trong nơi quản lý secrets/lịch vận hành và chuẩn bị rotate trước hạn;
không lưu token hoặc ngày hết hạn giả định vào source. Chọn thời hạn theo chính sách của team.

Trong **worker**:

```dotenv
USE_TTS=true
TTS_PROVIDER=vbee
VBEE_APP_ID=
VBEE_ACCESS_TOKEN=
VBEE_VOICE_CODE=hn_female_ngochuyen_full_48k-fhg
VBEE_SPEED=1
VBEE_PAID_REQUESTS_ENABLED=false
VBEE_WEBHOOK_URL=https://YOUR-WORKER-PUBLIC-HOST/tts/vbee/notify
VBEE_AUDIO_HOSTS=vbee.vn,api.vbee.vn,vbee.s3.ap-southeast-1.amazonaws.com
# Để trống: code dùng output/tts-cache; Docker cần giữ output volume hiện có.
VBEE_CACHE_DIR=
```

VBEE_SPEED dùng riêng thang Vbee **0.25–1.9**, không copy giá trị TTS_SPEED của FPT sang.

Thay YOUR-WORKER-PUBLIC-HOST bằng địa chỉ HTTPS worker thật (cùng worker đang nhận render),
không phải hostname BE Render. Endpoint notify chỉ trả 204; không có quyền đổi trạng thái job.
Worker lấy kết quả bằng polling có Bearer/App-Id nên không tin callback chưa được xác minh.

Nếu Vbee trả audio ở CDN khác, operator kiểm tra hostname HTTPS rồi thêm **đúng hostname** vào
VBEE_AUDIO_HOSTS. Không dùng wildcard/IP/private host để bỏ qua kiểm tra.
Danh sách mẫu trên chưa được xác nhận bằng tài khoản thật; xác nhận hostname là acceptance của smoke test.
Request dùng API v1 async; không dùng app_id/token body của API legacy.
Khi xem render bằng FE, để ttsVoice trống để dùng VBEE_VOICE_CODE mặc định hoặc gửi voice code Vbee
hợp lệ; không gửi FPT banmai vào Vbee. Chưa có UI danh sách giọng Vbee mới trong batch này.

Lỗi provider/timeout/empty audio không đổi ngầm sang giọng free. Worker tải audio về trước khi
link provider hết hạn, đo ffprobe, cache theo text/app/voice/speed/format. Đổi text/voice/speed → cache khác.
Đây là cache kỹ thuật, **chưa phải workflow audio approval/version artifact** của Q3.
Key bị rotate không đổi cache content; xóa cache theo chính sách vận hành nếu muốn thu hồi artifact.
POST tạo request chỉ gọi một lần; timeout có thể đã tính tiền nên không tự retry tạo lại.
Khi tải audio gặp HTTP 401/403/410, worker gọi lại GET có xác thực cho cùng requestId để lấy
link mới, tối đa một lần trong giới hạn polling. Không POST thêm để xử lý link hết hạn.
Polling chỉ nhận COMPLETED/PROCESSING/FAILED và audioLink; không dùng SUCCESS/FAILURE/audio_link của callback.

### Pricing trước khi bật provider trả phí

Đặt AI_PRICING_JSON ở **Spring BE**, ánh xạ đúng tên model thực tế provider trả về (có thể là
snapshot khác alias cấu hình). Mỗi entry phải có inputPerMillion, outputPerMillion là số
không âm và version là nhãn nguồn/ngày giá đã xác minh. Dùng giá từ hợp đồng/tài liệu billing
hiện tại của tài khoản; không copy giá model cũ hoặc nhập 0 để che thiếu cấu hình.
Thiếu model/usage thì BE giữ cost null, không phải miễn phí; dashboard ngân sách có thể đếm thiếu.
Xác nhận model/usage/pricingVersion/cost sau smoke test đầu trước khi mở rộng sử dụng.
AI_MONTHLY_BUDGET_USD hiện không chặn chi tiêu; chưa có quota enforcement.

## 4. API storyboard mới (BE)

Chỉ bật VIDEO_STORYBOARD_ENABLED=true sau khi provider/model/budget sẵn sàng.
Đây là thao tác có thể tốn phí khi người dùng gọi; không gọi tự động trong health hay bootstrap.

```http
POST /api/v1/scripts/{projectId}/ai/storyboard
Authorization: Bearer <session JWT>
Content-Type: application/json

{"baseVersion":1,"baseRevision":3}
```

Lấy baseVersion từ currentVersionNo, baseRevision từ draftRevision của GET script hiện tại.
API kiểm quyền WRITER/PRODUCER của project theo cơ chế authorization hiện có; không làm thay P-role
của Phần B chưa triển khai. Outsider/wrong role không gọi AI được.

Response:

```json
{
  "sourceVersion": 1,
  "sourceDraftRevision": 3,
  "sourceChanged": false,
  "suggestion": {
    "action": null,
    "model": "actual-provider-model",
    "elapsedMs": 1234,
    "requestId": "audit-id",
    "result": {"scenes": [{
      "sceneId": "existing-scene-id", "learningObjective": "…", "layout": "…",
      "visualDescription": "…", "motionCue": "…", "pronunciationNotes": "…", "reviewWarnings": []
    }]}
  }
}
```

Source đổi khi AI chạy → sourceChanged=true; không áp đề xuất lên dữ liệu mới khi chưa đối chiếu.
API không lưu script, không tăng revision, không start render. Model result là suggestion, không phải
scene-spec đã được renderer thực thi. Chưa có nút FE gọi API này; integration UI là Q2.
Accept/ignore audit có thể dùng API decision hiện có; việc apply content phải qua save/review hợp lệ.

## 5. Kích hoạt có kiểm soát sau khi bạn thêm key

1. Điền key/model/voice/URLs ở đúng process. Không gọi paid API trong bước validate env.
2. Build/start ai-service và worker bằng quy trình hiện có. Không cần migration mới cho batch này.
3. Kiểm tra ai-service health/ready; worker health; notification endpoint trả 204.
4. Chốt budget smoke test và người nghe/duyệt. Đổi VBEE_PAID_REQUESTS_ENABLED=true rồi nghe một câu ngắn.
5. Đo latency/chi phí/phát âm; thử cùng text hai lần để kiểm cache, đổi speed để kiểm invalidation.
6. Bật VIDEO_STORYBOARD_ENABLED=true, gọi một storyboard của script test bằng JWT hợp lệ.
7. Dựng một clip test, xem/nghe MP4 và so preview, caption/duration; không nghiệm thu bằng JSON alone.
8. Ghi kết quả thật + SHA/config/voice/model. Sau đó mới chốt P0/P1 và quyết định mở rộng.

Budget env trong repo trước đây chỉ theo dõi, không phải quota enforcement. Batch này chưa làm
giới hạn chi phí theo tháng có reserve/reconcile bền vững; đừng bật vòng lặp auto-generate/auto-QC.
Provider có thể tính phí theo token/ký tự; không hứa giá hoặc giọng tốt hơn trước khi nghe thử.

## 6. Nguồn

[Vbee app/token](https://api-docs.vbee.vn/tao-ung-dung-app-id-va-token),
[batch](https://api-docs.vbee.vn/vbee-api/text-to-speech/batch-api),
[polling](https://api-docs.vbee.vn/vbee-api/text-to-speech/get-request),
[voices](https://api-docs.vbee.vn/vbee-api/voices/get-list-voices).
[Claude Messages](https://platform.claude.com/docs/en/api/messages),
[structured outputs](https://platform.claude.com/docs/en/build-with-claude/structured-outputs),
[model IDs](https://platform.claude.com/docs/en/models/overview).
[OpenAI structured outputs](https://developers.openai.com/api/docs/guides/structured-outputs).
