import subprocess
import pytest
from fastapi.testclient import TestClient
from app import launch
from app.main import app


@pytest.fixture(autouse=True)
def deployment_env(monkeypatch, tmp_path):
    for name in ("AI_SERVICE_SHARED_KEY", "AI_SERVICE_KEY", "OPENAI_API_KEY", "AI_WHISPER_VERIFIED"):
        monkeypatch.delenv(name, raising=False)
    monkeypatch.setenv("AI_PROVIDER", "openai")
    monkeypatch.setenv("WHISPER_MODEL_DIR", str(tmp_path))
    monkeypatch.setenv("WHISPER_STARTUP_MODE", "required")
    return tmp_path


def weights(directory):
    # Tiny invalid fixtures: no real model is loaded/downloaded in unit tests.
    for name in ("model.bin", "config.json", "tokenizer.json"):
        (directory / name).write_text("fixture")


def test_missing_weights_fail_fast_even_if_verified_flag_was_supplied(monkeypatch):
    monkeypatch.setenv("AI_WHISPER_VERIFIED", "true")
    with pytest.raises(RuntimeError, match="Whisper weights missing"):
        launch.preflight()
    with TestClient(app) as client:
        assert client.get("/ready").status_code == 503


def test_optional_smoke_liveness_is_not_readiness(monkeypatch):
    monkeypatch.setenv("WHISPER_STARTUP_MODE", "optional")
    launch.preflight()
    with TestClient(app) as client:
        assert client.get("/health").status_code == 200
        assert client.get("/ready").status_code == 503


def test_offline_model_load_is_bounded(monkeypatch, deployment_env):
    weights(deployment_env)
    def timed_out(args, **kwargs):
        assert kwargs["timeout"] == 90
        assert kwargs["stderr"] == subprocess.DEVNULL
        raise subprocess.TimeoutExpired(args, 90)
    monkeypatch.setattr(launch.subprocess, "run", timed_out)
    with pytest.raises(RuntimeError, match="timed out"):
        launch.preflight()


def test_corrupt_model_is_not_ready(monkeypatch, deployment_env):
    weights(deployment_env)
    def bad_model(args, **kwargs):
        raise subprocess.CalledProcessError(1, args)
    monkeypatch.setattr(launch.subprocess, "run", bad_model)
    with pytest.raises(RuntimeError, match="offline model loading failed"):
        launch.preflight()


def test_ready_requires_offline_load_and_both_keys(monkeypatch, deployment_env):
    weights(deployment_env)
    calls = []
    monkeypatch.setattr(launch.subprocess, "run", lambda *args, **kwargs: calls.append((args, kwargs)))
    monkeypatch.setenv("AI_SERVICE_SHARED_KEY", "test-shared")
    monkeypatch.setenv("OPENAI_API_KEY", "test-no-network")
    launch.preflight()
    assert len(calls) == 1
    assert launch.os.environ["WHISPER_LOCAL_FILES_ONLY"] == "true"
    assert launch.os.environ["HF_HUB_OFFLINE"] == "1"
    with TestClient(app) as client:
        assert client.get("/ready").status_code == 200
        monkeypatch.delenv("OPENAI_API_KEY")
        assert client.get("/ready").status_code == 503


def test_new_shared_key_precedes_legacy_alias(monkeypatch):
    monkeypatch.setenv("AI_SERVICE_SHARED_KEY", "new-test-key")
    monkeypatch.setenv("AI_SERVICE_KEY", "old-test-key")
    body = {"systemInstruction": "STEM", "prompt": "Atoms", "responseSchema": {"type": "OBJECT"}}
    with TestClient(app) as client:
        assert client.post("/internal/v1/generate", json=body,
                           headers={"X-AI-Api-Key": "old-test-key"}).status_code == 401
        # Correct key reaches provider config validation, without invoking an API.
        assert client.post("/internal/v1/generate", json=body,
                           headers={"X-AI-Api-Key": "new-test-key"}).status_code == 503
