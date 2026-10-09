# Góp ý DB ERD so với Conceptual ERD

> Ngày review: 06/10/2026
> Đối chiếu: **DB ERD** (bảng vật lý) ↔ **Conceptual ERD** (11 entity + 4 actor) ↔ code thật `V1__initial_schema.sql`

**Kết luận:** đủ 11/11 entity, nhưng còn **5 lỗi logic**, **4 chỗ sai cardinality**, nhiều cột/enum thiếu, và **DB ERD đang khác code thật** (mục 6).

---

## 1. Mapping conceptual → bảng: đủ

| Conceptual | Bảng | |
|---|---|---|
| Writer, Reviewer, Producer, Admin | `users` (cột `role`) | ✓ |
| ClipProject, ScriptVersion, Composition, Scene, RenderJob, Clip, Publication, Template, Asset_Media, AI_Request, FeedbackComment | 11 bảng cùng tên | ✓ |
| Scene **uses N..M** Asset_Media | bảng nối `scene_assets` | ✓ |

Điểm làm tốt: `feedback_comments` dùng 2 FK nullable kèm `target_type`. Giữ cách này.

---

## 2. Lỗi logic: phải sửa

- [x] **① Reviewer approve/reject không có chỗ lưu** *(nặng nhất)*
  Conceptual có *Reviewer reviews / approves / rejects* tới ScriptVersion và Clip, nhưng DB không lưu ai duyệt, khi nào, quyết định gì, lý do gì. `pipeline_status` chỉ cho biết trạng thái hiện tại.
  → Thêm bảng `reviews(id, reviewer_id, target_type, script_version_id, clip_id, decision, reason, created_at)`
  → hoặc `status_histories(project_id, from_status, to_status, changed_by, reason, changed_at)`

- [x] **② `publications` tự mâu thuẫn**
  `clip_id «UNIQUE»` trong khi `platform` có 2 giá trị (YOUTUBE, INTERNAL_LIBRARY), nên một clip không thể vừa lên YouTube vừa vào thư viện.
  → Đổi thành `UNIQUE(clip_id, platform)`; quan hệ đổi thành Clip 1 — 0..N Publication

- [x] **③ `ai_requests.target_id` là polymorphic nên các FK là giả**
  Sơ đồ vẽ *analyzes* → script_versions, *generates TTS* → scenes, *runs QA* → clips, nhưng bảng chỉ có một cột `target_id`, DB không kiểm tra được.
  → Đổi thành 3 FK nullable (`script_version_id`, `scene_id`, `clip_id`) kèm CHECK
  → Thêm `response_data jsonb` để lưu kết quả AI (use case *Accept / Ignore AI Suggestions* cần)
  → Thêm `decision` (ACCEPTED / IGNORED)
  → `request_type` bổ sung `ANALYSIS` (concept extraction, readability, terminology)
  → Thêm `model`, `latency_ms` (Admin theo dõi chi phí; NFR "AI < 15s")

- [x] **④ `feedback_comments` thiếu thông tin**
  → Thêm `resolved_by`, `resolved_at` (conceptual có *Writer / Producer reads / resolves*, hiện chỉ có `is_resolved`)
  → Thêm `scene_key` hoặc `anchor jsonb` để comment inline trỏ được tới scene cụ thể
  → Thêm CHECK: `SCRIPT_INLINE` ⇒ `script_version_id` NOT NULL; `CLIP_TIMESTAMP` ⇒ `clip_id` và `timestamp_val` NOT NULL

- [ ] **⑤ Legend của conceptual mâu thuẫn với DB**
  Legend ghi *"Actor (kept separate — not User + Role)"* nhưng DB gộp thành `users + role`.
  → Sửa legend: *"Tách riêng ở mức khái niệm; mức vật lý gộp thành bảng users với cột role"*

---

## 3. Cardinality sai

- [x] `script_versions → compositions`: đang `1 ‖—‖ 1`, đúng là **`1 ‖—o│ 0..1`** (chỉ bản đã approve mới có composition)
- [x] `render_jobs → clips`: đang `1 ‖—‖ 1`, đúng là **`1 ‖—o│ 0..1`** (job FAILED không tạo clip)
- [x] `clips → publications`: đang `1 ‖—‖ 1`, đúng là **`1 ‖—o< 0..N`** (xem ②)
- [ ] `owns` / `contains` / `triggers`: nhãn ghi **1..N** nhưng ký hiệu vẽ **o<** (0..N). Sửa nhãn thành 0..N cho khớp ký hiệu.

