# Merge review — origin/main → feat/ai-microservice

Ngày: 2026-10-16. Trạng thái: chờ Nemo review, chưa đưa vào main/production.

## Phạm vi và nguồn

- Target trước merge: `dd90998ff6c9f3f026a1b9e1112fbb26179ccf4e` (feat/ai-microservice).
- Source đã fetch: `e7fffadf46dd55fee71fac98a38e8fc044498e39` (origin/main).
- Merge hai parent, một chiều; commit message: `merge: integrate origin/main into feat/ai-microservice for review`.
- Không checkout main, không merge ngược, không deploy/apply migration production. FE/RENDER không sửa.
- `.gitignore` local có sẵn được stash riêng rồi phục hồi; không nằm trong commit này.
- Căn cứ: `docs/REQUIREMENTS.md` mục 3–4 (Writer kiểm soát AI, bộ template chuẩn, FastAPI/LangChain), `docs/AI_NARRATIVE_ARC_CONTRACT.md`, `docs/AI_PHASE3_2026-10-15.md`, và code source main. Đây là đối chiếu tài liệu trong repo, không tuyên bố đã duyệt lại PDF đề cương.
- Tài liệu narrative cũ nói chưa dùng LEGO; chỉ dẫn hiện tại của Lê Trần xác nhận team đã đồng ý LEGO nên giữ implementation composed của Nemo.

## 10 file conflict và cách resolve

| File | Quyết định |
| --- | --- |
| `.env.example` | Giữ cả cấu hình AI và Google Sign-In; chỉ template, không đọc env thật. |
| `docker-compose.yml` | Giữ ai-service/shared key/pricing và cấu hình Google, Flyway/JPA của main. |
| `src/main/java/com/stemotion/core/config/SecurityConfig.java` | Giữ Google login public và hai route alignment với guard riêng; drafting dùng kiểm tra active workspace Writer của Nemo thay vì chặn theo global role cũ. |
| `src/main/java/com/stemotion/domain/admin/service/AdminMetricsService.java` | Giữ aggregate M01 thật của Nemo; budget đọc env, cost null khi không có dữ liệu giá, không đoán TTS provider. |
| `src/main/java/com/stemotion/domain/ai/dto/AiDtos.java` | Giữ `approach` (800 ký tự), `narrativeBrief` (500), ApproachRequest và action metaphor. Bỏ constructor 5 tham số mơ hồ; caller Java truyền rõ 6 tham số, constructor 4 tham số vẫn còn. JSON giữ tên field cũ của cả hai nhánh. |
| `src/main/java/com/stemotion/domain/ai/service/AiSchemas.java` | Giữ narrativeArc + approachName/flow; giữ schema COMPOSED, quiz, diagram, algorithm của Nemo. |
| `src/main/java/com/stemotion/domain/ai/service/AiScriptService.java` | Kết hợp hai input dẫn dắt, dynamic pacing, guardrail; giữ suggestApproaches và metaphor; giữ remote service cho generate và 4 action cũ. |
| `src/main/java/com/stemotion/domain/workspace/service/WorkspaceService.java` | Lấy nguyên file origin/main: admin-provisioned membership, role/leader trong workspace response; không khôi phục invitation flow đã bỏ. |
| `src/test/database/package.json` | Chạy cả membership và pricing migration tests. |
| `src/test/java/com/stemotion/domain/ai/service/AiScriptServiceTest.java` | Giữ test narrative/dynamic pacing, cập nhật template expectation theo main, giữ test icon fallback của Nemo cho embedded mode. |

## Các lỗi tích hợp không được Git đánh dấu conflict

