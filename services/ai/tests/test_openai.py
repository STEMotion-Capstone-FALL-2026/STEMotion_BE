import asyncio
import sys
from types import SimpleNamespace
import pytest
from fastapi import HTTPException
from app.models import GenerationRequest
from app.providers import OpenAIProvider

@pytest.mark.parametrize("task,expected", [("generate","custom-draft"),("resegment","custom-light"),
    ("grade","custom-light"),("extract","custom-light"),("terms","custom-light")])
def test_routes_by_task_and_preserves_actual_model_usage(monkeypatch, task, expected):
    monkeypatch.setenv("OPENAI_API_KEY", "test-only-key")
    monkeypatch.setenv("OPENAI_MODEL_DRAFT", "custom-draft")
    monkeypatch.setenv("OPENAI_MODEL_LIGHT", "custom-light")
    class Model:
        def __init__(self, **kwargs):
            assert kwargs["model"] == expected
            assert kwargs["api_key"] == "test-only-key"
            assert kwargs["max_retries"] == 2
        def with_structured_output(self, schema, **kwargs):
            assert kwargs == {"method":"json_schema","strict":True,"include_raw":True}
            return self
        async def ainvoke(self, messages):
            return {"parsed":{"score":80}, "raw":SimpleNamespace(
                usage_metadata={"input_tokens":12,"output_tokens":8,"total_tokens":20},
                response_metadata={"model_name":"actual-provider-model"}),"parsing_error":None}
    monkeypatch.setitem(sys.modules,"langchain_openai",SimpleNamespace(ChatOpenAI=Model))
    request = GenerationRequest(systemInstruction="STEM", prompt="Ohm", responseSchema={"type":"object"},task=task)
    result, model, usage = asyncio.run(OpenAIProvider().generate(request))
    assert result == {"score":80}
    assert model == "actual-provider-model"
    assert usage["total_tokens"] == 20

def test_missing_key_fails_before_sdk_initialization(monkeypatch):
    monkeypatch.delenv("OPENAI_API_KEY", raising=False)
    with pytest.raises(HTTPException) as ex:
        asyncio.run(OpenAIProvider().generate(GenerationRequest(systemInstruction="s",prompt="p",responseSchema={})))
    assert ex.value.status_code == 503

def test_refusal_is_not_fabricated_as_a_lesson(monkeypatch):
    monkeypatch.setenv("OPENAI_API_KEY", "test-only-key")
    class Refusal:
        def __init__(self, **kwargs): pass
        def with_structured_output(self, *args, **kwargs): return self
        async def ainvoke(self, messages): return {"parsed":None, "parsing_error":None, "raw":None}
    monkeypatch.setitem(sys.modules,"langchain_openai",SimpleNamespace(ChatOpenAI=Refusal))
    with pytest.raises(ValueError):
        asyncio.run(OpenAIProvider().generate(GenerationRequest(systemInstruction="s",prompt="p",responseSchema={})))


def test_strict_schema_requires_every_property_and_nulls_only_optional_ones():
    from app.schemas import drop_optional_nulls, strict_schema
    original = {"type": "object", "required": ["scenes"], "properties": {
        "fallbackMessage": {"anyOf": [{"type": "string"}, {"type": "null"}]},
        "scenes": {"type": "array", "items": {"type": "object", "required": ["title"],
            "properties": {"title": {"type": "string"}, "hint": {"type": "string"}}}}}}
    strict = strict_schema(original)
    assert strict["required"] == ["fallbackMessage", "scenes"] and strict["additionalProperties"] is False
    scene = strict["properties"]["scenes"]["items"]
    assert scene["required"] == ["title", "hint"] and scene["additionalProperties"] is False
    assert {"type": "null"} in scene["properties"]["hint"]["anyOf"]
    assert scene["properties"]["title"] == {"type": "string"}
    parsed = {"fallbackMessage": None, "scenes": [{"title": "Mở đầu", "hint": None}]}
    assert drop_optional_nulls(parsed, original) == {"scenes": [{"title": "Mở đầu"}]}

