"""Deployment checks only: no inference, provider calls, or model downloads."""
import os
from pathlib import Path


def shared_key():
    # New deployment name takes precedence; keep the Phase 3 name for rollback.
    return os.getenv("AI_SERVICE_SHARED_KEY") or os.getenv("AI_SERVICE_KEY", "")


def model_directory():
    return Path(os.getenv("WHISPER_MODEL_DIR") or "/opt/whisper/small")


def weights_present():
    directory = model_directory()
    return all((directory / name).is_file() and (directory / name).stat().st_size > 0
               for name in ("model.bin", "config.json", "tokenizer.json"))


def configured():
    names = {os.getenv("AI_PROVIDER", "openai")}
    names.update(os.getenv(k, "").strip() for k in ("AI_STORYBOARD_PROVIDER", "AI_REVIEW_PROVIDER") if os.getenv(k, "").strip())
    return bool(shared_key().strip() and all(provider_configured(name) for name in names))


def provider_configured(provider):
    key_name = {"openai": "OPENAI_API_KEY", "gemini": "GEMINI_API_KEY", "anthropic": "ANTHROPIC_API_KEY"}.get(provider)
    models_ready = provider != "anthropic" or all(os.getenv(name, "").strip()
        for name in ("ANTHROPIC_MODEL_DRAFT", "ANTHROPIC_MODEL_LIGHT"))
    return bool(key_name and os.getenv(key_name, "").strip() and models_ready)


def readiness():
    # launch sets this only after bounded, offline model loading has succeeded.
    ready = configured() and weights_present() and os.getenv("AI_WHISPER_VERIFIED") == "true"
    return {"status": "READY" if ready else "NOT_READY", "configured": configured(),
            "weightsPresent": weights_present(), "whisperVerified": os.getenv("AI_WHISPER_VERIFIED") == "true"}
