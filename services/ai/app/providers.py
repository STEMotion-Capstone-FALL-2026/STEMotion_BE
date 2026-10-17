from typing import Any
import os
import asyncio
from fastapi import HTTPException
from .models import GenerationRequest
from .schemas import drop_optional_nulls, json_schema, strict_schema

class GeminiProvider:
    async def generate(self, request: GenerationRequest) -> tuple[dict[str, Any], str, dict[str, Any]]:
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
        chain = llm.with_structured_output(schema, method="json_schema", include_raw=True)
        result = await asyncio.wait_for(chain.ainvoke([
            SystemMessage(content=request.systemInstruction), HumanMessage(content=request.prompt)
        ]), timeout=60)
        parsed = result.get("parsed")
        if result.get("parsing_error") or not isinstance(parsed, dict):
            raise ValueError("Expected structured object")
        usage = getattr(result.get("raw"), "usage_metadata", None) or {}
        return parsed, model, usage



class OpenAIProvider:
    """Task routing is explicit; no provider calls happen at import/health time."""
    async def generate(self, request: GenerationRequest) -> tuple[dict[str, Any], str, dict[str, Any]]:
        key = os.getenv("OPENAI_API_KEY", "").strip()
        model = os.getenv("OPENAI_MODEL_DRAFT", "gpt-4o") if request.task == "generate" else os.getenv("OPENAI_MODEL_LIGHT", "gpt-4o-mini")
        if not key or not model.strip():
            raise HTTPException(503, "OpenAI provider is not configured")
        from langchain_openai import ChatOpenAI
        from langchain_core.messages import HumanMessage, SystemMessage
        llm = ChatOpenAI(model=model, api_key=key, temperature=0.4, max_retries=2, timeout=55)
        original = json_schema(request.responseSchema)
        # Non-strict outputs dropped required fields (narration, durationInFrames, title), so
        # whole drafts were refused. Strict mode makes the model return every required field.
        schema = strict_schema(original)
        schema.setdefault("title", "STEMotionAIResult")
        schema.setdefault("description", "Structured STEM result; scientific content must not be fabricated")
        chain = llm.with_structured_output(schema, method="json_schema", strict=True, include_raw=True)
        response = await asyncio.wait_for(chain.ainvoke([
            SystemMessage(content=request.systemInstruction), HumanMessage(content=request.prompt)
        ]), timeout=60)
        parsed = response.get("parsed")
        if response.get("parsing_error") or not isinstance(parsed, dict):
            raise ValueError("Missing structured output or model refusal")
        parsed = drop_optional_nulls(parsed, original)
        raw = response.get("raw")
        usage = getattr(raw, "usage_metadata", None) or {}
        metadata = getattr(raw, "response_metadata", None) or {}
        return parsed, metadata.get("model_name") or model, usage
