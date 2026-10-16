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
    provider = os.getenv("AI_PROVIDER", "openai")
    key_name = {"openai": "OPENAI_API_KEY", "gemini": "GEMINI_API_KEY"}.get(provider)
    return bool(shared_key() and key_name and os.getenv(key_name))


def readiness():
    # launch sets this only after bounded, offline model loading has succeeded.
    ready = configured() and weights_present() and os.getenv("AI_WHISPER_VERIFIED") == "true"
    return {"status": "READY" if ready else "NOT_READY", "configured": configured(),
            "weightsPresent": weights_present(), "whisperVerified": os.getenv("AI_WHISPER_VERIFIED") == "true"}
