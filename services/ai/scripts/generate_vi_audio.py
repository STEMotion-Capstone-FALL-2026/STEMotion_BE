"""Opt-in Edge TTS fixture generation, not a unit test or paid OpenAI call."""
import asyncio
import json
from pathlib import Path
import edge_tts
import av

ROOT = Path(__file__).resolve().parents[3]
OUT = ROOT / ".audit/ai-acceptance-2026-10-16"
TEXT = (
    "Khi đặt một viên đá vào cốc nước, mực nước dâng lên. "
    "Phần nước bị chiếm chỗ có thể tích bằng thể tích viên đá. "
    "Ta đo mực nước trước và sau, rồi lấy giá trị sau trừ giá trị trước. "
    "Cách đo này giúp tìm thể tích của vật rắn có hình dạng không đều. "
    "Khi làm thí nghiệm, hãy thả vật nhẹ nhàng, tránh làm nước bắn ra ngoài "
    "và đọc vạch chia ngang tầm mắt."
)


async def main():
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / "transcript.txt").write_text(TEXT + "\n", encoding="utf-8")
    boundaries = []
    audio = OUT / "vi-HoaiMy.mp3"
    stream = edge_tts.Communicate(TEXT, "vi-VN-HoaiMyNeural", boundary="WordBoundary")
    with audio.open("wb") as target:
        async for chunk in stream.stream():
            if chunk["type"] == "audio":
                target.write(chunk["data"])
            elif chunk["type"] == "WordBoundary":
                boundaries.append({"text": chunk["text"], "start": chunk["offset"] / 10_000_000,
                                   "end": (chunk["offset"] + chunk["duration"]) / 10_000_000})
    with av.open(str(audio)) as container:
        audio_stream = container.streams.audio[0]
        duration = float(audio_stream.duration * audio_stream.time_base)
    (OUT / "edge-boundaries.json").write_text(json.dumps({"voice": "vi-VN-HoaiMyNeural",
        "durationSec": duration, "bytes": audio.stat().st_size, "words": boundaries},
        ensure_ascii=False, indent=2), encoding="utf-8")
    assert 20 <= duration <= 30, f"Audio outside requested acceptance duration: {duration}"
    assert 0 < audio.stat().st_size <= 10 * 1024 * 1024
    assert boundaries
    print(json.dumps({"durationSec": duration, "bytes": audio.stat().st_size, "boundaries": len(boundaries)}))


if __name__ == "__main__":
    asyncio.run(main())