- FastAPI `extra=forbid` sẽ từ chối `approach` (kể cả null) từ DTO merged. Đã thêm field và đưa cả hai input vào prompt; test qua HTTP mock xác nhận.
- Python prompt/schema cũ không có COMPOSED và cấm quiz/diagram. Đã port prompt merged và xuất `generatedScript.json` từ `AiSchemas` đã compile; validator nhận composed, kiểm tra frame/2–6 blocks và icon allowlist; không ép cảnh đầu TITLE_HERO khi main cho phép mở bằng vấn đề. Các guardrail scientific-content hiện có còn giữ.
- Action `metaphor` của main không thuộc `/script/action` v2. Giữ prompt/schema Nemo và chuyển qua generic gateway `/internal/v1/generate`; không làm mất action. Approaches cũng dùng generic gateway hiện có. Hai tác vụ sáng tạo này dùng draft model theo default generic task=generate; 4 action cũ vẫn theo routing light của service.
- Interceptor membership mới có thể chặn Producer alignment. Giữ membership guard cho drafting/approaches; alignment người dùng giữ JWT role guard, worker alignment giữ shared-key guard riêng.
- Admin constructor và các caller GenerateScriptRequest được đồng bộ với contract merged; thêm test xác nhận hai field được gửi qua REST client.

## Migration

- Không có conflict SQL. V15 composed → V16 member enum → V17 membership → V18 pricing, không trùng version.
- V15/V16/V17 byte-equivalent với origin/main; V18 không đổi so với target trước merge.
- PGlite local: toàn bộ suite migration pass, thêm chạy liên tục V1→V18 trên DB trống pass.
- Đây không phải xác nhận Flyway validate trên production và không phải rehearsal với dữ liệu production. V17 có cleanup invitation/column; vẫn cần backup và review rollout trước khi apply.

## Kiểm thử

- Maven `-q compile -DskipTests`: PASS.
- Maven `-q test`: 268 tổng, **251 passed, 17 skipped**, 0 failure/error. Các test opt-in môi trường/DB không được bật; không chạy cloud E2E.
- Suite Java chạy trong snapshot chỉ chứa source/pom và fixture pricing công khai, không có `.env`; snapshot source đối chiếu với working tree trước commit.
- Python `pytest -q`: **47 passed**, mock/local; không gọi provider trả phí, không tải model.
- `npm test` tại `src/test/database`: PASS cả 6 script migration, gồm membership rollback/reapply và V18 pricing.
- Chạy thêm V1→V18 cùng một PGlite instance: PASS; version unique.
- `git diff --check`: PASS.
- Lần Java đầu lỗi do constructor 5 tham số hiểu khác nhau và snapshot thiếu fixture pricing; đã sửa và chạy lại full suite thành công. Lượt sandbox ban đầu bị treo đã dừng, suite chính chạy với quyền native/loopback cần thiết.

## Nemo cần chốt trước merge cuối

1. UI dùng `approach` hay `narrativeBrief`, hay giữ cả hai? Hiện đều được gửi vào prompt, không tự đặt ưu tiên khi ý tưởng mâu thuẫn; chưa xóa endpoint/field nào. Tài liệu repo không đủ rõ để chọn thay team.
2. Metadata response nên thống nhất `narrativeArc` hay `approachName`/`flow`? Hiện giữ cả ba, optional như các schema nguồn.
3. Embedded Nemo thay icon lạ bằng `sparkle`, còn service remote giữ fail-closed. Đã giữ hành vi hai phía và test tương ứng; cần chốt chính sách chung.
4. Generic approaches/metaphor dùng draft model. Có muốn port thành endpoint Python riêng và đổi model routing không? Không mở rộng thành feature mới trong merge review.
5. Admin overview kế thừa nhãn Gemini từ main; trong remote mode nhãn này chưa phản ánh model thực tế của từng request. `/admin/ai-usage` có aggregate model/provenance riêng. Tổng cost hiện có thể chỉ bao phủ request được định giá; `null` được giữ nếu toàn bộ chưa có giá. Cần chốt dashboard và coverage hiển thị.
6. Rollout V17 trên DB đang có invitation cần backup/đánh giá dữ liệu; local tests không thay thế approval production.

## Giới hạn review

Đã review conflict, các đường gọi AI/auth bị ảnh hưởng, membership/composed và migration; chưa có live OpenAI, render E2E, deploy smoke hoặc xác nhận dữ liệu production. `checklist.md` và các runbook lịch sử được nhập nguyên từ branch nguồn, chưa viết lại toàn bộ trạng thái sau merge. Nemo review notes và quyết định merge cuối vào main.