---

## 4. Thiếu cột / sai enum

- [x] **`users`**: thêm `password_hash`, `google_id`, `status` (ACTIVE/INACTIVE), `updated_at`
- [x] **`render_jobs`**: enum `SUCCESS` đổi thành `COMPLETED` cho khớp render service; thêm `FAILED`, `error_message`, `started_at`
- [x] **`clips`**: `qa_status` thêm `PENDING`; thêm `qa_report jsonb` (chi tiết 4 hạng mục QA)
- [ ] **`templates`**:
  - `name` mới liệt kê 5 loại, trong khi đề tài ghi 7 và biên bản review ghi 11. Chốt một con số.
  - Thêm `subject` (thầy Hùng nhắc cân bằng template giữa 5 môn)
  - Thêm `category` riêng, không nhét loại template vào `name`
  - Thêm `created_by` (conceptual có *Admin manages Template*)
- [x] **`scene_assets`**: thêm `slot_key` (asset được đặt vào ô nào của scene)

---

## 5. Trình bày sơ đồ

- [ ] Enum bị cắt chữ: `role` mất ADMIN, `pipeline_status` mất PUBLISHED, `render_jobs.status` mất phần đuôi. Nới rộng khung.
- [ ] Nhãn "contains 1..N" đè lên `submitter_id`
- [ ] Nhãn "targets (Inline)" và "writes/resolves" chồng nhau
- [ ] Conceptual đang lẫn **hành động** với **quan hệ dữ liệu**: *monitors queue*, *monitors cost/usage*, *swaps assets*, *edits properties* là hành động của use case, không lưu thành dữ liệu. Xoá khỏi conceptual, hoặc nối vào bảng thật.
- [ ] Tên quan hệ không thống nhất: DB ghi "builds", conceptual ghi "opens draft / edits"

---

## 6. ⚠️ DB ERD khác DB đang chạy trong code

So với `STEMotion_BE/src/main/resources/db/migration/V1__initial_schema.sql`:

| DB ERD (ảnh) | Code thật |
|---|---|
| `compositions`, `clips`, `ai_requests`, `asset_media`, `scene_assets` | **không có** 5 bảng này |
| `scenes` thuộc `compositions` | `scenes` thuộc `script_versions` |
| một cột `pipeline_status` | tách 2 cột `script_status` + `video_status` |
| `templates`, `publications` | `remotion_templates`, `publish_records` |
| — | có thêm `workspaces`, `workspace_members`, `status_histories` |

Nếu nộp ERD này mà demo chạy code hiện tại, hội đồng hỏi "bảng compositions đâu" là không trả lời được.

**Cần nhóm chọn một hướng:**
- [ ] **A.** Giữ ERD này làm thiết kế đích, rồi viết migration `V2` để code theo kịp *(khuyên chọn, vì ERD gần với use case hơn)*
- [ ] **B.** Vẽ lại ERD theo code thật (lưu ý `status_histories` trong code đã giải quyết được một phần lỗi ①)

---

# Vòng 2: review lại DB ERD (09/10/2026)

**Kết luận:** DB ERD đã ổn hơn rõ. Phần vật lý đã sửa xong 11/14 mục ở trên. Chỗ chưa khớp bây giờ nằm chủ yếu ở **Conceptual ERD, vì sơ đồ này chưa được cập nhật**.

## Đã sửa tốt
- `review_decisions` mới giải quyết lỗi ①. Bảng này dùng cùng cách 2 FK nullable như `feedback_comments`, thống nhất.
- `publications` có `UNIQUE(clip_id, platform)` và ghi ràng buộc ngay dưới bảng.
- `ai_requests` đủ 3 FK, `response_data`, `decision`, `ANALYSIS`, `model`, `latency_ms`.
- `feedback_comments` có `scene_key`, `resolved_by`, `resolved_at`.
- Cardinality 1 : 0..1 cho compositions và clips, 1 : 0..N cho publications.
- `users`, `render_jobs`, `clips`, `scene_assets`, `templates` đã đủ cột.

