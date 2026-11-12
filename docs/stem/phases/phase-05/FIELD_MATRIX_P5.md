# P5 — Field matrix: 13 scenes / 12 block kinds

05/11/2026. Chặng1, design recorded; không implementation/test. Nguồn: P2 FIELD_CLASSIFICATION_AND_FRESHNESS; B ScenePropsValidator/ComposedSceneValidator; F src/types/stem.ts, composed normalizer và renderer COMPOSED. Renderer arbitrary pixel/audio proof vẫn NOT_RUN. Không tăng enum hoặc bịa block table.

## 1. Quy tắc phủ và projection

S = semantic; C = conditional presentation contract; R = render input; I = identity/binding. Mọi S cũng nằm trong presentationContract qua semanticHash và trong frozen render input khi renderer tiêu thụ. Mọi C/R phải được giữ raw hoặc normalized có proof, không silently drop. Unknown path lưu raw dưới unknown, vào semantic+contract+render projections và UNKNOWN/NEEDS_REVIEW; legacy shape hợp lệ vẫn được lưu/read theo schema hiện hữu, new malformed DTO vẫn400. Không lấy FE normalizer (có sửa icon/drop block/trim text) làm canonical semantic projector: projector hash đúng saved/rendered representation, không tự sửa meaning để hash trùng.

| Common path | Class / semantic inclusion | Contract/R input | Safe subset / failure rule |
|---|---|---|---|
| scenes order / type / title / narration | S; giữ thứ tự scene/token/case/dấu | All R, narration tokens C | Reorder hoặc title/narration change luôn hash semantic khác |
| scene.id / version.id / block.id / evidence.id | I; semantic reference map giữ quan hệ thay generated IDs | R giữ renderer seed ID; full snapshot binding giữ originals | UUID khác target vẫn cần review; renderer useVariation(id) làm ID ảnh hưởng render, không bỏ renderInput |
| captionWords[].text | S, giữ ordered exact tokens NFC | C token anchors | “không”/unit/minus đổi → stale |
| captionWords[].start/end | C numeric align | R exact, C giữ event ordering/coverage proof | Finite, start<=end, order và within duration. Có thể safe khi proof không mất nội dung; không global exclude |
| durationInFrames / fps | C (semantic thêm nếu raw unknown context) | R exact; C coverage/events | Duration/fps positive không chứng minh visibility; end cutoff stale/unknown |
| narrationAudioUrl | C transport; content digest/transcript/voice identity resolved riêng | R exact URL+identity; C audio semantics/identity | Signed URL refresh same digest không semantic edit; missing digest/transcript proof UNKNOWN, không fetch arbitrary URL trong stage1 |
| customFontSize/fontSizeScale/cardScale/cardTheme/cardWidth | C | C raw + R | Text clipping hoặc meaning-coded color không safe. Proven unchanged decoration có thể safe; otherwise UNKNOWN |
| componentLayouts[key].dx/dy/scaleX/scaleY/scale/fontSize/theme/hidden/zIndex | C; raw geometry còn trong contract | All R | hidden/overlap/minus clipping không auto-inherit; unknown component key retained raw |
| extraBlocks[] | S/C qua cùng block classifier | All R, ordered | Max proposed12; không schema riêng bỏ qua hash |
| visualPlan | Raw S+C conservatively, UNKNOWN (ngoài supported safe subset) | All R, registry/version/asset identity | Không assume DirectorPlanValidator structure pass = pedagogical/visibility proof |
| capturedAt/updatedAt/actor/modeRevision/draftRevision/receipt key | I/provenance ngoài semantic | Bind và audit; không arbitrary đưa vào R | CAS vẫn kiểm. Mode/policy eligibility recheck; không hash timestamp để stale vô ích |
| unknown property/subtree | Raw conservative S + C | All R nếu input chứa | UNKNOWN, no auto-inheritance; không drop hoặc normalize defaults lạ |

## 2. SceneType-specific fields

Common fields §1 áp lên **từng** scene. Props ở DB nested được flatten theo actual SceneMapper/render representation, có collision guard; không merge props đè id/type/title/narration authority. Mỗi S leaf phải có mutation test sau gate, ordered children không sort. Optional semantic field missing/null phân biệt trừ typed contract khai báo normalize.

