"""Real native decoder regression: mocks missed the PyAV 19 metadata_errors break."""
import io
import wave
import numpy as np
from faster_whisper.audio import decode_audio


def test_real_whisper_decoder_accepts_pcm_wav_without_a_model():
    rate = 16000
    samples = (np.sin(2 * np.pi * 440 * np.arange(rate) / rate) * 8000).astype("<i2")
    buffer = io.BytesIO()
    with wave.open(buffer, "wb") as output:
        output.setnchannels(1)
        output.setsampwidth(2)
        output.setframerate(rate)
        output.writeframes(samples.tobytes())
    buffer.seek(0)
    decoded = decode_audio(buffer, sampling_rate=rate)
    assert decoded.shape == (rate,)
    assert decoded.dtype == np.float32
    assert np.isfinite(decoded).all()
    assert np.max(np.abs(decoded)) > .2
