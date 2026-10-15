# Forced caption alignment contract

Task 3 lives in `services/ai`, not the RENDER repository. RENDER still owns TTS generation; callers send the audio and exactly the narration used to synthesize it. No FE/RENDER integration is changed by this backend task.

## Python private API

`POST /transcribe/align`, multipart form, header `X-AI-Api-Key: <AI_SERVICE_KEY>`:

- `file`: audio bytes, 1 byte to 10 MiB (WAV/MP3/etc. as supported by PyAV).
- `text`: known narration, required, 1–10000 characters.
- `language`: defaults to vi.

Response: `{words:[{text,start,end}], segments:[{text,start,end,words}], model, durationSec, method:"forced-alignment", tokensIn:null, tokensOut:null}`. All times are seconds relative to this scene's audio. Audio upload is supported; URL fetching is deliberately not part of this contract. Callers can download their own trusted TTS URL and upload the bytes.

The adapter uses faster-whisper 1.2.1 feature extraction/encoder and its CT2 attention/DTW `find_alignment` method on supplied text tokens. It never calls transcribe/generate to invent a new transcript. The tokenizer task identifier “transcribe” selects language tokens only; it does not execute ASR.

One request aligns one scene, 0.1–30s, within the Whisper text-token context. Longer scenes must be split at known narration/audio boundaries before calling; combine results by adding the audio offset. No proportional text-duration guessing is used. Missing/mismatched text, invalid or low-confidence timestamps, unsupported audio, or context overflow fail instead of returning fabricated timing. This is not a generic STT endpoint or a promise that arbitrary mismatched audio/text can be aligned.

WHISPER_MODEL defaults to small; WHISPER_DEVICE=cpu, WHISPER_COMPUTE_TYPE=int8. WHISPER_MODEL_DIR optionally locates the model cache. WHISPER_LOCAL_FILES_ONLY=true by default: provision model weights separately; API startup/health/tests never download weights. Set false explicitly only when model download is intended. Keep the cache in a mounted writable directory in containers. WHISPER_MIN_WORD_PROBABILITY defaults to 0.05; linguistic accuracy needs real narrated acceptance testing.

Errors: 401 wrong key, 503 missing service key/model, 413 empty/oversized audio, 422 bad text/audio/alignment, 502 unexpected inference error. Uploaded streams are closed after success or failure.

## BE proxy and authorization

- Browser/studio: `POST /api/v1/ai/transcribe/align`, same multipart fields, JWT, Writer/Producer/Admin only.
- Worker: `POST /api/v1/ai/internal/transcribe/align`, same multipart fields, `X-AI-Alignment-Key` matching BE env `AI_ALIGNMENT_WORKER_KEY`. Separate from AI_SERVICE_KEY; blank rejects 503. Wrong/missing key rejects 401. Do not embed either secret in FE code.
- BE sends only its configured service key to AI_SERVICE_URL. It records CAPTION audit success/failure and nullable token usage through M01, never audio blobs or credentials. Worker-origin requests have nullable requester, since no user JWT is present.
- Spring multipart request limits: file 10MB, whole request 11MB. BE validates file/text and the returned word clocks. Remote errors are sanitized; user validation fails 400 and remote/inference failures currently map to 502 (configuration before submission is 503).

Tests cover the actual adapter control flow with fake encoder/alignment output, exact text-token forwarding, no ASR call, timestamp failures, multipart forwarding, model/token audit metadata and both authentication boundaries. They do not download model weights or establish real Vietnamese alignment quality.

Source/API reference: [faster-whisper transcribe.py](https://github.com/SYSTRAN/faster-whisper/blob/v1.2.1/faster_whisper/transcribe.py). The internal alignment API is pinned; re-run adapter acceptance tests before upgrading it.
