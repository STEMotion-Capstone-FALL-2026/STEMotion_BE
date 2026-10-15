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

## OpenAI routing

AI_PROVIDER defaults to openai. OPENAI_API_KEY is read only from the service process environment; the owner creates/provides the key. No key is generated or provisioned by this repository. OPENAI_MODEL_DRAFT defaults to gpt-4o; OPENAI_MODEL_LIGHT defaults to gpt-4o-mini for resegment/grade/extract/terms. Exact provider model names from response metadata and raw token usage are returned to M01. Missing key/configuration fails 503. Refusal, malformed output and provider errors never become placeholder lesson data. AI_PROVIDER=gemini is an explicit service rollback; AI_SERVICE_MODE=embedded is the separate BE rollback.

LangChain uses JSON schema mode with include_raw=True and strict=False to support the existing optional per-template fields. Independent JSON Schema and scientific-field validation still run on script endpoints; structured output is not treated as proof of scientific truth.

References: [OpenAI structured outputs](https://developers.openai.com/api/docs/guides/structured-outputs), [GPT-4o mini](https://developers.openai.com/api/docs/models/gpt-4o-mini), [LangChain ChatOpenAI](https://docs.langchain.com/oss/python/integrations/chat/openai). Tests mock ChatOpenAI; no actual key or paid request is used.
