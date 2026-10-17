# OpenAI model config and Render env audit — 2026-10-17

Branch: feat/ai-microservice, base 7e196b6. Model/env changes prepared locally; this task authorizes commit and push to origin/feat/ai-microservice only. No deploy or production migration.

## Models and price provenance

Requested targets retained: draft `gpt-4.1`, light tasks `gpt-4o-mini` (final user choice for lower cost; supersedes the uncommitted 4.1-mini choice).
Official pages checked 2026-10-17:
- https://developers.openai.com/api/docs/models/gpt-4.1
- https://developers.openai.com/api/docs/models/gpt-4o-mini
- https://developers.openai.com/api/docs/pricing

Both selected model pages also display Deprecated. Migration follows the explicit requested target; it does not establish a non-deprecated long-term choice or a shutdown date. Both support structured outputs; no SDK, endpoint, prompt or request-schema change was needed for this config update.

| Workload | Previous config | New default | Old input/output USD per 1M | New input/output USD per 1M |
| --- | --- | --- | --- | --- |
| Draft | gpt-4o-2024-08-06 | gpt-4.1 | 2.50 / 10.00 | 2.00 / 8.00 |
| resegment/grade/extract/terms | gpt-4o-mini-2024-07-18 | gpt-4o-mini | 0.15 / 0.60 | 0.15 / 0.60 |

Old rates are the preserved historical config, not a new price lookup. New rates are standard uncached text, excluding cached-input/Batch discounts, tools and other modalities. Draft cost decreases 20%; light-task rate stays at its previous level, 62.5% below the superseded 4.1-mini choice. For 1000 input/500 output mock tokens: draft $0.006, light $0.00045.

New active pricing fixture: `config/ai-pricing-2026-10-17.json`; keep `config/ai-pricing-2026-10-16.json` and historical acceptance reports unchanged as evidence. New prices include alias and the corresponding snapshot (draft: 2025-04-14; light: 2024-07-18) because the provider returns actual model metadata and BE matches exact IDs. Unknown models/tokens or missing pricing still produce null. Existing request costs are never rewritten.

GET https://api.openai.com/v1/models returned HTTP 200 on 2026-10-17; the first request confirmed the 4.1 pair; a second request after the final light-model decision confirmed gpt-4.1, gpt-4o-mini and gpt-4o-mini-2024-07-18. Credential used only in the transient process, never added to project files. This confirms listing/access visibility, not quota, credit, successful generation or live lesson quality. No billable generation request was made. Key supplied in chat should be revoked/replaced before production use.

## Env ownership and exact names

Spring BE does NOT call OpenAI directly in remote mode. `AiGateway` and `AiAlignmentClient` call FastAPI. `GeminiClient` remains the explicit embedded rollback; `AI_SERVICE_MODE=embedded` selects Gemini, not OpenAI. Setting OPENAI_API_KEY on the BE service alone cannot enable OpenAI.

`AI_MODEL_DRAFT` and `AI_MODEL_LIGHT` are not read anywhere. Use `OPENAI_MODEL_DRAFT` and `OPENAI_MODEL_LIGHT` on Python ai-service.

| Exact env name | Consumer/evidence | Required? | Example/operator setting |
| --- | --- | --- | --- |
| AI_SERVICE_MODE | BE application.yml:61 / application-cloud.yml:37; AiGateway | Set explicitly for OpenAI remote flow; YAML default remote | remote |
| AI_SERVICE_URL | BE application.yml:62 / application-cloud.yml:38; AiGateway and AiAlignmentClient | Remote, reachable real URL | https://ai.example.com (replace) |
| AI_SERVICE_SHARED_KEY | BE YAML and services/ai/app/deployment.py:8 | Remote, same private value on both services | <same-generated-private-secret> |
| AI_SERVICE_KEY | Same consumers | Legacy alias only; prefer shared name | Do not set if canonical name is used |
| AI_PRICING_JSON | BE AiCostCalculator.java:17 | Needed for estimates, optional for generation | JSON below |
| AI_USAGE_ZONE | BE domain/admin/service/AiUsageService.java:19 | Optional; default shown | Asia/Ho_Chi_Minh |
| AI_MONTHLY_BUDGET_USD | BE AdminMetricsService.java:45 | Optional dashboard target; NOT enforced cap/log-only | 20 |
| AI_ALIGNMENT_WORKER_KEY | BE application.yml:60 and AiAlignmentController | Required only for worker's internal alignment route | <separate-generated-worker-secret> |
| AI_PROVIDER | Python main.py / deployment.py | Optional; default openai; set explicitly | openai |
| OPENAI_API_KEY | Python providers.py:38; readiness configuration | Yes with OpenAI, AI service ONLY | <new-private-key> |
| OPENAI_MODEL_DRAFT | Python providers.py:39 | Has default; explicitly set recommended | gpt-4.1 |
| OPENAI_MODEL_LIGHT | Python providers.py:39 | Has default; explicitly set recommended | gpt-4o-mini |
| GEMINI_API_KEY | BE GeminiClient or Python GeminiProvider | Only embedded BE / AI_PROVIDER=gemini rollback | <private-key-if-using-Gemini> |
| GEMINI_MODEL | BE YAML / Python providers.py | Only Gemini rollback; defaults differ by existing profile | <supported-Gemini-model> |

