"""Opt-in local topology/auth test; no server, weights download or paid requests."""
import argparse
import json
import os
from pathlib import Path
import secrets
import socket
import subprocess
import sys
import time
import urllib.request
import urllib.error

ROOT = Path(__file__).resolve().parents[2]
PROJECT = "stemotion-hetzner-smoke"


def command(args, **kwargs):
    return subprocess.check_output(args, text=True, **kwargs).strip()


def free_port():
    with socket.socket() as probe:
        probe.bind(("127.0.0.1", 0))
        return str(probe.getsockname()[1])


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--worker-image", default="stemotion-render:gcp-20261011", help="Explicit previously built image; NOT current-source build")
    parser.add_argument("--mock-worker", action="store_true", help="Use nginx topology stub, not a real worker")
    args = parser.parse_args()
    os.chdir(ROOT)
    if command(["docker", "ps", "-aq", "--filter", f"label=com.docker.compose.project={PROJECT}"]):
        raise RuntimeError("Existing smoke project; refusing to replace containers")
    out = ROOT / ".audit/hetzner-phase-a"
    out.mkdir(parents=True, exist_ok=True)
    empty = out / "empty-models"; empty.mkdir(exist_ok=True)
    worker = next((p for p in (ROOT.parent / "STEMotion_RENDER", ROOT.parents[2] / "STEMotion_RENDER") if (p / "Dockerfile").exists()), None)
    fe = next((p for p in (ROOT.parent / "STEMotion_FE", ROOT.parents[2] / "STEMotion_FE") if (p / "package.json").exists()), None)
    assert worker and fe, "Read-only sibling build contexts must exist"
    port = free_port()
    env = {**os.environ, "DEPLOY_MODE": "test", "CADDY_BIND_IP": "127.0.0.1", "HTTP_PORT": port,
        "HTTPS_PORT": free_port(), "AI_IMAGE": "stemotion-ai:local", "WORKER_IMAGE": "nginx:alpine" if args.mock_worker else args.worker_image,
        "WORKER_BUILD_CONTEXT": str(worker), "WORKER_FE_BUILD_CONTEXT": str(fe),
        "AI_SERVICE_SHARED_KEY": secrets.token_hex(32), "RENDER_SERVICE_KEY": secrets.token_hex(32),
        "REDIS_PASSWORD": secrets.token_hex(32), "OPENAI_API_KEY": "", "TTS_API_KEY": "", "USE_TTS": "false",
        "API_CALLBACK_URL": "http://127.0.0.1:9/not-called", "WORKER_PUBLIC_BASE_URL": f"http://127.0.0.1:{port}/worker/videos",
        "WHISPER_HOST_DIR": str(empty), "WHISPER_STARTUP_MODE": "optional"}
    dc = ["docker", "compose", "--env-file", "deploy/hetzner/.env.example", "-p", PROJECT, "-f", "docker-compose.hetzner.yml"]
    if args.mock_worker:
        override = out / "mock-worker.json"
        override.write_text(json.dumps({"services": {"worker": {"image": "nginx:alpine", "healthcheck": {
            "test": ["CMD", "wget", "-q", "-O", "/dev/null", "http://127.0.0.1:4000/health"]},
            "volumes": [f"{(ROOT/'deploy/hetzner/tests/nginx-worker.conf').as_posix()}:/etc/nginx/conf.d/default.conf:ro"]}}}), encoding="utf-8")
        dc += ["-f", str(override)]
    def run(*params): return command(dc + list(params), env=env)
    config = json.loads(run("config", "--format", "json"))
    subprocess.run([sys.executable, "deploy/hetzner/preflight.py"], input=json.dumps(config), text=True, check=True, env=env)
    created = False
    result = {"worker": "mock-nginx" if args.mock_worker else args.worker_image, "paidApiCalls": 0, "modelDownloaded": False}
    try:
        created = True
        subprocess.run(dc + ["up", "-d", "--no-build", "--wait", "--wait-timeout", "150"], env=env, check=True)
        if not args.mock_worker:
            captured = subprocess.check_output([sys.executable, "deploy/hetzner/snapshot_images.py", "deploy/hetzner/.env.example"],
                input=json.dumps(config), text=True, env=env)
            images = json.loads(captured)
            assert len(images["services"]) == 4
            for name, value in images["services"].items():
                container = run("ps", "-aq", name)
                assert value["image"] == command(["docker", "inspect", "--format", "{{.Image}}", container])
            result["runningImageSnapshot"] = "PASS (4 exact container image IDs)"
        def request(path, body=None, headers=None):
            req = urllib.request.Request(f"http://127.0.0.1:{port}"+path,
                data=None if body is None else json.dumps(body).encode(), headers={"Content-Type":"application/json", **(headers or {})})
            try:
                with urllib.request.urlopen(req, timeout=10) as response: return response.status, response.read().decode()
            except urllib.error.HTTPError as error: return error.code, error.read().decode()
        for path in ("/health", "/ai/health", "/worker/health"):
            status, body = request(path); assert status == 200, (path, status)
            result[path] = status
            if path == "/worker/health": assert json.loads(body)["queue"] == ("mock-nginx" if args.mock_worker else "bullmq")
        assert request("/ai/ready")[0] == 503  # no weights or provider key; never claim full readiness
        assert request("/worker/tts-preview?text=not-synthesized")[0] == 401
        assert request("/worker/render", {})[0] == 401
        assert request("/ai/internal/v1/generate", {})[0] == 401
        if not args.mock_worker:
            assert request("/worker/render", {}, {"X-Api-Key": env["RENDER_SERVICE_KEY"]})[0] == 400
            pong = run("exec", "-T", "worker", "node", "-e", "const Redis=require('/workspace/STEMotion_RENDER/node_modules/ioredis');const c=new Redis({host:'redis',port:6379});c.ping().then(p=>{console.log(p);c.disconnect()}).catch(()=>process.exit(1))")
            assert pong == "PONG", pong
        assert "NOAUTH" in run("exec", "-T", "redis", "redis-cli", "ping")
        assert run("exec", "-T", "redis", "sh", "-ec", 'REDISCLI_AUTH="$REDIS_PASSWORD" redis-cli ping') == "PONG"
        run("exec", "-T", "redis", "sh", "-ec", 'REDISCLI_AUTH="$REDIS_PASSWORD" redis-cli SET phase-a-smoke persist')
        run("restart", "redis")
        for _ in range(30):
            try:
                if run("exec", "-T", "redis", "sh", "-ec", 'REDISCLI_AUTH="$REDIS_PASSWORD" redis-cli GET phase-a-smoke') == "persist": break
            except subprocess.CalledProcessError: pass
            time.sleep(.5)
        else: raise RuntimeError("Redis persistence check failed")
        result.update({"redisAuth": "PASS", "redisRestartPersistence": "PASS", "workerRedisBootstrap": "NOT TESTED (mock)" if args.mock_worker else "PASS",
            "readiness": "503 as expected (empty weights/key)", "publishedServices": ["caddy"], "heavyRenderTested": False})
        (out / "smoke-results.json").write_text(json.dumps(result, indent=2), encoding="utf-8")
        print(json.dumps(result, indent=2))
    finally:
        if created:
            subprocess.run(dc + ["down", "--volumes", "--remove-orphans"], env=env, check=True)


if __name__ == "__main__": main()
