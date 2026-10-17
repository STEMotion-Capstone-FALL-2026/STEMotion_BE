"""Private AI service. BE owns user authorization, durable audit and saves."""
import asyncio
import hmac
import os
import time
from fastapi import Depends, FastAPI, Header, HTTPException, UploadFile, File, Form
from starlette.concurrency import run_in_threadpool
from fastapi.responses import JSONResponse
from .deployment import shared_key, configured, readiness
from .alignment import aligner, MAX_AUDIO_BYTES
from .models import GenerationRequest, GenerationResponse, ScriptInput, AnalysisInput, ActionInput
from .providers import GeminiProvider, OpenAIProvider
from .schemas import json_schema
from .prompting import build_request
from .validation import normalize_scenes, validate_result

def require_service_key(x_ai_api_key: str = Header(default="")) -> None:
    expected = shared_key()
    if not expected:
        raise HTTPException(503, "AI service key is not configured")
    if not hmac.compare_digest(expected.encode(), x_ai_api_key.encode()):
        raise HTTPException(401, "Invalid service key")


def provider():
    name = os.getenv("AI_PROVIDER", "openai")
    if name == "openai":
        return OpenAIProvider()
    if name == "gemini":
        return GeminiProvider()
    raise HTTPException(503, "Unsupported AI provider configuration")

app = FastAPI(title="STEMotion AI Service", version="2.0.0", docs_url=None, redoc_url=None)

@app.get("/health")
def health():
    name = os.getenv("AI_PROVIDER", "openai")
    return {"status": "UP", "provider": name,
        "configured": configured()}

@app.get("/ready", responses={503: {"description": "Configuration or Whisper preflight is not ready"}})
def ready():
    state = readiness()
    return JSONResponse(state, status_code=200 if state["status"] == "READY" else 503)

async def execute(request, client, validate_operation=None):
    started = time.monotonic()
    try:
        result, model, usage = await client.generate(request)
        if validate_operation:
            result = normalize_scenes(validate_operation, result)
            validate_result(validate_operation, result)
        def count(key):
            value = usage.get(key)
            return value if isinstance(value, int) and not isinstance(value, bool) and value >= 0 else None
        return GenerationResponse(result=result, model=model, elapsedMs=int((time.monotonic() - started) * 1000),
            inputTokens=count("input_tokens"), outputTokens=count("output_tokens"), totalTokens=count("total_tokens"))
    except HTTPException:
        raise
    except TimeoutError:
        raise HTTPException(504, "AI provider timed out") from None
    except Exception:
        raise HTTPException(502, "AI provider request failed") from None

@app.post("/internal/v1/generate", response_model=GenerationResponse, dependencies=[Depends(require_service_key)])
async def generate(request: GenerationRequest, client=Depends(provider)):
    return await execute(request, client)

@app.post("/script/generate", response_model=GenerationResponse, dependencies=[Depends(require_service_key)])
async def generate_script(request: ScriptInput, client=Depends(provider)):
    return await execute(build_request("generate", request), client, "generate")

@app.post("/script/action", response_model=GenerationResponse, dependencies=[Depends(require_service_key)])
async def action(request: ActionInput, client=Depends(provider)):
    return await execute(build_request(request.action, request), client, request.action)

def action_handler(operation):
    async def handler(request: AnalysisInput, client=Depends(provider)):
        return await execute(build_request(operation, request), client, operation)
    return handler

for operation in ("resegment", "grade", "extract", "terms"):
    app.add_api_route("/script/" + operation, action_handler(operation), methods=["POST"],
        response_model=GenerationResponse, dependencies=[Depends(require_service_key)])


@app.post("/transcribe/align", dependencies=[Depends(require_service_key)])
async def align_audio(file: UploadFile = File(...), text: str = Form(..., min_length=1, max_length=10000),
        language: str = Form("vi", min_length=2, max_length=8), engine=Depends(aligner)):
    if not text.strip():
        raise HTTPException(422, "Known narration text is required for forced alignment")
    try:
        audio = await file.read(MAX_AUDIO_BYTES + 1)
        if not audio or len(audio) > MAX_AUDIO_BYTES:
            raise HTTPException(413, "Audio must contain 1 to 10 MiB")
        return await run_in_threadpool(engine.align, audio, text.strip(), language)
    except HTTPException:
        raise
    except Exception:
        raise HTTPException(502, "Alignment failed") from None
    finally:
        await file.close()
