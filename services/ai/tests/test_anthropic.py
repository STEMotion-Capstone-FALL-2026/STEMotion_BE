import asyncio
import json
import httpx
import pytest
from fastapi import HTTPException
from app.models import GenerationRequest
from app.providers import AnthropicProvider
from app.deployment import configured

SCHEMA = {"type": "object", "properties": {"score": {"type": "integer"}}, "required": ["score"]}

def request(task="generate"):
    return GenerationRequest(systemInstruction="STEM helper", prompt="Review Ohm", responseSchema=SCHEMA, task=task)

def stub(monkeypatch, body, status=200):
    calls = []
    class Client:
        def __init__(self, **kwargs):
            assert kwargs["follow_redirects"] is False
        async def __aenter__(self): return self
        async def __aexit__(self, *args): pass
        async def post(self, url, **kwargs):
            calls.append(kwargs)
            assert url == "https://api.anthropic.com/v1/messages"
            return httpx.Response(status, json=body)
    monkeypatch.setattr(httpx, "AsyncClient", Client)
    return calls

@pytest.fixture(autouse=True)
def config(monkeypatch):
    monkeypatch.setenv("ANTHROPIC_API_KEY", "test-only")
    monkeypatch.setenv("ANTHROPIC_MODEL_DRAFT", "opus-test")
    monkeypatch.setenv("ANTHROPIC_MODEL_LIGHT", "analysis-test")

def good():
    return {"model": "actual-opus", "stop_reason": "end_turn", "content": [{"type": "text", "text": '{"score":80}'}],
        "usage": {"input_tokens": 10, "output_tokens": 4}}

@pytest.mark.parametrize("task,model", [("generate", "opus-test"), ("storyboard", "opus-test"), ("grade", "analysis-test")])
def test_schema_routing_and_usage(monkeypatch, task, model):
    calls = stub(monkeypatch, good())
    result, actual, usage = asyncio.run(AnthropicProvider().generate(request(task)))
    assert result == {"score": 80} and actual == "actual-opus" and usage["total_tokens"] == 14
    assert calls[0]["headers"]["x-api-key"] == "test-only"
    assert calls[0]["json"]["model"] == model
    assert calls[0]["json"]["output_config"]["format"]["schema"]["additionalProperties"] is False

def test_missing_key_does_not_call_network(monkeypatch):
    calls = stub(monkeypatch, good())
    monkeypatch.delenv("ANTHROPIC_API_KEY")
    with pytest.raises(HTTPException) as exc: asyncio.run(AnthropicProvider().generate(request()))
    assert exc.value.status_code == 503 and calls == []

@pytest.mark.parametrize("reason", ["max_tokens", "refusal", "tool_use", None])
def test_incomplete_response_is_not_success(monkeypatch, reason):
    body = good(); body["stop_reason"] = reason
    stub(monkeypatch, body)
    with pytest.raises(ValueError): asyncio.run(AnthropicProvider().generate(request()))

@pytest.mark.parametrize("status,expected", [(401,503),(403,503),(429,429),(500,502)])
def test_errors_are_sanitized(monkeypatch, status, expected):
    stub(monkeypatch, {"error": "provider secret should not be echoed"}, status)
    with pytest.raises(HTTPException) as exc: asyncio.run(AnthropicProvider().generate(request()))
    assert exc.value.status_code == expected and "secret" not in exc.value.detail

def test_valid_json_with_wrong_shape_is_rejected(monkeypatch):
    body = good(); body["content"][0]["text"] = json.dumps({"score": "not-a-number"})
    stub(monkeypatch, body)
    with pytest.raises(Exception): asyncio.run(AnthropicProvider().generate(request()))

def test_anthropic_readiness_needs_models_not_just_key(monkeypatch):
    monkeypatch.setenv("AI_PROVIDER", "anthropic")
    monkeypatch.setenv("AI_SERVICE_SHARED_KEY", "test-shared")
    assert configured()
    monkeypatch.setenv("ANTHROPIC_MODEL_LIGHT", "")
    assert not configured()

def test_task_routing_and_readiness_respect_all_selected_providers(monkeypatch):
    from app import providers
    monkeypatch.setenv("AI_PROVIDER", "openai")
    monkeypatch.setenv("AI_STORYBOARD_PROVIDER", "anthropic")
    monkeypatch.setenv("AI_SERVICE_SHARED_KEY", "test-shared")
    monkeypatch.setenv("OPENAI_API_KEY", "test-openai")
    calls = []
    class Claude:
        async def generate(self, req): calls.append(req.task); return {}, "opus", {}
    monkeypatch.setattr(providers, "AnthropicProvider", Claude)
    asyncio.run(providers.TaskRoutedProvider().generate(request("storyboard")))
    assert calls == ["storyboard"] and configured()
    monkeypatch.setenv("ANTHROPIC_API_KEY", "")
    assert not configured()
