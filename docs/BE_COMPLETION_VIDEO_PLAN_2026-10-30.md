# BE completion + video quality implementation plan

Ngày: 30/10/2026. Baseline: BE `7f207bb`, FE `aa4d722`, worker `7e5aeea`.
Nguồn đối chiếu: master_update_plan.md M01–M17; TEAMMATE_TASKS_2026-10-27 T01–T12;
Nemo DESIGN_MEMBERSHIP_APPROVAL_2026-10-26 và VISUAL_QUALITY_PLAN_2026-10-29 P0–P5.

## 1. Mục tiêu và ranh giới

Người dùng đã yêu cầu lập plan rồi triển khai code BE/config tích hợp. Lượt này xây nền tích hợp
Vbee TTS và OpenAI/Claude qua backend/ai-service/worker, với tests offline và hướng dẫn setup key.
Không tự chạy paid requests, thay secret thật, push/deploy/migrate production.
Plan đầy đủ bao gồm backlog nghiệp vụ; policy chưa được xác nhận không tự coi là đã duyệt để code.

ChatGPT là sản phẩm của OpenAI, Claude Opus là model của Anthropic. Thiết kế có adapter riêng,
không dùng một key cho hai provider. Model ID là cấu hình, kiểm quyền truy cập bằng tài khoản khi setup.
Không hứa chất lượng hoặc model availability chỉ từ tên model; API billing/quota phải kiểm tra riêng.

Bubble Sort chỉ là idea video demo mentor, không phát sinh engine/trace/API riêng.
AI đề xuất, Writer/Producer chỉnh và Reviewer duyệt; không tự publish hoặc chạy mã AI tự do.
Mục tiêu video đúng, dễ đọc, học thuật, âm thanh tốt; nhiều màu/asset không tự đồng nghĩa đẹp hơn.

## 2. Đối chiếu và danh sách BE còn thiếu

| Ticket | Đối chiếu | Cần làm và nghiệm thu | Owner/dependency |
|---|---|---|---|
| A-6 | Nemo A4.5 / T02 | Log client không gửi baseRevision; feature flag bắt buộc; thiếu trả 400 khi bật; rollout FE trước | BE; không tự bật prod |
| A-7 | M10/M11, A-acct | Coverage xét account + membership active; xử lý account lock ảnh hưởng nhiều tổ nguyên tử | BE; chốt policy account |
| A-8 | T02/T07 | Sync kiểm scriptVersionId + revision dưới lock cho draft/composition; regression version mới cùng revision, Postgres thật | Đã code local 30/10; xem VIDEO_INTEGRATION_BATCH, chưa push/deploy |
| B-1 | Nemo B1/B2/B6 | Schema additive + rollback, migration/schema tests; chọn số migration theo main thực tế, không mặc định V23 nếu đã bị dùng | BE; D08/D09/D15/D16 |
| B-2 | M12/T06 | Proposal state machine, approve idempotent, không bypass bằng create cũ, ngoại lệ Writer-Leader có audit | BE; B-1, D12 |
| B-3 | M13/M14/T06 | Join/assign/revoke/rejoin, role snapshot bất biến; pending unique; kiểm account/workspace lúc approve | BE; B-1, D13/D15 |
| B-4 | T05 | P-role trên mọi write/AI/render; Admin vận hành tách duyệt nội dung; publish không suy từ review | BE; B-3, D-PUB |
| B-5 | T03/T12 | Backfill dry-run/idempotent, evidence-only, conflict findings không tự cấp quyền, màn xác nhận | BE + FE; B-3/D21 |
| B-6 | M17/T07 | Lease SCRIPT/COMPOSITION, session/generation, expire/force-release, ghi nguyên tử với check; giữ revision | BE + FE; D16/D17 |
| B-7 | M13/T08 | Contribution actor/role/version bất biến, event idempotent cùng transaction/outbox; không tính điểm từ click | BE; B-3/D18 |
| B-8 | M16/T09 | Leader inbox/board, blockers thật, count/filter/page ở SQL; không thay bằng Admin dashboard | BE + FE; B-2/B-3 |
| B-9 | T12 | Staging E2E nhiều role, rollout flag và migration artifacts, rollback | LeTran vận hành; FE hoàn tất |
| PERF-1 | Audit query | API aggregate workspace stats; server pagination/sort; không gọi N requests từ FE hoặc paginate in-memory | BE + FE; metrics thực |

