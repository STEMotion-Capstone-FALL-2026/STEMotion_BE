# Local Whisper / pricing acceptance — 2026-10-16

Status: quantitative timing acceptance passed with an explicit local confidence setting; listening sign-off remains unverified. No OpenAI request, push, deploy or production migration. Work performed on feat/ai-microservice.

## Weights and offline startup

- Public CTranslate2 repository: Systran/faster-whisper-small, pinned revision `536b0662742c02347bc0e980a01041f333bce120`.
- Downloaded into `models/faster-whisper-small/`; model.bin 483,546,902 bytes plus config.json/tokenizer.json/vocabulary.txt. Huggingface_hub 2.2.0 was already installed, so no upgrade was needed. Used snapshot_download, equivalent to the requested CLI download but with an immutable revision. [Manifest and SHA256s](../services/ai/acceptance/vi-water-displacement/model-provenance.json).
- Both local .env files and all model/cache files are Git-ignored. Only the small authored audio/transcript/result fixtures are committed. Model binaries are not in the image or Git.
- Private services/ai/.env sets WHISPER_MODEL_DIR to the absolute local model folder; local-files-only, cpu/int8, required startup mode. Root .env selects WHISPER_MODEL=faster-whisper-small for Compose's volume subfolder. See updated AI_ALIGNMENT_CONTRACT.md.
- Real loopback service startup passed offline; /health and /ready returned 200 using a deliberately fake provider-key placeholder for readiness configuration checks. No provider request was made. The persisted local .env keeps OPENAI_API_KEY empty; without a key, real /ready correctly returns 503 even when weights are valid.
- Temporarily renamed the actual weights directory, ran the deployment launcher, saw exit 1 with explicit missing-weights error in ~0.11 s, then restored the directory in finally. The test-owned server was stopped afterward. [Evidence](../services/ai/acceptance/vi-water-displacement/missing-weights.json).

## Real audio, measured timing, and limitations

Fixture: vi-VN-HoaiMyNeural via edge-tts 7.3.1; **22.68 s, 136,080 bytes**, within the <=30s / <=10MiB contract. [Audio](../services/ai/acceptance/vi-water-displacement/vi-HoaiMy.mp3), [known narration](../services/ai/acceptance/vi-water-displacement/transcript.txt), [offline listening/timestamp review](../services/ai/acceptance/vi-water-displacement/review.html). Audio SHA256: `0a5642705219ca0439770394d5df8d9e8db7c3897ffa0fd0c4578e0b75b201d7`.

Reference timings are Edge's 78 WordBoundary events, not manually annotated speech boundaries. Comparison uses Unicode-normalized Vietnamese whitespace units, ignoring punctuation/case but preserving accents. Sequence matching reports coverage; it does not silently discard mismatches. Multiword tokens never receive invented internal timestamps.

| Comparison | Text coverage | Boundary samples | Mean / p95 / maximum absolute error |
|---|---:|---:|---|
| Direct faster-whisper ASR vs Edge | 74/78 | 148 | 0.0328 / 0.075 / **0.125 s** |
| Forced-alignment endpoint vs Edge | **78/78** | **156** | 0.0336 / 0.075 / **0.125 s** |
| Endpoint vs direct ASR on matching text | 74/78 | 148 | 0.00527 / 0.020 / **0.060 s** |

Direct baseline used WhisperModel(local_path, device=cpu, compute_type=int8), real language detection, then transcribe(language=vi, word_timestamps=True). Detected **vi, probability 0.999578**. ASR made four lexical/accent mistakes: thi instead of thí, and độc vật chi instead of đọc vạch chia. Therefore ASR output and known-transcript alignment are **not text-identical**. The endpoint retains the correct supplied narration; all its measured boundaries are below 0.3 s. Do not claim literal ASR equality or full ASR transcription accuracy.

Default confidence 0.05 rejected the known word thể (likelihood 0.03779) with HTTP 422. Raw timing for that word matched Edge closely. Local services/ai/.env explicitly sets WHISPER_MIN_WORD_PROBABILITY=0.03; the positive acceptance uses that value. The application default and .env.example remain 0.05. This is a single-fixture calibration, not a recommendation to lower production confidence generally. No proportional timing, ASR substitution, or fabricated timestamps were added.

