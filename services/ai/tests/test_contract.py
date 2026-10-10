from fastapi.testclient import TestClient
from app.main import app, json_schema, provider


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
            return {"scenes": [{"type": "TITLE_HERO"}]}, "test-gemini"
    app.dependency_overrides[provider] = Stub
    try:
        with TestClient(app) as client:
            response = client.post("/internal/v1/generate", json=BODY, headers={"X-AI-Api-Key": "test-shared-key"})
            assert response.status_code == 200
            assert response.json()["model"] == "test-gemini"
            assert response.json()["result"]["scenes"][0]["type"] == "TITLE_HERO"
            assert response.json()["elapsedMs"] >= 0
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