## Còn phải sửa

### A. Sửa Conceptual trước, DB ERD dẫn xuất lại sau

Thứ tự đúng là **Conceptual → DB ERD → code**. Mọi bảng trong DB phải truy ngược được về một entity hoặc một quan hệ trong conceptual. Vòng 1 sửa thẳng trên DB là làm ngược thứ tự, nên bây giờ cần đưa các thay đổi đó về conceptual.

Truy ngược 14 bảng hiện có:
- 11 bảng ← 11 entity ✓
- `users` ← 4 actor, theo quy tắc ánh xạ "gộp generalization thành 1 bảng + cột `role`" ✓
- `scene_assets` ← quan hệ *Scene uses N..M Asset_Media*, theo quy tắc "quan hệ N..M thành bảng nối" ✓
- `review_decisions` ← **không có nguồn trong conceptual** ✗

Việc cần làm trên conceptual:
- [x] Thêm entity **ReviewDecision**: Reviewer *makes* 0..N ReviewDecision; ReviewDecision *concerns* ScriptVersion **hoặc** Clip. Thay 2 đường *reviews / approves / rejects*. Lý do tách thành entity: quan hệ này có thuộc tính riêng (decision, reason, thời điểm).
- [x] Sửa cardinality ngay trên conceptual: *owns / contains / triggers* thành 0..N; *mapped to* và *produces* thành 0..1; *references* thành 0..N. DB chỉ chép lại.
- [x] Bỏ các đường hành động không sinh ra dữ liệu: *monitors queue*, *monitors cost/usage*, *swaps assets*, *edits properties*. Đây là use case, không phải quan hệ dữ liệu, và DB không có (cũng không cần) FK cho chúng.
- [x] Legend *"kept separate — not User + Role"*: giữ được, vì tách actor ở mức khái niệm là đúng. Chỉ cần ghi thêm quy tắc ánh xạ để giải thích vì sao DB gộp thành `users + role`.

### B. Lỗi mới trong DB ERD
- [ ] **Nhãn "gets QA" đặt sai trên 2 đường vào `review_decisions`.** QA là việc của `ai_requests`; quyết định duyệt là việc khác. Đổi thành "is reviewed in".
- [ ] `templates.category` đang ghi "(title, formula, diagram, chart, quiz, **etc**)". Enum trong DB không được có "etc". Liệt kê đủ 11 loại như code: TITLE_HERO, MATH_FORMULA, DIAGRAM_EXPLAINER, DATA_CHART, ALGORITHM_WALKTHROUGH, STEM_QUIZ, OUTRO, CHEMICAL_REACTION, COMPARISON_SPLIT, PROCESS_TIMELINE, GEOMETRY_SPACE.
- [ ] `pipeline_status` ghi "(DRAFT..PUBLISHED)". Ghi đủ 6 trạng thái.
- [ ] Enum vẫn bị cắt chữ: `users.role` mất ADMIN, `render_jobs.status` mất FAILED, `review_decisions.decision` mất REJECTED.
- [ ] Ghi chú CHECK dưới 3 bảng dùng FK nullable, giống cách đã làm với `UNIQUE` ở `publications`:
  - `feedback_comments`: SCRIPT_INLINE ⇒ script_version_id NOT NULL; CLIP_TIMESTAMP ⇒ clip_id, timestamp_val NOT NULL
  - `review_decisions`: SCRIPT ⇒ script_version_id; CLIP ⇒ clip_id
  - `ai_requests`: ANALYSIS/SEGMENTATION ⇒ script_version_id; TTS_CAPTION ⇒ scene_id; QA ⇒ clip_id
- [ ] `compositions` bị mất `updated_at` (Producer có sửa composition nên cần cột này); `scenes` thiếu `created_at`.

### C. Trình bày
- [ ] **Khó đọc hơn bản trước.** `users` nằm góc dưới trái, có khoảng 8 đường chạy song song sát nhau, không phân biệt được đường nào nối tới bảng nào. Nên đặt `users` ở giữa phía trên, các bảng con xếp xung quanh.
- [ ] Đường quá dài vắt ngang cả trang, như `script_versions` → `review_decisions`, `clips` → `publications`. Nên đặt `review_decisions` và `publications` cạnh `clips`.
- [ ] Ảnh xuất ra độ phân giải thấp, chữ trong bảng gần như không đọc được. Export PNG ở **scale 2** hoặc xuất SVG.
- [ ] Đa số đường không có nhãn, chỉ vài đường có. Gắn nhãn đủ hết, hoặc bỏ hết và chỉ dùng ký hiệu crow's foot.