Đã có Phần A workspace/revision/stale-sync/structured 409 và test trong main; không làm lại.
FE sửa Writer sync/đọc details đã có; nhánh Writer sync còn gộp mọi 409 cần sửa riêng.
Nemo docs còn chữ “chưa push” dù `7f207bb` đã trên origin/main: cập nhật trạng thái khi bàn giao.

## 3. Hai workstream độc lập

1. Nghiệp vụ: A-6/A-8 → chốt quyết định → B-1..B-6 → B-7/B-8 → staging/rollout.
2. Chất lượng: provider/config → Vbee + AI gợi ý → P0 baseline → P1 Academic → P2 scene spec → P3 asset → P4 vision → P5 motion/audio.

Không bắt video chờ membership, cũng không để video tự bỏ qua quyền hiện tại.
Mỗi quyết định chỉ chặn ticket liên quan; không cần chốt toàn bộ Dxx cùng lúc.

## 4. Kiến trúc đích

```text
FE Writer / Producer / Reviewer (không giữ provider keys)
  → Spring BE: auth, permission, revision, audit, config reference, job orchestration
      → ai-service: OpenAI / Anthropic / Gemini adapters → structured suggestions
      → render worker: Vbee / FPT / Google adapter → measured audio → Whisper alignment
          → FE Remotion entry + style/spec snapshot → MP4 → Reviewer final QC → publish
```

Vbee nằm ở worker vì preview/sync/render đang dùng chung TTS ở đó. Key chỉ đặt đúng process gọi provider.
OpenAI/Anthropic keys ở ai-service; BE Render chỉ cần shared key/URL khi AI_SERVICE_MODE=remote.
Embedded mode hiện là Gemini, không giả vờ OPENAI_API_KEY đặt ở Render BE sẽ đổi embedded provider.

## 5. Hướng nâng chất lượng theo mentor và plan Nemo

| Mentor / plan | Đầu ra cần có | Tình trạng / thứ tự |
|---|---|---|
| M01 âm thanh, Vbee | Adapter Vbee + giọng/nhịp cấu hình, đo audio thật, A/B và dùng đúng audio đã duyệt trong MP4 | Adapter/config trước; nghe/render cần key và budget |
| M07 script chỉn chu | Prompt sư phạm, mục tiêu học, kiến thức nền, ví dụ, lỗi hiểu nhầm; Writer quyết định | Sửa cả embedded/remote prompt |
| M04/M05 AI mạnh | OpenAI/Claude adapter, model routing, timeout, schema validation, usage thật, no silent fallback | Tích hợp trong ai-service; key sau |
| M03/P1 academic | StyleSpec version, neutral palette/font/motion/caption; không còn theme seed giả phong cách | FE renderer cần implementation riêng; chốt D03 |
| P2 scene spec | Gợi ý storyboard/scene plan có schema trước; renderer spec phiên bản sau | BE trả gợi ý, không tự lưu/sửa script đã duyệt |
| P3 minh họa | Đúng chủ đề, provenance; không ép “hình gần nhất”; SVG sanitize, không chữ/công thức trong ảnh sinh | Sau P2/budget; không tạo engine Bubble Sort |
| P4 vision QC | Findings có scene/time/frame, lý do, bằng chứng; false-positive/timeout rõ | Hỗ trợ, không thế Reviewer; SDK vision không tự thành pipeline |
| M08 Manim | Một prototype so cùng cảnh với Remotion; artifact/version/runtime isolation | Spike riêng, không bắt buộc tích hợp |
| P5 motion/audio | Nhịp hành động theo lời, clip thật không cắt tiếng, caption và audio nhất quán | Sau P0/P1 và pipeline audio artifacts |

