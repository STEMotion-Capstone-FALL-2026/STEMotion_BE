"""Private AI execution service. BE owns user/workspace authorization and saves."""
import asyncio
import hmac
import os
import time
from typing import Any

from fastapi import Depends, FastAPI, Header, HTTPException
from pydantic import BaseModel, Field


class GenerationRequest(BaseModel):
    systemInstruction: str = Field(min_length=1, max_length=20000)
    prompt: str = Field(min_length=1, max_length=80000)
    responseSchema: dict[str, Any]


class GenerationResponse(BaseModel):
    result: dict[str, Any]
    model: str
    elapsedMs: int


def require_service_key(x_ai_api_key: str = Header(default="")) -> None:
    expected = os.getenv("AI_SERVICE_KEY", "")
    if not expected:
        raise HTTPException(503, "AI service key is not configured")
    if not hmac.compare_digest(expected.encode(), x_ai_api_key.encode()):
        raise HTTPException(401, "Invalid service key")


def json_schema(value: Any) -> Any:
    """Translate the existing BE Gemini schema to LangChain JSON Schema."""
    if isinstance(value, list):
        return [json_schema(item) for item in value]
    if not isinstance(value, dict):
        return value
    result = {key: json_schema(item) for key, item in value.items() if key != "nullable"}
    if isinstance(result.get("type"), str):
        result["type"] = result["type"].lower()
    if value.get("nullable"):
        result = {"anyOf": [result, {"type": "null"}]}
    return result


class GeminiProvider:
    async def generate(self, request: GenerationRequest) -> tuple[dict[str, Any], str]:
        key = os.getenv("GEMINI_API_KEY", "")
        if not key:
            raise HTTPException(503, "Gemini provider is not configured")
        # Lazy import keeps health checks and isolated contract tests independent
        # of provider initialization. Keys never leave this service.
        from langchain_google_genai import ChatGoogleGenerativeAI
        from langchain_core.messages import HumanMessage, SystemMessage

        model = os.getenv("GEMINI_MODEL", "gemini-3.5-flash-lite")
        llm = ChatGoogleGenerativeAI(model=model, api_key=key, temperature=0.4, max_retries=2)
        schema = json_schema(request.responseSchema)
        schema.setdefault("title", "STEMotionAIResult")
        schema.setdefault("description", "Structured STEMotion AI response")
        chain = llm.with_structured_output(schema, method="json_schema")
        result = await asyncio.wait_for(chain.ainvoke([
            SystemMessage(content=request.systemInstruction), HumanMessage(content=request.prompt)
        ]), timeout=60)
        if not isinstance(result, dict):
            raise ValueError("Expected structured object")
        return result, model


def provider() -> GeminiProvider:
    return GeminiProvider()


app = FastAPI(title="STEMotion AI Service", version="1.0.0", docs_url=None, redoc_url=None)


@app.get("/health")
def health():
    # Does not call Gemini or expose credentials. Readiness is configuration only.
    return {"status": "UP", "configured": bool(os.getenv("AI_SERVICE_KEY") and os.getenv("GEMINI_API_KEY"))}


@app.post("/internal/v1/generate", response_model=GenerationResponse, dependencies=[Depends(require_service_key)])
async def generate(request: GenerationRequest, client: GeminiProvider = Depends(provider)):
    started = time.monotonic()
    try:
        result, model = await client.generate(request)
        return GenerationResponse(result=result, model=model, elapsedMs=int((time.monotonic() - started) * 1000))
    except HTTPException:
        raise
    except TimeoutError:
        raise HTTPException(504, "AI provider timed out") from None
    except Exception:
        # Provider exceptions can contain request URLs/keys. Never send them to FE.
        raise HTTPException(502, "AI provider request failed") from None