The extra approaches/metaphor flows retained by the previous merge go through the generic endpoint, whose task defaults to generate, so use the draft model. Four original actions use the light model.

## Whisper/startup env on Python ai-service

Evidence: `services/ai/app/{alignment,deployment,launch}.py`; `services/ai/.env.example`.

| Env | Required? / default | Example |
| --- | --- | --- |
| WHISPER_MODEL_DIR | Complete pre-provisioned directory required by default launcher | /opt/whisper/small (baked), or /models/faster-whisper-small (volume) |
| WHISPER_MODEL | Default small for direct loading; launcher replaces with resolved directory | small |
| WHISPER_DEVICE | Optional cpu | cpu |
| WHISPER_COMPUTE_TYPE | Optional int8 | int8 |
| WHISPER_LOCAL_FILES_ONLY | Optional true; launcher forces true | true |
| WHISPER_MIN_WORD_PROBABILITY | Optional 0.05 | 0.05 |
| WHISPER_STARTUP_MODE | Default required; optional is smoke-only and leaves readiness unavailable without weights | required |
| WHISPER_STARTUP_TIMEOUT_SEC | Optional 90, range 1..600 | 90 |
| UVICORN_WORKERS | Optional 1; each process has separate cached model/lock | 1 |
| PORT | Optional 8000, Render may supply its own | 8000 |
| OMP_NUM_THREADS | Optional native runtime tuning, template value 1 | 1 |

Do not set AI_WHISPER_VERIFIED manually: launcher owns/clears it. It also sets HF_HUB_OFFLINE=1. /health is liveness; /ready requires provider/shared-key config and verified local weights. A configured key is not a provider billing/credit check.

## Render dashboard observation (read-only)

Inspected existing service STEMotion_BE, id srv-db4f7n9srm7s73au73n0, Environment page on 2026-10-17. Only KEY column was read; no Show secret or Edit/Save was used. The expanded direct-variable list contains 13 names:
CORS_ORIGINS, DB_PASSWORD, DB_POOL_SIZE, DB_URL, DB_USERNAME, GEMINI_API_KEY, GEMINI_MODEL, JAVA_TOOL_OPTIONS, JWT_SECRET, RENDER_CALLBACK_URL, RENDER_SERVICE_KEY, RENDER_SERVICE_URL, SPRING_PROFILES_ACTIVE.

Missing from this BE service's displayed list: **AI_SERVICE_MODE, AI_SERVICE_URL, AI_SERVICE_SHARED_KEY, AI_PRICING_JSON, AI_USAGE_ZONE, AI_MONTHLY_BUDGET_USD, AI_ALIGNMENT_WORKER_KEY**. Last three are optional/conditional as described above. Legacy AI_SERVICE_KEY is also absent, but need not be added.

OPENAI_API_KEY, OPENAI_MODEL_DRAFT, OPENAI_MODEL_LIGHT and AI_PROVIDER are also absent on BE, but belong on the separate Python ai-service. No separate AI service dashboard/config was verified; user must check its vars on its actual host (Render or Hetzner). Presence of GEMINI_API_KEY on BE does not establish its value/validity and does not substitute for the remote shared key.

No dashboard values changed; no inference about hidden secret values or inherited environment groups. Required non-AI Spring settings (DB/JWT/CORS/etc.) remain separately necessary. No private .env files were read or changed. Existing private env overrides may still select old models: operator must replace OPENAI_MODEL_* there/on the deployment host without committing secrets.

## AI_PRICING_JSON value for BE

Use the compact or multiline JSON content below as the environment VALUE (no shell assignment/extra quote layer). The application does not auto-load the JSON file; it reads the env string.

```json
{
  "gpt-4.1": {
    "inputPerMillion": 2,
    "outputPerMillion": 8,
    "version": "openai-standard-text-2026-10-17"
  },
  "gpt-4.1-2025-04-14": {
    "inputPerMillion": 2,
    "outputPerMillion": 8,
    "version": "openai-standard-text-2026-10-17"
  },
  "gpt-4o-mini": {
    "inputPerMillion": 0.15,
    "outputPerMillion": 0.6,
    "version": "openai-standard-text-2026-10-17"
  },
  "gpt-4o-mini-2024-07-18": {
    "inputPerMillion": 0.15,
    "outputPerMillion": 0.6,
    "version": "openai-standard-text-2026-10-17"
  }
}
```

## Changes and validation

- Updated Python provider defaults, root/service/Hetzner .env.example templates, Hetzner Compose defaults, deployment docs, pricing fixture and AI tests. Application YAML/AiGateway/GeminiClient do not hardcode OpenAI model IDs and therefore need no model-name edit.
- Java `mvn -q test -Dtest='*Ai*'`: 51 discovered, **43 passed / 8 skipped**, 0 failures/errors. Used matching source+config snapshot without .env to prevent spring-dotenv reading local credentials. Opt-in DB/cloud E2E remained disabled.
- Python `pytest -q`: **52 passed**, mocks/local. New tests cover all five task default routes; existing override and refusal tests remain.
- Pricing tests: alias and returned snapshot estimates, unknown model, missing tokens and missing AI_PRICING_JSON -> null.
- No live generation, credit purchase, model download, DB migration or deploy performed. Commit/push authorized only to feat/ai-microservice.
