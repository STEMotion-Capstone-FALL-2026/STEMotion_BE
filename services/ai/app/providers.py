from typing import Any
import os
import asyncio
from fastapi import HTTPException
from .models import GenerationRequest
from .schemas import json_schema

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