### D. Vẫn còn từ vòng 1
- [ ] Mục 6: DB ERD vẫn khác code thật `V1__initial_schema.sql`. Nhóm chưa chọn hướng A hay B. Nếu theo đúng chuỗi Conceptual → DB ERD → code thì hướng **A** (viết migration V2 để code theo ERD) là hướng nhất quán.

---

# Bổ sung: phân loại theo 4 lĩnh vực S-T-E-M (09/10/2026)

**Vấn đề:** Conceptual chưa có cách phân loại theo Science / Technology / Engineering / Math. Hiện môn học chỉ là một thuộc tính dạng chữ (`clip_projects.subject`, `templates.subject`), không hiện trên conceptual và không gắn với lĩnh vực STEM nào.

**Phát hiện thêm:** 5 môn đang dùng (Math, Physics, Chemistry, Biology, CS) **không có môn nào thuộc E (Engineering)**:

| Lĩnh vực | Môn (GDPT 2018) |
|---|---|
| S – Science | Vật lý, Hóa học, Sinh học |
| T – Technology | Tin học |
| E – Engineering | **trống**. Ứng viên: môn *Công nghệ* |
| M – Mathematics | Toán |

## ~~Việc cần làm trên Conceptual~~ (bỏ)

> **Đã chốt 09/10/2026:** KHÔNG đưa phân loại STEM vào conceptual, chỉ thêm ở DB (phần "DB ERD dẫn xuất lại" bên dưới).

- [ ] Thêm entity **StemDiscipline**, gồm đúng 4 giá trị S, T, E, M
- [ ] Thêm entity **Subject** (môn học): Subject *belongs to* N..1 StemDiscipline
- [ ] ClipProject *is about* N..1 Subject (môn chính, dùng để lọc thư viện theo môn và lớp)
- [ ] ClipProject *integrates* N..M StemDiscipline (một clip STEM thường lồng ghép nhiều lĩnh vực; đây là điểm khác clip bài giảng thường)
- [ ] Template *suits* N..M Subject (để chứng minh template cân bằng giữa các môn, theo góp ý của thầy Hùng)

## DB ERD dẫn xuất lại (sửa sau)
- [ ] Bảng `stem_disciplines(code PK, name)` với 4 dòng S, T, E, M
- [ ] Bảng `subjects(id, name, stem_discipline_code FK)`
- [ ] `clip_projects.subject varchar` đổi thành `subject_id FK`
- [ ] Bảng nối `clip_project_disciplines(clip_project_id, discipline_code)`
- [ ] Bảng nối `template_subjects(template_id, subject_id)`; bỏ cột `templates.subject`
- [ ] Đổi tên `templates.category` thành `template_type` (giá trị TITLE_HERO, MATH_FORMULA, ...), để không nhầm với phân loại STEM

## Cần chốt với thầy
- [ ] Có thêm môn *Công nghệ* để phủ chữ E không? Đề tài gốc chỉ ghi 5 môn, nên thêm môn là **thay đổi phạm vi**.
- [ ] Nếu không thêm môn, thì chữ E được thể hiện qua đâu? Gợi ý: scene theo quy trình thiết kế kỹ thuật, hoặc dùng quan hệ *integrates* ở trên để gắn E cho các clip có phần thiết kế, chế tạo.

---

# Conceptual ERD vẽ lại theo ký hiệu chân chim (09/10/2026)

File: `STEMotion_Conceptual_ERD.drawio`, gồm 16 entity và 28 quan hệ, mỗi đầu quan hệ có ký hiệu min/max.
- Đã áp dụng toàn bộ mục A: thêm ReviewDecision; cardinality 0..1 và 0..N; bỏ *monitors queue*, *monitors cost/usage*, *edits properties* (Scene); *swaps assets* đổi thành *uploads*.
- Không có đường nào cắt qua ô. Có 3 chỗ hai đường giao nhau, vẽ bằng cung nhảy (line jump) để vẫn đọc được.
