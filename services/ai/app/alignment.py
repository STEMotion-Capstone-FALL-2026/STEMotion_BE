"""Known-transcript forced alignment. Never invokes Whisper transcription."""
import io
import math
import os
from functools import lru_cache
from threading import Lock
from fastapi import HTTPException

MODEL_LOCK = Lock()
MAX_AUDIO_BYTES = 10 * 1024 * 1024

@lru_cache(maxsize=1)
def load_model():
    from faster_whisper import WhisperModel
    # Production should pre-provision weights; no key is required for local models.
    return WhisperModel(os.getenv("WHISPER_MODEL", "small"),
        device=os.getenv("WHISPER_DEVICE", "cpu"), compute_type=os.getenv("WHISPER_COMPUTE_TYPE", "int8"),
        download_root=os.getenv("WHISPER_MODEL_DIR") or None,
        local_files_only=os.getenv("WHISPER_LOCAL_FILES_ONLY", "true").lower() == "true")

class WhisperAligner:
    def align(self, audio_bytes: bytes, text: str, language: str):
        from faster_whisper.audio import decode_audio, pad_or_trim
        from faster_whisper.tokenizer import Tokenizer
        from faster_whisper.transcribe import merge_punctuations
        try:
            audio = decode_audio(io.BytesIO(audio_bytes), sampling_rate=16000)
        except Exception:
            raise HTTPException(422, "Audio could not be decoded") from None
        duration = len(audio) / 16000
        if not math.isfinite(duration) or duration < 0.1 or duration > 30:
            raise HTTPException(422, "Align one scene at a time: audio must be 0.1 to 30 seconds")
        # One cached model; bounded concurrent access keeps encoder/decoder memory stable.
        with MODEL_LOCK:
            try:
                model = load_model()
            except Exception:
                raise HTTPException(503, "Alignment model is unavailable; provision WHISPER_MODEL weights") from None
            try:
                tokenizer = Tokenizer(model.hf_tokenizer, model.model.is_multilingual, task="transcribe", language=language)
                tokens = tokenizer.encode(text)
            except Exception:
                raise HTTPException(422, "Unsupported alignment language or text") from None
            if not tokens or len(tokens) > model.max_length - len(tokenizer.sot_sequence) - 1:
                raise HTTPException(422, "Transcript exceeds the alignment context; split the scene")
            features = model.feature_extractor(audio)
            frames = min(round(len(audio) / model.feature_extractor.hop_length), model.feature_extractor.nb_max_frames)
            encoded = model.encode(pad_or_trim(features))
            # CT2 attention/DTW alignment of supplied text tokens; no ASR generate/transcribe.
            alignment = model.find_alignment(tokenizer, [tokens], encoded, frames)[0]
            # Punctuation may have zero duration: attach it to its spoken word first.
            merge_punctuations(alignment, "\"\'“¿([{-", "\"\'.。,，!！?？:：”)]}、")
        return format_alignment(alignment, text, duration, os.getenv("WHISPER_MODEL", "small"))

def format_alignment(alignment, text, duration, model):
    if not alignment:
        raise HTTPException(422, "No reliable word alignment was found")
    raw_text = "".join(word["word"] for word in alignment)
    if "".join(raw_text.split()) != "".join(text.split()):
        raise HTTPException(422, "Aligned text does not match the supplied narration")
    words = []
    previous_end = 0.0
    for word in alignment:
        label = word["word"].strip()
        if not label:
            continue
        start, end = float(word["start"]), float(word["end"])
        if (not math.isfinite(start) or not math.isfinite(end) or start < previous_end - 0.02
                or start < 0 or end <= start or end > duration + 0.05
                or float(word.get("probability", 0)) < float(os.getenv("WHISPER_MIN_WORD_PROBABILITY", "0.05"))):
            raise HTTPException(422, "Low confidence or invalid word timestamps; review the narration/audio")
        start, end = max(previous_end, start), min(end, duration)
        if end <= start:
            raise HTTPException(422, "Invalid word duration")
        words.append({"text": label, "start": round(start, 3), "end": round(end, 3)})
        previous_end = end
    if not words:
        raise HTTPException(422, "No words were aligned")
    return {"words": words, "segments": [{"text": text, "start": words[0]["start"],
        "end": words[-1]["end"], "words": words}], "model": model, "durationSec": duration,
        "method": "forced-alignment", "tokensIn": None, "tokensOut": None}

def aligner():
    return WhisperAligner()
