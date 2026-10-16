"""Opt-in loopback service + restored-directory fail-fast acceptance."""
import argparse
import json
import os
from pathlib import Path
import socket
import subprocess
import sys
import time
import httpx
from acceptance_alignment import ROOT, OUT, service, save


def main(compat_av_dir=None):
    model = (ROOT / "models/faster-whisper-small").resolve()
    hidden = model.with_name(model.name + ".missing-acceptance")
    allowed = (ROOT / "models").resolve()
    assert model.is_relative_to(allowed) and hidden.is_relative_to(allowed)
    assert model.is_dir() and not hidden.exists()
    values = dict(line.split("=", 1) for line in (ROOT / "services/ai/.env").read_text(encoding="utf-8").splitlines()
                  if "=" in line and not line.lstrip().startswith("#"))
    env = {**os.environ, **values, "WHISPER_MODEL_DIR": str(model), "WHISPER_LOCAL_FILES_ONLY": "true",
           "HF_HUB_OFFLINE": "1", "WHISPER_STARTUP_MODE": "required", "OMP_NUM_THREADS": "2",
           "OPENAI_API_KEY": "acceptance-placeholder-not-a-real-key", "GEMINI_API_KEY": "",
           "PYTHONIOENCODING": "utf-8"}
    if compat_av_dir:
        env["PYTHONPATH"] = str(compat_av_dir.resolve())
    cwd = ROOT / "services/ai"
    # Exercise the actual configured path missing, not merely a made-up path.
    model.rename(hidden)
    try:
        before = time.monotonic()
        rejected = subprocess.run([sys.executable, "-m", "app.launch"], cwd=cwd, env=env,
                                  capture_output=True, text=True, timeout=15)
        assert rejected.returncode == 1 and "Whisper weights missing" in rejected.stderr
        save("missing-weights.json", {"exitCode": rejected.returncode, "elapsedSec": time.monotonic()-before,
                                      "log": rejected.stderr.strip(), "directoryRestored": True})
    finally:
        hidden.rename(model)
    with socket.socket() as probe:
        probe.bind(("127.0.0.1", 0)); port = probe.getsockname()[1]
    url = f"http://127.0.0.1:{port}"
    # Keep uvicorn in the same owned process for reliable Windows cleanup.
    command = ("from app.launch import preflight; preflight(); import uvicorn; "
               f"uvicorn.run('app.main:app',host='127.0.0.1',port={port},workers=1)")
    with (OUT / "service-local.log").open("w", encoding="utf-8") as log:
        process = subprocess.Popen([sys.executable, "-c", command], cwd=cwd, env=env, stdout=log, stderr=log)
        try:
            for _ in range(120):
                if process.poll() is not None:
                    raise RuntimeError("Service exited; inspect local service-local.log")
                try:
                    ready = httpx.get(url+"/ready", timeout=1)
                    if ready.status_code == 200:
                        break
                except httpx.TransportError:
                    pass
                time.sleep(.5)
            else:
                raise RuntimeError("Service readiness timed out")
            assert httpx.get(url+"/health").status_code == 200
            save("startup-ready.json", {"health": 200, "ready": ready.json(), "localFilesOnly": True,
                "openaiCredential": "test placeholder only; no OpenAI request", "compatAvProbe": bool(compat_av_dir)})
            # No credentials in CLI arguments, persisted outputs or logs.
            service(url, values["AI_SERVICE_SHARED_KEY"])
        finally:
            process.terminate()
            try: process.wait(timeout=10)
            except subprocess.TimeoutExpired: process.kill(); process.wait(timeout=5)
    print("Owned local service stopped; weights directory restored")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--compat-av-dir", type=Path)
    args = parser.parse_args()
    main(args.compat_av_dir)
