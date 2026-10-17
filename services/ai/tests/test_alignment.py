import sys
from types import SimpleNamespace
import numpy as np
import pytest
from fastapi import HTTPException
from fastapi.testclient import TestClient
from app.main import app
from app.alignment import aligner, WhisperAligner, format_alignment
import app.alignment as module

HEADERS = {"X-AI-Api-Key":"test-key"}
@pytest.fixture
def client(monkeypatch):
    monkeypatch.setenv("AI_SERVICE_KEY", "test-key")
    with TestClient(app) as c: yield c
    app.dependency_overrides.clear()

def test_endpoint_requires_known_text_and_service_auth(client):
    files={"file":("scene.wav", b"audio", "audio/wav")}
    assert client.post("/transcribe/align",files=files,data={"text":"Ohm"}).status_code == 401
    assert client.post("/transcribe/align",files=files,headers=HEADERS).status_code == 422

def test_endpoint_returns_words_from_injected_aligner(client):
    class Stub:
        def align(self, audio, text, language):
            assert audio == b"audio" and text == "Ohm" and language == "vi"
            return {"words":[{"text":"Ohm","start":0.1,"end":0.5}],"segments":[]}
    app.dependency_overrides[aligner]=Stub
    response=client.post("/transcribe/align", files={"file":("scene.wav",b"audio")}, data={"text":"Ohm"},headers=HEADERS)
    assert response.status_code == 200
    assert response.json()["words"][0]["end"] == 0.5

def test_empty_audio_is_rejected(client):
    assert client.post("/transcribe/align",files={"file":("x.wav",b"")},data={"text":"Ohm"},headers=HEADERS).status_code == 413

def test_actual_adapter_forces_supplied_tokens_without_asr(monkeypatch):
    import faster_whisper.audio as audio
    import faster_whisper.tokenizer as tokenizer
    monkeypatch.setattr(audio,"decode_audio",lambda *a,**kw:np.zeros(16000,dtype=np.float32))
    class Tokens:
        sot_sequence=[1,2]
        def __init__(self,*a,**kw):pass
        def encode(self,text):
            assert text == "Xin chào."
            return [10,11,12]
    monkeypatch.setattr(tokenizer,"Tokenizer",Tokens)
    class Features:
        hop_length=160;nb_max_frames=3000
        def __call__(self,audio):return np.zeros((80,100))
    class Model:
        hf_tokenizer=None;model=SimpleNamespace(is_multilingual=True);max_length=448
        feature_extractor=Features()
        def encode(self,features):return "encoded"
        def find_alignment(self,tokenizer,tokens,encoded,frames):
            assert tokens == [[10,11,12]] and frames == 100 and encoded == "encoded"
            return [[{"word":" Xin","tokens":[10],"start":0.1,"end":0.4,"probability":0.9},
                {"word":" chào","tokens":[11],"start":0.4,"end":0.8,"probability":0.9},
                {"word":".","tokens":[12],"start":0.8,"end":0.8,"probability":0.9}]]
        def transcribe(self,*a,**kw):raise AssertionError("ASR must not be called")
    monkeypatch.setattr(module,"load_model",lambda:Model())
    result=WhisperAligner().align(b"audio","Xin chào.","vi")
    assert [w["text"] for w in result["words"]] == ["Xin","chào."]
    assert result["method"] == "forced-alignment"

@pytest.mark.parametrize("word", [
    {"word":"Ohm","start":0.5,"end":0.1,"probability":0.9},
    {"word":"Ohm","start":0.1,"end":5,"probability":0.9},
    {"word":"Ohm","start":0.1,"end":0.5,"probability":0.001},
    {"word":"different","start":0.1,"end":0.5,"probability":0.9},
])
def test_unreliable_alignment_fails_closed(word):
    with pytest.raises(HTTPException):format_alignment([word],"Ohm",1,"test")


def _words(probabilities):
    return [{"word": f" w{i}", "start": i * 0.3, "end": i * 0.3 + 0.25, "probability": p}
            for i, p in enumerate(probabilities)]

def test_a_few_unexpected_words_are_kept_and_flagged():
    # One spelling variant ("tỉ") among ten words must not drop the scene's captions.
    result = format_alignment(_words([0.9] * 9 + [0.02]), " ".join(f"w{i}" for i in range(10)), 4, "test")
    assert len(result["words"]) == 10
    assert result["lowConfidenceWords"] == 1
    assert result["words"][9]["lowConfidence"] is True and "lowConfidence" not in result["words"][0]

def test_an_alignment_mostly_of_unexpected_words_is_refused():
    with pytest.raises(HTTPException):
        format_alignment(_words([0.9] * 6 + [0.01] * 4), " ".join(f"w{i}" for i in range(10)), 4, "test")

