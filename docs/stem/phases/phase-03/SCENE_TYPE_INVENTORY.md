# P3.7 — Inventory SceneTypes, 05/11/2026

BE f6c6f0b `core/enums/StemEnums.java:SceneType`, DTO SceneDtos và ScenePropsValidator dùng enum này; FE **committed**9fe67cf `src/types/stem.ts:SceneType` (không lấy dirty asset work). BE và FE cùng13 tên dưới đây. Worker3344a29 `src/types.ts:RenderRequest` nhận `inputProps: Record<string, unknown>`, không khai enum scene độc lập; worker gốc vẫn022ea0a, không checkout/pull. Đây là đọc git object, không build/test worker.

- TITLE_HERO
- MATH_FORMULA
- DIAGRAM_EXPLAINER
- DATA_CHART
- ALGORITHM_WALKTHROUGH
- STEM_QUIZ
- OUTRO
- CHEMICAL_REACTION
- COMPARISON_SPLIT
- PROCESS_TIMELINE
- GEOMETRY_SPACE
- ILLUSTRATED_EXPLAINER
- COMPOSED

`V15__composed_scene.sql` (history commit02979f4) thêm COMPOSED vào12 loại cũ; không loại mới thứ14. 12=13 trừ COMPOSED trong cách đếm trước V15. BE/FE list equal: True. Extraction checked final enum member without trailing comma. Worker không enum để giả tuyên bố đồng nhất schema; kiểm adapter/render projector vẫn cần P4/P5. Không thêm/xóa SceneType ở P3.

Unknown fields/types không bỏ qua trong freshness; P3 bảo thủ NEEDS_REVIEW khi content revision đổi, không dùng revision/timestamp làm semantic hash. C07 chỉ đối chiếu inventory/contract, chưa khóa classifier implementation. Ba hash semantic/presentationContract/renderInput, AT21 và timing/motion đổi nghĩa chưa kiểm chứng ở P3, NOT RUN đến P4/P5.

Historical parent `02979f4^` enum inspected: 12 members: TITLE_HERO, MATH_FORMULA, DIAGRAM_EXPLAINER, DATA_CHART, ALGORITHM_WALKTHROUGH, STEM_QUIZ, OUTRO, CHEMICAL_REACTION, COMPARISON_SPLIT, PROCESS_TIMELINE, GEOMETRY_SPACE, ILLUSTRATED_EXPLAINER. Difference to f6c6f0b is COMPOSED only.