Không dùng quy tắc “90% cảnh đúng” để cho phép xuất bản 10% sai: mọi lỗi kiến thức đã biết phải
sửa hoặc loại cảnh trước publish. Số 90% chỉ có thể đo first-pass generation, không là ngưỡng release.
Các 80% vision/1–5 rubric/4 bài là tiêu chí thử nghiệm Nemo đề xuất, không phải mentor đã chốt.

## 6. Provider/config implementation được giao lượt này

### C1. OpenAI + Anthropic

- Giữ OpenAI adapter hiện có và compatibility env OPENAI_MODEL_DRAFT/LIGHT.
- Thêm Anthropic Messages adapter, key/version/model cấu hình; JSON schema, kiểm refusal/truncated/invalid output.
- Route generate khác analysis; metadata trả model thực dùng và tokens khi có; không tạo số 0 giả khi không biết.
- Không ép temperature lên model không hỗ trợ; không tự đổi provider khi thiếu key/timeout.
- Có readiness/config status không gọi model và không lộ key.
- Test adapter bằng mock, không gọi paid API trong tests.

### C2. Vbee

- Dùng API v1 chính thức, tránh nhầm API legacy. Long narration dùng async batch + bounded polling.
- Preview/sync/render cùng dispatcher; voiceCode có dấu `_` hợp lệ, không dùng FPT voice ID cho Vbee.
- Vbee batch yêu cầu webhookUrl: worker cung cấp notification endpoint không đổi dữ liệu;
  kết quả chỉ tin khi GET request có auth. Không dùng callback công khai để đánh dấu job hoàn tất.
- Download audio ngay, URL Vbee có hạn; lưu local artifact để render, không nhét link tạm vào video lâu dài.
- HTTPS, host allowlist, no redirects, giới hạn bytes/time, đo duration bằng ffprobe;
  error/empty audio/missing key không fallback sang giọng free hoặc trả silent video thành công.
- POST tạo paid request không tự retry khi timeout không rõ provider đã nhận hay chưa.
- Dùng cờ cho phép paid TTS mặc định false; nhập key không tự phát sinh tác vụ.
- Test missing config, processing/completed/failed, deadline, unsafe URL, actual duration và dispatcher.

### C3. Gợi ý video qua BE

- Một API gợi ý storyboard dựa script hiện tại, kiểm WRITER/PRODUCER scope trước gọi AI.
- Chỉ trả đề xuất có cấu trúc; không lưu content, không thay revision, không tự render.
- Gắn requestId/model để audit và người dùng accept/ignore theo luồng có sẵn.
- Prompt là helper học thuật, không ép viết toàn bài hoặc chọn hình gần nhất.
- Chưa tuyên bố AI “dựng video đẹp” cho tới khi FE thực thi scene/style và MP4 được chấm.

### C4. Cấu hình và vận hành

- Env examples rỗng, có bảng process/key/model/voice/budget; không sửa `.env` thật.
- V1 quản trị qua environment (Admin/ops), không làm secret management UI động khi chưa chốt D07.
- Config version/artifact snapshot theo job là ticket kế tiếp; không tuyên bố “retry giữ cấu hình” trước khi có code.
- Smoke paid request chỉ sau user setup key, chọn model/voice và đồng ý ngân sách.

## 7. Roadmap sau batch tích hợp

