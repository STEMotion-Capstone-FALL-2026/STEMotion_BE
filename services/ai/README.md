# STEMotion AI service

Separate FastAPI process with LangChain and Gemini. Kept under the BE repository
so CI and deployment can use the existing repository; it has its own Dockerfile,
dependencies and HTTP boundary. FE continues calling BE `/api/v1/ai/*`.
BE owns authorization, prompts, scene validation and persistence. This service
executes the model and returns structured JSON; it never connects to Supabase.

## Local

From this directory:

```powershell
python -m venv .venv
.venv/Scripts/python -m pip install -r requirements.lock
# Set AI_SERVICE_KEY, GEMINI_API_KEY and GEMINI_MODEL privately.
.venv/Scripts/python -m uvicorn app.main:app --host 127.0.0.1 --port 8000
.venv/Scripts/python -m pytest -q
```

## Deployment and rollout

Create a separate Docker Web Service with root directory `services/ai`.
Configure `AI_SERVICE_KEY`, the existing `GEMINI_API_KEY`, and `GEMINI_MODEL` on
that service. Do not put either key in FE. Use `/health` for the health check;
`configured` only confirms environment variables, not a successful provider call.
The internal generation endpoint requires `X-AI-Api-Key` and has no browser CORS.

Once tested, configure BE:

```dotenv
AI_SERVICE_MODE=remote
AI_SERVICE_URL=https://<ai-service-host>
AI_SERVICE_KEY=<same-shared-key-as-ai-service>
```

Remote mode has no fallback to the embedded provider. An unavailable AI service
returns an explicit error. Keep `AI_SERVICE_MODE=embedded` during rollout; revert
to that mode to roll back. Remove the Gemini key from BE only after remote mode
has been verified. The model reported to FE comes from the service response.

Captions/Whisper, moving prompt ownership into this service and a dedicated
repository are follow-up work; this change does not claim those are implemented.

References: [LangChain Gemini structured output](https://reference.langchain.com/python/langchain-google-genai/chat_models/ChatGoogleGenerativeAI/with_structured_output),
[FastAPI testing](https://fastapi.tiangolo.com/tutorial/testing/).
