# P5 — Callback/manifest/artifact digest: phương án trình gate

05/11/2026. **PENDING GATE, chưa DTO/code/test/worker patch.**

## 1. Baseline facts

RenderCallbackRequest hiện có jobId/status/progress/stage/videoUrl/errorMessage/captionSync, không hash/attempt/artifact digest. Callback xác thực X-Render-Api-Key qua integration config, tìm internal/external job ID, terminal duplicate no-op, Clip unique per render job và immutable URL. RenderJob giữ frozen inputProps/composition revision. Chưa có MP4 byte-proof. Không đọc `.env`/key thật, không liên hệ worker/provider hoặc render thật trong Chặng1.

## 2. Phương án A đề nghị (không worker patch)

BE additive optional `artifactManifest` trên callback (không required với callback legacy):

```json
{
  "schemaVersion": "stem-render-manifest.v1",
  "attemptToken": "TEST_ONLY_example_not_a_secret",
  "renderInputHash": "<64 lowercase hex, server-computed expected>",
  "snapshotId": "<opaque snapshot ref>",
  "rendererVersion": "<immutable renderer/template identity>",
  "artifactSha256": null,
  "artifactBytes": null,
  "assurance": "BINDING_ONLY"
}
```

Ví dụ schema là proposal, placeholder không request hợp lệ; attemptToken là correlation nonce, **không thay auth key**. Với fake fixtures, artifact digest có thể literal synthetic SHA của known fixture bytes và assurance TEST_ONLY_SYNTHETIC, không ghi WORKER_MEASURED/MP4_VERIFIED. Optional digest phải actual64hex+nonnegative bounded integer bytes khi có, null nếu unknown; không hash videoUrl làm artifact checksum. Client không được tự chọn assurance có quyền cao hơn server route.

Server tạo attemptToken random, persist expected job/input/snapshot/renderer/version trong frozen sidecar trước dispatch. Test fake integration biết expected manifest từ fixture/owned test orchestration; không mở public GET trả attempt secrets. Worker hiện hữu không nhận/sử dụng manifest mới trong P5 vì **không sửa worker/Remotion**; nếu cần gửi opaque metadata để worker echo phải được mở phạm vi riêng. BE fallback không giả rằng worker echo đã tồn tại.

## 3. Mức bảo đảm và lựa chọn fallback

| Input / condition | Proposed result | Điều không được claim |
|---|---|---|
| Auth/key sai hoặc job không visible/tồn tại | Existing401/403/404; no writes | Token/hash không thay authentication |
| Supplied manifest wrong job attempt/inputHash/snapshot/renderer hoặc invalid digest | 400 malformed /409 binding mismatch; no partial job/clip/current URL update | Không “fallback legacy” để chấp nhận mismatch đã biết |
| Supplied manifest exact, server frozen tuple matches current reviewed lineage | Accept completion + CLIP snapshot BINDING_ONLY (TEST_ONLY_SYNTHETIC trong fixtures); current eligibility theo remaining guards | Hash echo chỉ correlation; even artifactSha256 supplied là assertion của integration, chưa independent byte proof |
| Missing manifest, frozen job/input lineage vẫn exact và no known mismatch | Legacy-compatible completion có clip/snapshot UNKNOWN assurance; không inherit technical review qua thiếu proof. Chỉ publish theo explicit LEGACY_UNVERIFIED acknowledgement, no current-STEM/verified eligibility | Không default MATCH/BYTE_VERIFIED vì status COMPLETED hoặc API key đúng |
| Valid old job nhưng current target đã khác | Giữ completion historical nếu không inconsistent; không ghi current project URL/video eligibility đè mới. Publish current endpoint rejects stale selection | Không cho explicit legacy ack bypass known stale/mismatch |
| Job FAILED/cancelled/completed terminal | Same normalized terminal completion replay no-op; conflicting terminal payload/URL/manifest digest409, late progress ignored/audited | Không đổi approval/QA/publication hoặc accept new artifact qua terminal job |

Không-manifest route áp cho known legacy callback; không xóa stored completed artifacts cũ. New job thiếu snapshot do feature disabled/baseline cũng không fabricated lineage: explicit legacy publish route, require existing current clip/version/composition guards + QA, no known mismatch. Khi lineage biết chắc sai (wrong version/composition/job hoặc provided hash mismatch), luôn hard block mọi mode, acknowledgement không cứu.

Idempotency: normalized meaningful terminal payload gồm job/attempt/status/videoUrl/artifact digest/manifest identity; progress/stage không thay artifact. Same payload replay trả existing clip, no duplicate snapshot; payload khác conflict. Retry creates new job + immutable render snapshot linked source attempt, reads frozen props; same input hashes bằng, attempt binding khác. Callback late từ attempt cũ không áp vào new job.

## 4. True artifact digest và worker boundary

Đọc bytes MP4 độc lập để verify checksum cần controlled artifact store/worker contract, allowlisted retrieval/size/time/network policy và versioned renderer identity. Nó ngoài Q11 A (không MP4 thật), không tự tải arbitrary callback URL, không mutate worker/assets. Proposal không tuyên bố hash echo hoặc signed URL same là proof output đúng. Audio content digest/transcript thiếu thì F13 compatibility UNKNOWN; không gọi TTS/Whisper/provider thật để lấp gap.

Nếu người dùng muốn strict manifest required cho worker thực tế hoặc independent artifact checksum verification: dừng phần phụ thuộc, trình scope worker/store riêng. Không tự mở P6/P8 hoặc sửa production callback compatibility. Trong P5 hiện tại ưu tiên **phương án A với fake manifest + explicit assurance**, chờ xác nhận cùng số/location migration.

## 5. Tests dự kiến, hiện NOT_RUN

B03 wrong input/attempt/snapshot/hash; malformed400; authenticated callbacks; no partial writes; duplicate completion same vs conflicting; late old job not current; retry same frozen input/new attempt; both B01 CLIP and B02 COMPOSITION flow qua real Spring/JWT/local HTTP + fake integration; missing-manifest explicit legacy ack allowed only unknown/no known mismatch; P01/P02/G05/YouTube503 no-write regressions. Không browser/MP4/real audio proof; fixtures DRAFT/UNVERIFIED và TEST_ONLY.
