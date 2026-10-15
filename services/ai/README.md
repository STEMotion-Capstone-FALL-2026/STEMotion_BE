# STEMotion AI service

Location: `services/ai/` inside BE. Reuses the existing FastAPI/LangChain boundary instead of creating a second `ai-service/` tree; independently built Python process, no Java runtime dependency.

Start: install `requirements.lock`, set `AI_SERVICE_KEY` and provider credentials in process env, then `uvicorn app.main:app --host 127.0.0.1 --port 8000` from this directory. Never copy BE `.env` into the image. Docker exposes the same app on port 8000. Private endpoints require `X-AI-Api-Key`; the BE owns JWT/RBAC and persistent M01 audit. A blank service key rejects requests.

- `POST /script/generate`: topic, subject, gradeLevel, optional targetDurationSec (0 = auto), narrativeBrief <=500 chars.
- `POST /script/action`: action=resegment/grade/extract/terms plus scriptText, subject, gradeLevel.
- `POST /script/resegment`, `/script/grade`, `/script/extract`, `/script/terms`: same analysis body without action.
- All return the existing result/model/elapsedMs/inputTokens/outputTokens/totalTokens envelope. Unknown usage is null, never a fabricated zero.
- `/internal/v1/generate` remains for compatibility with the former generic gateway. It is private, accepts a prompt/schema, and is not the new script entrypoint.

Script prompts and schemas were ported from integrated BE base 2bf41da, including guardrail, free narrative arc, dynamic pacing, template exclusions and icon catalog. Python validates structured output before returning it: missing formulas/equations, bad quantitative data, unknown icons, missing scene content or broken structure fail 502; no sample lesson data is substituted. A guardrail rejection returns isValidStemTopic=false and empty scenes; BE preserves the existing 400 user-facing behavior. Validation checks completeness/shape, not proof of scientific truth: review by a human is still required.

BE runtime defaults `AI_SERVICE_MODE=remote`, configured through `AI_SERVICE_URL`/`AI_SERVICE_KEY`. `AI_SERVICE_MODE=embedded` explicitly rolls back to the unchanged Java/Gemini prompt path. There is no silent remote-to-embedded fallback. M01 still persists actual model/usage and failures through AiGateway; public BE endpoint contracts stay stable.

Tests: `python -m pytest -q`. Providers are dependency-overridden; test collection/health never initializes or downloads a model or calls a paid API.
