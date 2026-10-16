"""Bounded offline preflight, then exec uvicorn as PID 1 (no shell interpolation)."""
import logging
import os
import subprocess
import sys
from .deployment import model_directory, weights_present

log = logging.getLogger("ai.startup")


def preflight():
    os.environ.pop("AI_WHISPER_VERIFIED", None)
    mode = os.getenv("WHISPER_STARTUP_MODE", "required")
    if mode not in {"required", "optional"}:
        raise RuntimeError("WHISPER_STARTUP_MODE must be required or optional")
    # Deployment never downloads weights; provision before starting the container.
    os.environ["WHISPER_LOCAL_FILES_ONLY"] = "true"
    os.environ["HF_HUB_OFFLINE"] = "1"
    if not weights_present():
        message = "Whisper weights missing: WHISPER_MODEL_DIR must contain model.bin, config.json and tokenizer.json; provision bake/volume first"
        if mode == "required":
            raise RuntimeError(message)
        log.warning("%s; optional smoke mode: /ready stays 503", message)
        return
    os.environ["WHISPER_MODEL"] = str(model_directory().resolve())
    try:
        timeout = int(os.getenv("WHISPER_STARTUP_TIMEOUT_SEC", "90"))
        if not 1 <= timeout <= 600:
            raise ValueError()
    except ValueError:
        raise RuntimeError("WHISPER_STARTUP_TIMEOUT_SEC must be 1..600") from None
    try:
        # Child is killed on timeout. No ASR, inference or outbound network access.
        subprocess.run([sys.executable, "-c", "from app.alignment import load_model; load_model()"],
                       check=True, timeout=timeout, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    except subprocess.TimeoutExpired:
        raise RuntimeError("Whisper offline model loading timed out; check weights and memory") from None
    except subprocess.CalledProcessError:
        raise RuntimeError("Whisper offline model loading failed; check model format, CPU runtime and available RAM") from None
    os.environ["AI_WHISPER_VERIFIED"] = "true"
    log.info("Whisper offline preflight passed (no inference performed)")


def main():
    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(name)s: %(message)s")
    try:
        workers = int(os.getenv("UVICORN_WORKERS", "1"))
        port = int(os.getenv("PORT", "8000"))
        if workers < 1 or not 1 <= port <= 65535:
            raise RuntimeError("UVICORN_WORKERS must be positive; PORT must be 1..65535")
        preflight()
    except (RuntimeError, ValueError) as exc:
        log.error("Startup rejected: %s", exc)
        return 1
    args = [sys.executable, "-m", "uvicorn", "app.main:app", "--host", "0.0.0.0",
            "--port", str(port), "--workers", str(workers)]
    os.execvp(sys.executable, args)


if __name__ == "__main__":
    sys.exit(main())