[Endpoint metrics](../services/ai/acceptance/vi-water-displacement/service-metrics.json), [ASR metrics](../services/ai/acceptance/vi-water-displacement/direct-vs-edge.json). Service response fixture normalizes only the machine-specific model path to ${WHISPER_MODEL_DIR}; original local output is retained under .audit. A human has **not** signed off by listening. Use review.html to compare Edge/forced captions against the actual audio; the numerical result does not replace that check or establish accuracy on other audio.

## PyAV compatibility fix and verification

Real audio exposed a dependency regression missed by mocked alignment tests: faster-whisper 1.2.1 calls av.open(metadata_errors=ignore), but av 19.0.1 rejects that parameter. User explicitly approved changing **only av==19.0.1 to av==18.1.0** in requirements.lock. Python remains 3.12; all other runtime pins unchanged. Initial isolated probes were followed by final acceptance on the main local venv, without PYTHONPATH compatibility overrides. Added a real synthesized PCM WAV decoder regression, no model/network required.

Regular tests: 45 Python passed locally; 5 Java pricing/calculator tests passed using only mock tokens. Linux image build with the new pin and pip check passed. Linux test result is recorded after completion below.

## Snapshot pricing configuration

Checked official OpenAI documentation on **2026-10-16**. Requested https://openai.com/api/pricing redirected to the business pricing landing page, so rates were verified against the official model pages and API pricing documentation: [GPT-4o](https://developers.openai.com/api/docs/models/gpt-4o), [GPT-4o mini](https://developers.openai.com/api/docs/models/gpt-4o-mini), [API pricing](https://developers.openai.com/api/docs/pricing).

| Exact configured snapshot | USD input / 1M tokens | USD output / 1M tokens | Mock 1000 input / 500 output |
|---|---:|---:|---:|
| gpt-4o-2024-08-06 | 2.50 | 10.00 | **0.0075 USD** |
| gpt-4o-mini-2024-07-18 | 0.15 | 0.60 | **0.00045 USD** |

Rates are standard uncached text estimates, excluding Batch discounts, cached-input discounts and other modalities. Both snapshot pages currently mark these models deprecated: availability for an actual account remains unverified because no real API request was made. Models were preserved as requested, not silently migrated.

Both private local .env files and their public .env.example templates use exact snapshot IDs. The pricing map is also versioned in config/ai-pricing-2026-10-16.json; Java test loads that same file rather than a different hardcoded test rate table. Missing AI_PRICING_JSON, unknown exact model or missing token counts returns null. AI_PRICING_JSON is consumed by BE, not the Python service. OPENAI_API_KEY remains blank; no credits required or consumed.

## Reproduction (explicit opt-in; never automatic unit-test downloads)

From the BE worktree:

```powershell
.venv/Scripts/python.exe -m pip install -r services/ai/requirements.lock
.venv/Scripts/python.exe -m pip install -r services/ai/requirements.acceptance.txt
.venv/Scripts/python.exe services/ai/scripts/provision_whisper.py --download
.venv/Scripts/python.exe services/ai/scripts/generate_vi_audio.py
.venv/Scripts/python.exe services/ai/scripts/acceptance_alignment.py direct
.venv/Scripts/python.exe services/ai/scripts/run_local_alignment_acceptance.py
```

The service runner reads private services/ai/.env, needs the actual model path/shared key and explicit local confidence 0.03 for this fixture, binds loopback on a free port, uses only a fake OpenAI-key placeholder, tests rename/restore and cleans up its server. Do not run the missing-model smoke script while another process is actively using the weights directory. Fresh Edge generations may vary; preserve the accompanying WordBoundary metadata for each run. Existing committed fixtures record this measured run, not a guarantee of bit-identical future synthesis.

Final Linux verification: 45 tests passed inside the rebuilt Python 3.12 image with --network none. The native WAV decoder test passed on both Windows and Linux. The final loopback runner's threshold assertions also passed on the non-override runtime.
