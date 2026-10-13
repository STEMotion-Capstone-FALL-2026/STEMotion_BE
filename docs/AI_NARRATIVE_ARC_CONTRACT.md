# AI Narrative Arc — Step 1

## Request

`POST /api/v1/ai/script/generate` (API base `/api/v1` + `/ai/script/generate`).

New optional field: `narrativeBrief?: string`, nullable, maximum **500 characters**.
Omitted/null/empty/whitespace: AI chooses the narrative arc. A supplied brief directs
the hook, order of ideas and rhythm. Briefs cannot override STEM topic guardrails,
scientific accuracy, template constraints or dynamic pacing. Overlong input returns
HTTP 400 validation error before calling the provider.

```json
{
  "topic": "Định luật Ôm",
  "subject": "Physics",
  "gradeLevel": "10",
  "targetDurationSec": 60,
  "narrativeBrief": "Dạy định luật Ôm như một gameshow, phỏng vấn một electron."
}
```

## Response

Existing `AiActionResponse` envelope is unchanged. `result.narrativeArc` is optional:

```json
{
  "narrativeArc": {
    "summary": "Electron tham gia gameshow khám phá quan hệ giữa U, I và R.",
    "approach": "gameshow",
    "segments": [
      { "order": 1, "title": "Electron lên sân khấu", "purpose": "Gây tò mò" },
      { "order": 2, "title": "Thử thách điện trở", "purpose": "Giải thích I = U/R" }
    ]
  }
}
```

Example above is the new portion inside `response.result`; existing scenes and other
metadata remain. `summary` and `approach` are required within a returned arc;
`segments` is optional in the provider schema. Each returned segment requires numeric
`order`, string `title` and string `purpose`. Segment order expresses the narrative;
it is not a new scene ID or a database reference. BE passes the JSON through without
deep parsing, auto-repair or invented placeholders.

Invalid topics retain the existing HTTP 400 `ApiException.badRequest` behavior,
including when a brief is supplied; the model's rejection may omit the arc entirely.
FE should tolerate an absent arc and display a returned arc to the Writer. This task
does not implement FE editing/persistence or change the save-draft API.

## Local verification — 2026-10-14

- `mvn -Dtest=AiScriptServiceTest test`: 12/12 passed, mocked Gemini only.
- `mvn -Dtest=AiScriptServiceTest,GlobalExceptionHandlerTest test`: 25/25 passed.
- Expanded clean check with `ScenePropsValidatorTest`: 50/51 passed; the sole
  existing validator failure is described below and is not hidden/skipped.
- `mvn -DskipTests package`: BUILD SUCCESS after the clean test compilation.
- `git diff --check`: passed. No full suite or live AI quality evaluation claimed.
- Coverage: Writer brief injection into prompt; null/blank auto choice; 500-character
  boundary / HTTP 400 for 501; arc/segments pass-through and optional schema;
  invalid topic with brief still rejected; all dynamic pacing branches; older JSON
  requests without the new field remain compatible.
- Local ignored logs: workspace `.audit/narrative-arc-step1-*.log`.

## Scope and baseline

Implemented against BE `origin/main` baseline `3c13c06` on local branch
`feat/narrative-arc-step1`. That baseline contains the approved dynamic pacing and
template constraints and uses embedded `GeminiClient`. `deploy/render-staging` is
a separate baseline using `AiGateway`; it is unchanged by this task. Integration into
the deployment branch is a separate step, not an automatic replacement of its code.

Known baseline discrepancy: the broader scene-validator check fails
`ScenePropsValidatorTest.rejectsUnknownIcon`. Baseline commit `3c13c06` changed unknown
icons from rejection to a `sparkle` fallback while retaining the rejection test.
Both files are byte-identical to that baseline in this task. This behavior conflicts
with the previously approved allowlist/fail-closed policy and needs separate review;
it is not changed here because template registry/asset allowlist work is explicitly
excluded. Do not interpret the narrative-arc tests as verification of that validator.

No DB migration, template registry/asset allowlist change, `runAction` change, live AI
call, push or deployment. TITLE_HERO first, OUTRO last, excluded template types and
scientific-accuracy requirements remain unchanged. Template × Asset Library remains
the architecture; free block assembly is deferred.
