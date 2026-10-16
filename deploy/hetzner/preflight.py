"""Validate rendered Compose JSON on stdin without printing credentials."""
import json
import os
from pathlib import Path
import re
import sys


def check(config):
    services = config["services"]
    ai, worker, redis, caddy = (services[name] for name in ("ai-service", "worker", "redis", "caddy"))
    ce = caddy["environment"]
    mode = ce["DEPLOY_MODE"]
    assert mode in {"test", "prod"}, "DEPLOY_MODE must be test or prod"
    for service, name in ((ai, "AI_SERVICE_SHARED_KEY"), (worker, "RENDER_SERVICE_KEY"), (redis, "REDIS_PASSWORD")):
        assert re.fullmatch(r"[A-Za-z0-9_-]{32,}", str(service["environment"].get(name, ""))), f"Set a random 32+ character hex/base64url {name}"
    assert worker["environment"]["REDIS_PASSWORD"] == redis["environment"]["REDIS_PASSWORD"]
    assert worker["environment"]["RENDER_CONCURRENCY"] == "1"
    assert worker["environment"]["RENDER_FRAME_CONCURRENCY"] == "1"
    assert len(config["networks"]) == 1, "Expected one bridge network"
    for name in ("ai-service", "worker", "redis"):
        assert not services[name].get("ports"), f"{name} must not publish host ports"
    for name in (() if os.getenv("HETZNER_WORKER_IMAGE_ONLY") == "1" else ("worker",)):
        build = services[name]["build"]
        assert (Path(build["context"]) / "Dockerfile").is_file(), "Worker Dockerfile/context missing; copy the frozen sibling checkout"
        context = build["additional_contexts"]["fe"]
        assert (Path(context) / "package.json").is_file(), "FE build context missing"
    assert worker["environment"]["API_CALLBACK_URL"].startswith(("http://", "https://")), "Set callback URL"
    if mode == "prod":
        assert ai["environment"].get("OPENAI_API_KEY"), "Production requires owner-provided OpenAI key"
        assert ai["environment"]["WHISPER_STARTUP_MODE"] == "required", "Production requires fail-fast weights mode"
        weight_mount = next(v for v in ai["volumes"] if v["target"] == "/models")
        folder = Path(weight_mount["source"]) / "faster-whisper-small"
        assert all((folder / n).is_file() for n in ("model.bin", "config.json", "tokenizer.json")), "Transfer verified weights first"
        for name in ("AI_DOMAIN", "WORKER_DOMAIN"):
            host = ce[name]
            assert re.fullmatch(r"[a-z0-9][a-z0-9.-]+[a-z0-9]", host) and "." in host and not host.endswith((".invalid", ".example")), f"Set real {name}"
        assert ce["AI_DOMAIN"] != ce["WORKER_DOMAIN"], "AI and worker domains must differ"
        assert "@" in ce["ACME_EMAIL"] and not ce["ACME_EMAIL"].endswith(".invalid"), "Set real ACME_EMAIL"
        assert {p["published"] for p in caddy["ports"]} == {"80", "443"}, "Production needs host ports 80/443"
        assert worker["environment"]["API_CALLBACK_URL"].startswith("https://"), "Production BE callback needs HTTPS"
        assert worker["environment"]["PUBLIC_BASE_URL"] == f"https://{ce['WORKER_DOMAIN']}/videos", "Set production WORKER_PUBLIC_BASE_URL"
    assert worker["environment"].get("USE_TTS") != "true" or worker["environment"].get("TTS_API_KEY"), "TTS enabled without provider key"


if __name__ == "__main__":
    try:
        check(json.load(sys.stdin))
    except (AssertionError, KeyError, ValueError) as error:
        print(f"Deployment preflight rejected: {error}", file=sys.stderr)
        sys.exit(1)
    print("Deployment preflight passed; no credentials printed. This does not provision or contact a server.")