| Batch | Nội dung | Acceptance / phụ thuộc |
|---|---|---|
| Q0 — batch hiện tại | Provider/config + prompt + storyboard suggestion + tests offline | Missing key báo đúng; mocks pass; không gọi paid thật |
| Q1 | 4 script duyệt, A/B Vbee, baseline MP4, D03 + chủ preset | Audio nghe được, chi phí/latency ghi thật; video cũ làm baseline |
| Q2 | FE StyleSpec legacy/academic + BE snapshot contract | 5 pilot templates/caption, preview/MP4 giống; historical unchanged |
| Q3 | Persist audio artifact + config version/hash, approval/invalidation | Audio preview đã duyệt dùng lại khi sync/render; sửa text/voice/speed thì invalidate |
| Q4 | Scene specification renderer, Producer edit, schema migration nếu cần | Element IDs bền, lỗi spec có vị trí, no arbitrary execution |
| Q5 | Asset đúng chủ đề + provenance + bounded vision QC | Known scientific errors không publish; findings có evidence |
| Q6 | Manim decision, motion/sound mix, final E2E | Người duyệt xem/nghe MP4, không chỉ screenshot |

Q1 cần key/model/voice/budget. Q2 cần FE owner; Q4 trở đi không được đánh dấu done chỉ vì gateway nhận JSON.
Paid cost guard bền vững (reserve/reconcile budget, idempotency, max attempts) cần thiết trước mở rộng tự động;
AI_MONTHLY_BUDGET_USD hiện chỉ monitor, không phải quota enforcement.

## 8. Test và rollout

- Python: Anthropic auth/shape/schema/usage/refusal/truncation, readiness, provider routing; regression existing OpenAI/script.
- Worker: Vbee mocked auth/body/polling/failure/host guard, ffprobe duration validation, provider no fallback;
  TypeScript build + existing TTS/HTTP tests.
- BE: storyboard auth/response/audit path, Java compile/tests; không dùng production DB để test.
- FE visual/audio E2E sau key: Writer → review → Producer preview/sync/render → final Reviewer → publication.
- Release tuple gồm BE/FE/worker/ai-service SHAs + schema + config version; production do LeTran vận hành.
- Rollback: trả AI_PROVIDER/TTS_PROVIDER trước, giữ artifact đang chạy; không xóa migration/dữ liệu tùy ý.
- Health/readiness không gọi provider trả phí; cấu hình đủ không đồng nghĩa key hợp lệ hoặc credits còn.

## 9. Quyết định cần chốt

D03 academic trắng đen hay neutral+accent; D04/D07 budget và ai được chọn model/voice;
D19 mức sửa Producer; D06 advisory QC; D08–D18/D-PUB/A-acct cho Phần B.
Đề xuất routing ban đầu: Claude Opus cho script/storyboard khó, OpenAI cho phản biện/QC khi model phù hợp,
hoặc chỉ dùng một provider để tiết kiệm; không bắt gọi cả hai cho mọi clip.
API adapter được cấu hình độc lập; chỉ chọn model ID tài khoản được cấp quyền.

## 10. Tài liệu provider đã tra ngày 30/10/2026

- [OpenAI structured outputs](https://developers.openai.com/api/docs/guides/structured-outputs)
- [OpenAI images/vision](https://developers.openai.com/api/docs/guides/images-vision)
- [Claude Messages](https://platform.claude.com/docs/en/api/messages)
- [Claude structured outputs](https://platform.claude.com/docs/en/build-with-claude/structured-outputs)
- [Claude models](https://platform.claude.com/docs/en/models/overview)
- [Vbee app/token](https://api-docs.vbee.vn/tao-ung-dung-app-id-va-token)
- [Vbee batch](https://api-docs.vbee.vn/vbee-api/text-to-speech/batch-api)
- [Vbee request polling](https://api-docs.vbee.vn/vbee-api/text-to-speech/get-request)
- [Vbee voice catalog](https://api-docs.vbee.vn/vbee-api/voices/get-list-voices)

Đây là tài liệu hiện hành đã đọc, không phải bằng chứng paid smoke test đã thành công trên tài khoản.
