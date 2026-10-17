# AI service deployment runbook (prepared, NOT applied)

Updated 2026-10-16. Scope: local Docker build/smoke plus a proposed Render service. No push, Render changes, provider calls, model downloads or production migration are authorized by this file. Existing root render.yaml is unchanged; **dashboard instructions** below are the chosen deployment specification.

## Architecture and resource decision

```text
FE/worker -> BE (JWT/RBAC, M01 audit and cost aggregation)
               -> ai-service (X-AI-Api-Key shared secret)
                    -> OpenAI via LangChain (draft/light model routing)
                    -> local faster-whisper / CTranslate2 (known-text alignment)
```

AI needs no Postgres connection or depends_on Postgres: BE alone persists usage. No FE/RENDER changes. Public health endpoints expose flags only, not keys/prompts or paths.

**Render Free (512 MB) is NOT sufficient for Whisper small.** Allow roughly 1–2 GB RAM for CPU/int8 alignment plus Python/API overhead; this is a sizing estimate, not a measurement of our workload. Upstream CPU small benchmarks also show memory above a 512 MB budget. Minimum proposed trial plan: **1c-2g (legacy Standard), 1 CPU / 2 GB, $25/month**. Use one uvicorn worker and one OpenMP thread; each additional worker can allocate another model. 2 GB is a starting point requiring real-audio peak-RSS and latency testing, not a production capacity guarantee. Increase to 2c-4g (legacy Pro, about $85/month) if peak memory/concurrency needs it.

Proposed service: `stemotion-ai-service`, Docker Web Service, Singapore (same as the proposed BE region; verify actual BE region before creating), port supplied by Render, readiness path `/ready`, auto-deploy OFF, one instance. Optional persistent disk mounted at `/models`: 2 GB at $0.25/GB-month is ~$0.50/month, making the minimum trial ~$25.50/month with disk, or ~$25/month with baked weights. Excludes OpenAI usage, egress, workspace fees, taxes and other services. Confirm current prices in the dashboard before a separately approved deployment.

