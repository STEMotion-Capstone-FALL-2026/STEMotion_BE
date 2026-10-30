from typing import Any
import os
import asyncio
from fastapi import HTTPException
from .models import GenerationRequest
from .schemas import drop_optional_nulls, json_schema, strict_schema


class AnthropicProvider:
    """Claude is a distinct provider; no key, SDK or paid call is needed at import time."""
    async def generate(self, request: GenerationRequest) -> tuple[dict[str, Any], str, dict[str, Any]]:
        import json
        import httpx
        from jsonschema import validate
        key = os.getenv("ANTHROPIC_API_KEY", "").strip()
        model = os.getenv("ANTHROPIC_MODEL_DRAFT", "").strip() if request.task in ("generate", "storyboard") else os.getenv("ANTHROPIC_MODEL_LIGHT", "").strip()
        if not key or not model:
            raise HTTPException(503, "Anthropic provider is not configured")
        schema = json_schema(request.responseSchema)
        # Empty generic object schemas cannot constrain content; all production contracts must be explicit.
        payload = {"model": model, "max_tokens": int(os.getenv("ANTHROPIC_MAX_OUTPUT_TOKENS", "8192")),
            "system": request.systemInstruction, "messages": [{"role": "user", "content": request.prompt}],
            "output_config": {"format": {"type": "json_schema", "schema": strict_schema(schema)}}}
        async with httpx.AsyncClient(timeout=55, follow_redirects=False) as client:
            response = await client.post("https://api.anthropic.com/v1/messages", json=payload,
                headers={"x-api-key": key, "anthropic-version": os.getenv("ANTHROPIC_API_VERSION", "2023-06-01")})
        if response.status_code in (401, 403):
            raise HTTPException(503, "Anthropic credential or model access is invalid")
        if response.status_code == 429:
            raise HTTPException(429, "Anthropic rate limit or credit limit reached")
        if not response.is_success:
            raise HTTPException(502, "Anthropic provider request failed")
        body = response.json()
        if body.get("stop_reason") != "end_turn":
            raise ValueError("Claude output was refused, truncated or incomplete")
        parts = body.get("content", [])
        if any(p.get("type") == "refusal" for p in parts):
            raise ValueError("Claude refused the request")
        result = json.loads("".join(p.get("text", "") for p in parts if p.get("type") == "text"))
        validate(result, strict_schema(schema))
        result = drop_optional_nulls(result, schema)
        usage = body.get("usage") or {}
        inp, out = usage.get("input_tokens"), usage.get("output_tokens")
        # Unknown counts remain unknown, including provider-specific cached/image billing details.
        counts = {"input_tokens": inp, "output_tokens": out}
        if all(isinstance(v, int) and not isinstance(v, bool) and v >= 0 for v in (inp, out)):
            counts["total_tokens"] = inp + out
        return result, body.get("model") or model, counts

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
        model = os.getenv("OPENAI_MODEL_DRAFT", "gpt-4o") if request.task in ("generate", "storyboard") else os.getenv("OPENAI_MODEL_LIGHT", "gpt-4o-mini")
        if not key or not model.strip():
            raise HTTPException(503, "OpenAI provider is not configured")
        from langchain_openai import ChatOpenAI
        from langchain_core.messages import HumanMessage, SystemMessage
        options = {"model": model, "api_key": key, "max_retries": 2, "timeout": 55}
        # Reasoning models may reject temperature. Omit it unless explicitly configured.
        if os.getenv("OPENAI_TEMPERATURE", "").strip():
            options["temperature"] = float(os.environ["OPENAI_TEMPERATURE"])
        llm = ChatOpenAI(**options)
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


class TaskRoutedProvider:
    """Optional server-controlled routing; clients cannot submit provider/key/endpoint overrides."""
    async def generate(self, request: GenerationRequest):
        key = "AI_STORYBOARD_PROVIDER" if request.task == "storyboard" else (
            "AI_REVIEW_PROVIDER" if request.task == "grade" else "AI_PROVIDER")
        name = os.getenv(key, "").strip() or os.getenv("AI_PROVIDER", "openai")
        factories = {"openai": OpenAIProvider, "anthropic": AnthropicProvider, "gemini": GeminiProvider}
        if name not in factories:
            raise HTTPException(503, "Unsupported AI provider configuration")
        return await factories[name]().generate(request)
