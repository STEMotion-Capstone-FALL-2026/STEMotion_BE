"""Explicit real-model local acceptance; never collected by pytest or calls OpenAI."""
import argparse
import difflib
import hashlib
import json
import os
from pathlib import Path
import sys
import time
import unicodedata

ROOT = Path(__file__).resolve().parents[3]
OUT = ROOT / ".audit/ai-acceptance-2026-10-16"


def save(name, data):
    (OUT / name).write_text(json.dumps(data, ensure_ascii=False, indent=2,
        default=lambda value: value.item()), encoding="utf-8")


def normalized(text):
    return "".join(c for c in unicodedata.normalize("NFC", text).lower()
                   if unicodedata.category(c)[0] in {"L", "N"})


def units(words):
    result = []
    for word in words:
        parts = [normalized(p) for p in word["text"].split() if normalized(p)]
        for i, part in enumerate(parts):
            # Never invent a boundary inside a multi-syllable token.
            result.append({"text": part, "start": word["start"] if i == 0 else None,
                           "end": word["end"] if i == len(parts) - 1 else None})
    return result


def compare(reference, actual):
    expected, observed = units(reference), units(actual)
    matcher = difflib.SequenceMatcher(a=[w["text"] for w in expected],
                                    b=[w["text"] for w in observed], autojunk=False)
    errors, worst, matched = [], [], 0
    for block in matcher.get_matching_blocks():
        for i in range(block.size):
            a, b = expected[block.a + i], observed[block.b + i]
            matched += 1
            for edge in ("start", "end"):
                if a[edge] is not None and b[edge] is not None:
                    error = float(abs(a[edge] - b[edge])); errors.append(error)
                    worst.append({"text": a["text"], "boundary": edge,
                                  "reference": a[edge], "actual": b[edge], "errorSec": error})
    errors.sort()
    return {"referenceUnits": len(expected), "actualUnits": len(observed), "matchedUnits": matched,
            "textCoverage": matched / max(len(expected), len(observed), 1), "comparedBoundaries": len(errors),
            "meanErrorSec": sum(errors) / len(errors) if errors else None,
            "p95ErrorSec": errors[min(len(errors) - 1, int(len(errors) * .95))] if errors else None,
            "maxErrorSec": max(errors) if errors else None,
            "boundariesBelow300ms": sum(e < .3 for e in errors),
            "allBelow300msAndFullText": bool(errors) and max(errors) < .3 and matched == len(expected) == len(observed),
            "worst": sorted(worst, key=lambda w: w["errorSec"], reverse=True)[:10]}


def direct(model_dir):
    from faster_whisper import WhisperModel
    from faster_whisper.audio import decode_audio
    os.environ["HF_HUB_OFFLINE"] = "1"
    started = time.monotonic()
    model = WhisperModel(str(model_dir), device="cpu", compute_type="int8", cpu_threads=2,
                         num_workers=1, local_files_only=True)
    audio = decode_audio(str(OUT / "vi-HoaiMy.mp3"))
    language, probability, _ = model.detect_language(audio=audio)
    segments, info = model.transcribe(audio, language="vi", word_timestamps=True,
                                     beam_size=5, condition_on_previous_text=False)
    rows = [{"text": s.text, "start": s.start, "end": s.end,
             "words": [{"text": w.word, "start": w.start, "end": w.end,
                        "probability": w.probability} for w in s.words]} for s in segments]
    result = {"detectedLanguage": language, "languageProbability": probability,
              "configuredLanguage": info.language, "elapsedSec": time.monotonic() - started,
              "segments": rows, "words": [w for s in rows for w in s["words"]]}
    save("direct-asr.json", result)
    reference = json.loads((OUT / "edge-boundaries.json").read_text(encoding="utf-8"))
    metrics = compare(reference["words"], result["words"])
    save("direct-vs-edge.json", metrics)
    print(json.dumps({"detectedLanguage": language, "languageProbability": probability,
                      "elapsedSec": result["elapsedSec"], "metrics": metrics}, ensure_ascii=True))


def service(url, key):
    import httpx
    audio_path = OUT / "vi-HoaiMy.mp3"
    started = time.monotonic()
    with audio_path.open("rb") as audio:
        response = httpx.post(url.rstrip("/") + "/transcribe/align", headers={"X-AI-Api-Key": key},
            data={"text": (OUT / "transcript.txt").read_text(encoding="utf-8").strip(), "language": "vi"},
            files={"file": (audio_path.name, audio, "audio/mpeg")}, timeout=180)
    if response.status_code != 200:
        save("service-error.json", {"status": response.status_code, "body": response.json()})
        raise RuntimeError(f"Alignment returned {response.status_code}; see local service-error.json")
    result = response.json()
    save("service-alignment.json", result)
    edge = json.loads((OUT / "edge-boundaries.json").read_text(encoding="utf-8"))
    direct_result = json.loads((OUT / "direct-asr.json").read_text(encoding="utf-8"))
    metrics = {"elapsedSec": time.monotonic() - started, "audioSha256": hashlib.sha256(audio_path.read_bytes()).hexdigest(),
               "serviceVsEdge": compare(edge["words"], result["words"]),
               "serviceVsDirectAsr": compare(direct_result["words"], result["words"])}
    save("service-metrics.json", metrics)
    print(json.dumps(metrics, ensure_ascii=True))
    if not metrics["serviceVsEdge"]["allBelow300msAndFullText"]:
        raise RuntimeError("Known-text alignment failed full-text / <300ms Edge-boundary acceptance")
    if metrics["serviceVsDirectAsr"]["maxErrorSec"] is None or metrics["serviceVsDirectAsr"]["maxErrorSec"] >= .3:
        raise RuntimeError("Service/ASR timing disagreement exceeds 300ms on matched text")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("mode", choices=["direct", "service"])
    parser.add_argument("--model-dir", type=Path, default=ROOT / "models/faster-whisper-small")
    parser.add_argument("--url", default="http://127.0.0.1:8000")
    parser.add_argument("--compat-av-dir", type=Path, help="Explicit isolated compatibility probe only")
    args = parser.parse_args()
    if args.compat_av_dir:
        sys.path.insert(0, str(args.compat_av_dir.resolve()))
    if args.mode == "direct":
        direct(args.model_dir)
    else:
        service(args.url, os.environ["AI_SERVICE_SHARED_KEY"])
