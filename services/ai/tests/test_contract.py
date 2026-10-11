from fastapi.testclient import TestClient
from app.main import app, json_schema, provider, GeminiProvider, GenerationRequest


def test_langchain_provider_preserves_raw_usage(monkeypatch):
    import asyncio
    import sys
    from types import SimpleNamespace
    monkeypatch.setenv("GEMINI_API_KEY", "test-only-key")
    class Model:
        def __init__(self, **kwargs):
            assert kwargs["max_retries"] == 2
        def with_structured_output(self, schema, **kwargs):
            assert kwargs["include_raw"] is True
            return self
        async def ainvoke(self, messages):
            return {"parsed": {"score":80}, "raw":SimpleNamespace(usage_metadata={"input_tokens":12,"output_tokens":8,"total_tokens":20}), "parsing_error":None}
    monkeypatch.setitem(sys.modules,"langchain_google_genai",SimpleNamespace(ChatGoogleGenerativeAI=Model))
    monkeypatch.setitem(sys.modules,"langchain_core.messages",SimpleNamespace(HumanMessage=lambda **kw:kw,SystemMessage=lambda **kw:kw))
    result, model, usage = asyncio.run(GeminiProvider().generate(GenerationRequest(**BODY)))
    assert result == {"score":80}
    assert usage["total_tokens"] == 20


def test_unknown_usage_is_null_not_zero(monkeypatch):
    monkeypatch.setenv("AI_SERVICE_KEY","test-shared-key")
    class Stub:
        async def generate(self, request):
            return {}, "stub", {}
    app.dependency_overrides[provider]=Stub
    try:
        with TestClient(app) as client:
            response=client.post("/internal/v1/generate",json=BODY,headers={"X-AI-Api-Key":"test-shared-key"})
            assert response.status_code == 200
            assert response.json()["inputTokens"] is None
            assert response.json()["outputTokens"] is None
            assert response.json()["totalTokens"] is None
    finally:
        app.dependency_overrides.clear()


BODY = {"systemInstruction": "Vietnamese STEM", "prompt": "Atoms", "responseSchema": {"type": "OBJECT"}}


def test_auth_fails_closed(monkeypatch):
    monkeypatch.delenv("AI_SERVICE_KEY", raising=False)
    with TestClient(app) as client:
        assert client.post("/internal/v1/generate", json=BODY).status_code == 503
        monkeypatch.setenv("AI_SERVICE_KEY", "test-shared-key")
        assert client.post("/internal/v1/generate", json=BODY).status_code == 401


def test_be_contract_and_provider_metadata(monkeypatch):
    monkeypatch.setenv("AI_SERVICE_KEY", "test-shared-key")
    class Stub:
        async def generate(self, request):
            assert request.prompt == "Atoms"
            return {"scenes": [{"type": "TITLE_HERO"}]}, "test-gemini", {"input_tokens":12,"output_tokens":8,"total_tokens":20}
    app.dependency_overrides[provider] = Stub
    try:
        with TestClient(app) as client:
            response = client.post("/internal/v1/generate", json=BODY, headers={"X-AI-Api-Key": "test-shared-key"})
            assert response.status_code == 200
            assert response.json()["model"] == "test-gemini"
            assert response.json()["result"]["scenes"][0]["type"] == "TITLE_HERO"
            assert response.json()["elapsedMs"] >= 0
            assert response.json()["inputTokens"] == 12
            assert response.json()["outputTokens"] == 8
            assert response.json()["totalTokens"] == 20
    finally:
        app.dependency_overrides.clear()


def test_provider_failure_does_not_leak_secrets(monkeypatch):
    monkeypatch.setenv("AI_SERVICE_KEY", "test-shared-key")
    class Broken:
        async def generate(self, request):
            raise RuntimeError("secret-key-in-url")
    app.dependency_overrides[provider] = Broken
    try:
        with TestClient(app) as client:
            response = client.post("/internal/v1/generate", json=BODY, headers={"X-AI-Api-Key": "test-shared-key"})
            assert response.status_code == 502
            assert "secret" not in response.text
    finally:
        app.dependency_overrides.clear()


def test_schema_keeps_nullability():
    schema = json_schema({"type": "OBJECT", "properties": {"message": {"type": "STRING", "nullable": True}}})
    assert schema == {"type": "object", "properties": {"message": {"anyOf": [{"type": "string"}, {"type": "null"}]}}}