Sources checked 2026-10-16: [Render compute plans](https://render.com/docs/compute-plans), [Render pricing](https://render.com/pricing), [faster-whisper CPU benchmarks](https://github.com/SYSTRAN/faster-whisper#small-model-on-cpu), [Render Docker](https://render.com/docs/docker), [persistent disks](https://render.com/docs/disks), [regions](https://render.com/docs/regions).

## Environment contract

Required means required for readiness/full operation, not for the intentionally incomplete smoke mode. Set secrets in Render's secret environment fields, never in Docker build arguments, Git, commands or logs. Examples below contain no usable credentials.

| Name | Owner / required? | Default | Example / notes |
|---|---|---|---|
| AI_SERVICE_SHARED_KEY | BE + AI, yes | empty | Generate privately; same random secret on both services. HTTP header remains X-AI-Api-Key. |
| AI_SERVICE_KEY | BE + AI, legacy only | empty | Compatibility alias; use the new name. Do not configure conflicting aliases. |
| OPENAI_API_KEY | AI, yes with openai | empty | Owner-provided secret; never created or used by this task. |
| AI_PROVIDER | AI, optional | openai | openai; gemini only for explicit rollback. |
| OPENAI_MODEL_DRAFT | AI, optional | gpt-4.1 | Draft generation model. |
| OPENAI_MODEL_LIGHT | AI, optional | gpt-4o-mini | resegment/grade/extract/terms. |
| GEMINI_API_KEY | AI, only Gemini rollback | empty | Private provider key. BE may separately need it only in embedded rollback. |
| GEMINI_MODEL | AI, Gemini rollback | gemini-3.5-flash-lite | Explicit fallback model; no silent failover. |
| WHISPER_MODEL | AI, optional | small | Compose selects /models/small; launcher uses the verified absolute model directory internally. |
| WHISPER_MODEL_DIR | AI, yes | image: /opt/whisper/small | Volume: /models/small. Actual CTranslate2 model folder, not merely a hub cache root. |
| WHISPER_DEVICE | AI, optional | cpu | CPU image; CUDA is not provided. |
| WHISPER_COMPUTE_TYPE | AI, optional | int8 | Keep int8 for the proposed RAM budget. |
| WHISPER_LOCAL_FILES_ONLY | AI | true | Launcher forces true; no startup download. |
| HF_HUB_OFFLINE | AI | 1 | Launcher forces 1. Provision weights outside runtime. |
| WHISPER_STARTUP_MODE | AI, optional | required | required exits 1 on absent weights; optional is diagnostic smoke only. |
| WHISPER_STARTUP_TIMEOUT_SEC | AI, optional | 90 | 1..600 seconds for offline model-load preflight, killed on timeout. |
| WHISPER_MIN_WORD_PROBABILITY | AI, optional | 0.05 | Existing alignment confidence threshold. |
| UVICORN_WORKERS | AI, optional | 1 | Positive integer. Keep 1 until measured; models multiply with workers. |
| OMP_NUM_THREADS | AI image, optional | 1 | CPU threading bound. |
| PORT | AI image, supplied by Render | 8000 | Bind 0.0.0.0; Compose fixes container port 8000. |
| AI_SERVICE_MODE | BE, yes for remote | remote | embedded is explicit Java/Gemini rollback. |
| AI_SERVICE_URL | BE, yes | http://localhost:8000 | Compose: http://ai-service:8000; Render: assigned HTTPS service origin. |
| AI_PRICING_JSON | **BE**, needed for cost estimates | {} | Exact response model -> inputPerMillion/outputPerMillion/version. See AI_USAGE_CONTRACT.md. Not read/enforced by Python. |
| AI_USAGE_ZONE | BE, optional | Asia/Ho_Chi_Minh | Usage aggregation day boundaries. |
| AI_MONTHLY_BUDGET_USD | **Reserved operator target only** | unset | Example 20. BE admin overview reads this target; it does not enforce it, block requests or send alerts. Setting it does not create a spend cap. Use provider-side controls and manual monitoring. |
| AI_ALIGNMENT_WORKER_KEY | BE, only worker proxy | empty | Separate worker-to-BE auth; not the AI service shared key. |
| AI_SERVICE_ENV_FILE | Compose, optional | ./services/ai/.env | Service-only env file; example file has empty secrets. |
| AI_LOCAL_PORT | Compose, optional | 8000 | Binds loopback only; choose another port for an isolated smoke project. |

PYTHONUNBUFFERED/PYTHONDONTWRITEBYTECODE/PIP_NO_CACHE_DIR are image defaults (1). AI_WHISPER_VERIFIED is an internal launcher result, not an operator setting; launcher clears any supplied value. Readiness validates configuration/offline model loading but never calls OpenAI or runs inference, so it cannot certify API quota/key validity or alignment quality.

## Image and weights

Base python:3.12-slim; pip installs exact requirements.lock with pip check, no package upgrades. CPU libgomp1/libstdc++6/CA certificates are installed. PyAV wheel supplies FFmpeg libraries; no CUDA stack. Runtime UID/GID 10001, no root privileges. CMD uses a Python launcher and execs uvicorn; SIGTERM reaches uvicorn. Each startup verifies nonempty model.bin/config.json/tokenizer.json then performs bounded offline model loading in a child process, not inference. Missing/corrupt models or timeout fail with a clear sanitized startup error. The preflight model is freed before serving; the existing alignment loader caches its own model on first use.

Three provisioning options (none performed in this task):

1. **Bake (recommended for first Render release / reproducible rollback).** Separately obtain a pinned, verified CTranslate2 small model on a trusted build machine; place its complete folder under services/ai/models/small/. Then run `docker build --build-arg BAKE_WHISPER_MODEL=true -t <approved-image-tag> services/ai`. The selected Docker stage COPYs existing files; it never downloads weights. Defaults select the empty stage (BAKE_WHISPER_MODEL=false). Model files are Git-ignored; never commit them. Baked weights increase image size by hundreds of MB, transfer time and registry storage, but permit immutable image+model rollback. Publish that image only after separate explicit approval. Runtime directory: /opt/whisper/small. Do not mount an empty volume over it.
2. **Volume (recommended for local development).** Provision the complete folder at ./models/small on the host; Compose mounts ./models:/models read-only. Render requires a paid persistent disk and WHISPER_MODEL_DIR=/models/small, readable by UID 10001. Disk content is not part of a code rollback; preserve versioned model folders/backups. Disks also limit service scaling/deployment behavior; read Render disk constraints. Prefer an initial baked image, then transfer verified weights to the mounted disk via an approved maintenance shell/job before switching the directory. An empty disk with required mode intentionally fails startup. Do not bypass readiness for a public release.
3. **Download out of band.** An explicitly approved provisioning job can use huggingface_hub.snapshot_download for Systran/faster-whisper-small, pinned to a reviewed revision, into the final folder; verify checksums/model license and read permissions, then start required mode offline. No automatic startup/build download path is added. This avoids bloating the app image but adds provisioning/network work and needs disk persistence. Never download per request/restart; never use an unpinned mutable revision for a release.

## Local build and smoke

From the BE checkout, copy services/ai/.env.example to services/ai/.env privately when ready for real operation. Use that file as Compose interpolation input so the same shared key reaches both BE and AI. Set other BE DB/JWT/worker settings separately in a private root .env; use only an explicitly local DB for full-stack tests (the existing Compose file's DB default points outside Docker). AI-only smoke starts no DB, BE, render worker or MinIO.

```powershell
# Build has no credentials and downloads no model.
docker build -t stemotion-ai:local -f services/ai/Dockerfile services/ai
# For a fully configured local stack (after provisioning weights), read both files:
docker compose --env-file .env --env-file services/ai/.env up -d backend
```

Strict smoke with no weights: supply temporary non-secret placeholders for required unrelated Compose interpolation fields (JWT_SECRET, RENDER_SERVICE_KEY), set AI_SERVICE_ENV_FILE=./services/ai/.env.example, then:

```powershell
docker compose --env-file services/ai/.env.example -p stemotion-ai-smoke up -d --no-deps ai-service
docker compose --env-file services/ai/.env.example -p stemotion-ai-smoke logs ai-service
```

Expected: container exits 1 quickly with `Whisper weights missing`, rather than hanging. This is a successful negative check, not a healthy service.

For the separate liveness smoke, set WHISPER_STARTUP_MODE=optional in the shell and run that same up command again (Compose recreates on env change). `/health` returns 200 without weights/keys; `/ready` returns 503 with false flags. Docker/Compose health remains unhealthy, preventing backend's service_healthy dependency from starting. Once actual weights and keys are available, restore required mode and verify `/ready` 200. Do not call script/align endpoints in this smoke. Remove only this task's ai-service container afterward with `docker compose ... -p stemotion-ai-smoke rm -s -f ai-service`; do not run global down/prune commands.

## Render dashboard procedure — future, requires separate approval

1. Record reviewed BE/AI commits, image digest and model revision/checksums. Keep auto-deploy OFF. First publish a tested baked image through an approved registry workflow; this task does not publish one. Alternative repository Docker builds use Dockerfile services/ai/Dockerfile and build context services/ai, but an ordinary Git build contains no model binaries and therefore needs a separately provisioned disk before it can pass readiness.
2. Dashboard -> New -> Web Service -> Existing Image. Name stemotion-ai-service, select approved image by immutable digest (private registry credentials are secrets), Singapore matching BE, plan 1c-2g / legacy Standard, one instance. Use default image CMD; no shell command overrides. Choose a distinct nonproduction service for acceptance first. See [deploy an image](https://render.com/docs/deploy-an-image).
3. Add AI-only env values from the table. Set shared key + OpenAI key privately, CPU/int8, required mode, one worker, verified model directory. Render supplies PORT. For the recommended baked image, no persistent disk is required. If using an already provisioned disk, mount /models and select its verified versioned folder.
4. Set health check to /ready. Save/create only after separate authorization; dashboard creation can immediately deploy and bill. Wait for clear offline-preflight logs and ready status. If weights are absent/corrupt, fix provisioning rather than relaxing production readiness. Check /health=200 and /ready=200 without printing secrets.
5. In a separately approved nonproduction BE configuration, set AI_SERVICE_MODE=remote, AI_SERVICE_URL to this service's HTTPS origin, and the identical AI_SERVICE_SHARED_KEY. BE sends X-AI-Api-Key already; no new header or business logic is introduced. Configure AI_PRICING_JSON on BE using exact returned model IDs. V15 must be applied through its separate manual/backup approval before this BE code uses that database. Do not auto-enable Flyway.
6. Paid API and real-audio tests require a separate acceptance budget/approval. Verify valid/invalid service auth, script/actions, known-text word timing, latency/peak memory, BE M01 audit and admin usage. Health alone does not establish these. Check invalid auth never exposes credentials. Keep FE/worker dashboard/integration changes out of this deployment prep.
7. Rollback: keep a known-good previous image digest/deploy SHA and matching model revision. In dashboard select that previous successful deploy/image digest and redeploy; verify /ready and BE connectivity. Roll back BE to its corresponding SHA if needed. Database and mounted disk do not roll back with code: do not automatically run V15 down migration or overwrite model folders. First deployment has no known-good service revision: stop the new service and restore the previous BE configuration rather than claiming rollback to an untested SHA.

## Before release

- [ ] Owner-provided keys entered privately; same shared key on BE and AI; no secrets in image/layers/Git/logs.
- [ ] Verified compatible weights exist at the configured directory, readable by UID 10001; required mode; offline preflight and /ready pass.
- [ ] Paid RAM plan selected; real Vietnamese scene peak memory/latency and concurrency measured with one worker.
- [ ] Exact model/version pricing configured in BE; unknown-cost coverage reviewed; monthly budget is recognized as NOT enforced by this code.
- [ ] BE points to the right HTTPS origin, auth works, and separately approved schema/manual migration requirements are met.
- [ ] Auto-deploy disabled; previous known-good SHA/image/model recorded; no secret logging in observability.

Local verification results are recorded after execution; no real model inference or Render deployment is claimed.

Compatibility decision approved by Le Tran: keep requirements.lock unchanged and use Python 3.12. The attempted Python 3.11 build failed because av==19.0.1 and numpy==2.5.4 require Python >=3.12. No pins were upgraded or regenerated.

Automated smoke (PowerShell 7, after image build): pwsh -File services/ai/scripts/smoke-compose.ps1. It uses only the example env file and temporary placeholders, chooses a free loopback port, checks strict failure plus optional liveness/readiness and native imports/non-root UID, then removes only its own container/network. It refuses an existing smoke project and requires ./models to have no provisioned model.

## Verification executed locally (2026-10-16)

- Python 3.12 Docker build: PASS; exact lock unchanged, pip check reports no broken requirements. Python 3.11 was rejected by av/numpy metadata as described above.
- BAKE_WHISPER_MODEL=true Docker stage: PASS with the existing EMPTY fixture folder only. This verifies conditional COPY/build wiring, not real model compatibility or successful inference.
- PowerShell Compose smoke: PASS. Required missing-weights startup exited 1 with the expected clear message. Optional smoke returned /health 200 and /ready 503, UID 10001; av/ctranslate2/faster_whisper imported on Linux. No model/keys were supplied. Only ai-service was started; its isolated container/network were removed afterward.
- Python suite: 44 passed in the existing Windows environment and 44 passed in the built Linux image with --network none (same suite, not 88 distinct tests).
- Java: 7 targeted tests passed, including both actual application YAML profiles' shared-key resolution/legacy fallback and existing gateway/alignment client auth tests.
- Compose interpolation verified BE AI_SERVICE_URL=http://ai-service:8000 and AI_PRICING_JSON={}. BE itself was not started against the preexisting external DB default.
- Real weights/model inference, real OpenAI key/quota, real Vietnamese alignment quality and paid Render peak-memory acceptance remain untested. No production schema, staging/main branch, FE or RENDER files were changed. No push/deploy was performed.

The host Docker engine initially failed on stale inference/secrets-engine sockets. Socket-only runtime locations were preserved by renaming (no factory reset, image/volume deletion or secret-content changes); engine recovered. Existing four containers remained present; cleanup removed only this task's smoke resources.


Current model/pricing and Render env audit (2026-10-17): see [AI_RENDER_ENV_2026-10-17.md](AI_RENDER_ENV_2026-10-17.md). Historical acceptance runs retain their original model/pricing fixtures.