| # | SceneType | S exact paths từ schema hiện hữu | C/unknown paths và relationship checks |
|---|---|---|---|
| 1 | TITLE_HERO | subtitle,subject,gradeLevel,badgeText | Badge visibility/layout; subject legacy không tự curriculum mapping |
| 2 | MATH_FORMULA | latex; steps[].label/latexSnippet/explanation (ordered) | Step reveal/clipping formula/minus; không symbolic LaTeX equivalence |
| 3 | DIAGRAM_EXPLAINER | diagramTitle,svgType; labels[].name/description | labels[].xPercent/yPercent C, default conservative relationship-critical; altered pairing→contract differs |
| 4 | DATA_CHART | chartType,xAxisLabel,yAxisLabel,dataPoints[].label/value ordered | dataPoints[].color C (may encode groups); chart animation/geometry C |
| 5 | ALGORITHM_WALKTHROUGH | language,codeSnippet; steps[].lineHighlight/variableState/note ordered | layoutSplit C; which code line highlighted S as actual step content |
| 6 | STEM_QUIZ | question,options[] ordered,correctIndex,hint,explanation | Choice reveal timing/answer color/hidden C |
| 7 | OUTRO | summaryPoints[] ordered,nextLessonSuggestion,instructorName | Conclusion/next task clipped C; name not expert endorsement |
| 8 | CHEMICAL_REACTION | equation,reactants,products,condition,observation | flaskColor C because observation color may matter |
| 9 | COMPARISON_SPLIT | topicA.title/badge/points[]; topicB.title/badge/points[]; conclusion | topicA/B.color C, layout/font C; preserve A/B pairing |
| 10 | PROCESS_TIMELINE | processTitle; stages[].stageNumber/title/description/badge ordered | Step reveal/order/arrow/safety coverage C |
| 11 | GEOMETRY_SPACE | shapeType,theoremName,formulaLatex,dimensions.a/b/c,explanation | Spatial presentation/label relationships C; extra dimension unknown not ignored |
| 12 | ILLUSTRATED_EXPLAINER | headline,caption,icons[].name/label ordered; layout treated S conservatively because exchange/zoom express relations | ambience/detail C; icon registry and visualPlan unknown proof. No icon replacement to sparkle during hashing |
| 13 | COMPOSED | frame conservatively S (slots encode relations); blocks ordered S leaf fields from §3 | ambience C; slot/geometry/cues/motion C/S below; renderer MAX_COMPOSED_BLOCKS12 matches validator |

Field membership known above không tự chứng minh mọi arbitrary geometry safe. Same semantic hash có thể đi cùng different presentationContract và invalidate technical compatibility.

## 3. BlockKind-specific matrix — 12 kinds + extraBlocks

All blocks retain `kind` S, ID/reference mapping I/S, full original ID in R; text optional nếu schema render dùng vẫn S. Block array order giữ. Unrecognized kind không tự biến thành text hoặc bỏ khỏi projector. Baseline validator required content hiện có vẫn áp (220 chars block text, numbers finite, points<=8, options2–4, icons<=6), không weaken để test.

| # | Kind | S content / reference relationship | C presentation constraints |
|---|---|---|---|
| 1 | title | text | slot/font/geometry/cue coverage |
| 2 | text | text | Same common C; no text trim |
| 3 | list | items[] ordered + optional text | Reveal order, strike/cutoff |
| 4 | formula | latex + optional explanatory text | Minus/unit/full expression visible |
| 5 | icon | icon.name/label + optional text | Asset meaning identity; registry digest required for compatibility |
| 6 | icons | icons[].name/label ordered + optional text | Relative placement/relations/asset proof |
| 7 | number | value,unit,text | Count animation may express derivation, not blanket decoration |
| 8 | chart | points[].label/value ordered; text | Chart ordering/colors/layout/visibility |
| 9 | choice | question,options[] ordered,answer,text | Answer reveal relative question and hiding incorrect choices |
| 10 | arrow | from,to,text; resolved directed refs | Slot/geometry/motion that changes direction/connection; no same-target/self dangling |
| 11 | highlight | to,text; target identity (from if present retained) | Highlight pose/coverage may change which claim emphasized |
| 12 | guide | text,pose conservatively S; any referenced relation | Guide slot/point relation must remain matched, no assumption idle/point decorative |

| Common block field | Rule |
|---|---|
| slot | Preserve in contract; conservative semantic if relation/role depends on position. Frame/slot identity resolved deterministically, no slot reassign to hide missing block. |
| at/until/revealAt/strikeAt | **Strings are narration cue anchors**, không numeric seconds (F type). Token anchor text change is semantic/contract change. Same anchor resolve to frame timing using renderer rule; unknown/missing/ambiguous cue not fake coverage. |
| motion pop/rise/slide/draw/type/count | C raw by default; only proved decorative subset can invariant. count/draw/slide may encode direction/quantity; no auto keep approval. |
| emphasis none/pulse/float/shake; tone default/accent/good/bad | C raw; good/bad may encode answer, retain conservatively in S if used semantically. |
| x/y/w/h/scale/fontSize/hidden | C raw, finite/bounded technical parsing; hidden/cutoff/overlap not safe globally. |
| arbitrary extra block props | Preserve raw+UNKNOWN; extraBlocks uses same classifier, never stripped from any projection. |

## 4. Coverage promises và limits

- Stage2 target: deterministic projection/canonical mutation evidence đủ 13 rows scene + 12 rows block + common/unknown paths; F10 count gap được user Q08 xác nhận inventory, không còn giả denominator13.
- Safe timing/theme proof chỉ supported subset theo actual renderer rules; arbitrary layout/asset/visualPlan/pixel/audio proof giữ UNKNOWN hoặc NOT_RUN. Không hứa tất cả combinations/classroom-safe.
- F01/AT21 safe numeric sync, F02 clip/cutoff, F03 tokens, F04 cue ordering, F05 motion, F06 visibility, F07 theme, F08 unknown, F09/10 inventory mutation, F11 canonical, F12 number/LaTeX/unit, F13 audio identity, F14 edition/version: phải tách expected/actual. Tất cả **NOT_RUN ở Chặng1**.
