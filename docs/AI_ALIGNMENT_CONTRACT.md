# Forced caption alignment contract

Task 3 lives in `services/ai`, not the RENDER repository. RENDER still owns TTS generation; callers send the audio and exactly the narration used to synthesize it. No FE/RENDER integration is changed by this backend task.

## Python private API

`POST /transcribe/align`, multipart form, header `X-AI-Api-Key: <AI_SERVICE_SHARED_KEY>` (legacy alias `AI_SERVICE_KEY` remains supported):

- `file`: audio bytes, 1 byte to 10 MiB (WAV/MP3/etc. as supported by PyAV).
- `text`: known narration, required, 1–10000 characters.
- `language`: defaults to vi.

Response: `{words:[{text,start,end}], segments:[{text,start,end,words}], model, durationSec, method:"forced-alignment", tokensIn:null, tokensOut:null}`. All times are seconds relative to this scene's audio. Audio upload is supported; URL fetching is deliberately not part of this contract. Callers can download their own trusted TTS URL and upload the bytes.

The adapter uses faster-whisper 1.2.1 feature extraction/encoder and its CT2 attention/DTW `find_alignment` method on supplied text tokens. It never calls transcribe/generate to invent a new transcript. The tokenizer task identifier “transcribe” selects language tokens only; it does not execute ASR.

One request aligns one scene, 0.1–30s, within the Whisper text-token context. Longer scenes must be split at known narration/audio boundaries before calling; combine results by adding the audio offset. No proportional text-duration guessing is used. Missing/mismatched text, invalid or low-confidence timestamps, unsupported audio, or context overflow fail instead of returning fabricated timing. This is not a generic STT endpoint or a promise that arbitrary mismatched audio/text can be aligned.

WHISPER_MODEL defaults to small; WHISPER_DEVICE=cpu, WHISPER_COMPUTE_TYPE=int8. For the deployment launcher, WHISPER_MODEL_DIR points to the actual CTranslate2 folder containing model.bin/config.json/tokenizer.json (not just the hub cache root). Local provisioning uses ./models/faster-whisper-small; Compose maps this to /models/faster-whisper-small when WHISPER_MODEL=faster-whisper-small. The launcher selects this absolute model path, forces local-files-only/offline, and performs bounded model-load preflight. Missing weights fail fast in required mode. A read-only weight mount is sufficient after provisioning; no startup download is performed. Use python -m app.launch for deployment preflight. Direct uvicorn skips these startup checks. WHISPER_MIN_WORD_PROBABILITY defaults to 0.05; local acceptance used 0.03 explicitly, without changing that default.

Errors: 401 wrong key, 503 missing service key/model, 413 empty/oversized audio, 422 bad text/audio/alignment, 502 unexpected inference error. Uploaded streams are closed after success or failure.

## BE proxy and authorization

- Browser/studio: `POST /api/v1/ai/transcribe/align`, same multipart fields, JWT, Writer/Producer/Admin only.
- Worker: `POST /api/v1/ai/internal/transcribe/align`, same multipart fields, `X-AI-Alignment-Key` matching BE env `AI_ALIGNMENT_WORKER_KEY`. Separate from AI_SERVICE_KEY; blank rejects 503. Wrong/missing key rejects 401. Do not embed either secret in FE code.
- BE sends only its configured service key to AI_SERVICE_URL. It records CAPTION audit success/failure and nullable token usage through M01, never audio blobs or credentials. Worker-origin requests have nullable requester, since no user JWT is present.
- Spring multipart request limits: file 10MB, whole request 11MB. BE validates file/text and the returned word clocks. Remote errors are sanitized; user validation fails 400 and remote/inference failures currently map to 502 (configuration before submission is 503).

Regular tests cover adapter control flow with fake encoder/alignment output, exact text-token forwarding, no ASR call, timestamp failures, multipart forwarding, model/token audit metadata and both authentication boundaries. A native WAV decoder regression catches the PyAV metadata_errors incompatibility. PyAV is pinned to 18.1.0: 19.0.1 breaks faster-whisper 1.2.1 real audio decoding. Opt-in real Vietnamese acceptance is documented separately in AI_LOCAL_ACCEPTANCE_2026-10-16.md; it is never run automatically by pytest and requires already provisioned weights. Direct ASR is a comparison baseline, not a replacement for the known-text alignment endpoint.

Source/API reference: [faster-whisper transcribe.py](https://github.com/SYSTRAN/faster-whisper/blob/v1.2.1/faster_whisper/transcribe.py). The internal alignment API is pinned; re-run adapter acceptance tests before upgrading it.
