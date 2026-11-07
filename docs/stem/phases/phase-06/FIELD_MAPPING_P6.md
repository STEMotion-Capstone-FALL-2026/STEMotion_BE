# P6 — Field/method mapping actual

07/11/2026. G06 wire baseRevision không đổi; learning-context enum vẫn chỉ IN_CLASS_SCREEN/GROUP_PRACTICE. Không public activation/approve AI endpoint.

| Surface | Server source / actual field | FE consumer / giới hạn |
|---|---|---|
| Grounded request | projectId từ path; operation generate/resegment/grade/extract/terms, schemaVersion stem-ai.v1, scriptVersionId và expectedScript/Brief/ModeRevision | scriptService.getGroundedSuggestion / StemGroundedAiPanel; chỉ request click, không autosave |
| Context | Resolver current editable draft + active Writer + brief pin/catalog parent/source closure + evidence/scenes; max closure200, P3 limits | Không gửi từ client; profile PRIMARY_DRAFT chọn vocabulary từ numeric grade1–5 đã lưu, không curriculum certification |
| Grounded response | result/model/requestId/elapsedMs và grounding target/contextDigest/UNVERIFIED/profile/objectiveKeys/activityKeys/warnings | Hiển thị gợi ý nháp và allowed refs từ hồ sơ; không objective coverage/expert verdict |
| Accept log | Existing PUT /ai/requests/{id}/decision thêm optional grounding {projectId,target,contextDigest}; grounded omission400/stale409 | decideAiRequest, không save script. Current request context phải khớp persisted request data, không tin digest claim đơn lẻ |
| Frozen library root | PUBLICATION snapshot đúng publication_id; publicMetadata title/subject/gradeLevel/duration/count/owner/workspace | LibraryItem cũ + nullable learningCard, verificationStatus/snapshotId giữ |
| learningCard | Payload brief.brief: gradeKey/subjectKeys/outcomeIds/context/distributionChannels/objectives/studentTasks/scopeOfClip/safetyNotes; whitelist source edition fields | StemLearningCard: việc làm sau xem, điều kiện/an toàn, hoạt động ngoài MP4, read/copy/link, UNVERIFIED/legacy label |
| educationLevel | Chỉ frozen editions.data.educationLevel khi cùng frozen brief.gradeKey và một level nhất quán | Thiếu→null/Chưa đối chiếu, không current catalog hoặc numeric grade inference trong library |
| Review provenance | Frozen snapshotId/publicationId/capturedMode và edition refs, expertVerified false | Không expose actor/nonce/full frozen input, không gán technical reviewer thành chuyên gia |
| Filter aliases | subject/gradeLevel cũ; grade alias khác gradeLevel→400. subjectId ánh xạ logical brief.subjectKeys, outcomeId là edition ID | Server parameters trước pagination, không FE lọc page để tính tổng |
| Other filters | educationLevel, usageContext, contextRole PRIMARY(default)/SECONDARY/ANY, channel, verificationStatus, q<=200 | Primary/channel tách; values unknown/bad bounds400. Missing curriculum không match valued filter |
| Paging | Array body; page0,size20 mặc định khi yêu cầu, size<=100; X-Total-Count/Pages/Page/Size; stable publication publishedAt/id order | apiClient.getPage + libraryService array pagination metadata. Existing apiClient request/get và legacy callers giữ |
| Facets | Same SQL filter predicate, DISTINCT frozen values; no current curriculum backfill | Advanced filters fetch theo yêu cầu, giữ original unused-filter-fetch regression |
| Detail/read/export | Existing /library/clips/{projectId}, visibility/status guard cũ; video/embed từ đúng publication/clip | Xem/tải/nhúng/sao chép thẻ+link. Không clone project hoặc copy approval |

Library SQL uses parameters and EXISTS for arrays to avoid duplicate rows, COUNT applies the same predicate before LIMIT/OFFSET. Query proof trên synthetic DB nhỏ không phải guarantee production performance. P6 không thêm index/table hoặc claim every field/label đã được expert chốt. Exact null/legacy fallback giữ như CONTRACT_P6.md và tests.
