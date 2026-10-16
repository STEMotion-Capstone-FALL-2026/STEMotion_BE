import copy
import pytest
from fastapi.testclient import TestClient
from app.main import app, provider
from app.models import ScriptInput
from app.prompting import build_request

VALID = {"isValidStemTopic": True, "scenes": [
    {"type": "TITLE_HERO", "title": "Ohm", "narration": "Mở đầu", "durationInFrames": 120},
    {"type": "MATH_FORMULA", "title": "Ohm", "narration": "Công thức", "durationInFrames": 150,
     "latex": "U=IR", "steps": [{"label": "Ohm", "latexSnippet": "U=IR", "explanation": "Điện áp"}]},
    {"type": "ILLUSTRATED_EXPLAINER", "title": "Mạch", "narration": "Mạch điện", "durationInFrames": 150,
     "headline": "Mạch điện", "icons": [{"name": "atom", "label": "Vật chất"}]},
    {"type": "OUTRO", "title": "Kết", "narration": "Tổng kết", "durationInFrames": 120,
     "summaryPoints": ["U=IR"], "nextLessonSuggestion": "Mạch nối tiếp"}]}
BODY = {"topic": "Ohm", "subject": "Physics", "gradeLevel": "10", "narrativeBrief": "gameshow"}

@pytest.fixture
def client(monkeypatch):
    monkeypatch.setenv("AI_SERVICE_KEY", "test-key")
    with TestClient(app) as client:
        yield client
    app.dependency_overrides.clear()

HEADERS = {"X-AI-Api-Key": "test-key"}
def stub(result):
    class Stub:
        async def generate(self, request):
            return result, "mock-model", {"input_tokens": 12, "output_tokens": 8, "total_tokens": 20}
    app.dependency_overrides[provider] = Stub

def test_script_generation_preserves_metadata_and_does_not_mutate_input(client):
    stub(VALID)
    r = client.post("/script/generate", json=BODY, headers=HEADERS)
    assert r.status_code == 200, r.text
    assert r.json()["result"] == VALID
    assert r.json()["inputTokens"] == 12

@pytest.mark.parametrize("mutate", [
    lambda r: r["scenes"][1].pop("latex"),
    lambda r: r["scenes"][1].update(latex=""),
    lambda r: r["scenes"][2]["icons"][0].update(name="unicorn"),
    lambda r: r["scenes"][1].update(type="DATA_CHART", dataPoints=[{"label":"bad", "value":"made up"}]),
    lambda r: r.update(scenes=[]),
    lambda r: r.pop("isValidStemTopic"),
])
def test_incomplete_scientific_output_fails_closed(client, mutate):
    result = copy.deepcopy(VALID); mutate(result); stub(result)
    assert client.post("/script/generate", json=BODY, headers=HEADERS).status_code == 502

def test_guardrail_does_not_fabricate_scenes(client):
    result = {"isValidStemTopic": False, "fallbackMessage": "Invalid", "scenes": []}
    stub(result)
    r = client.post("/script/generate", json=BODY, headers=HEADERS)
    assert r.status_code == 200
    assert r.json()["result"] == result

def test_brief_validation_precedes_provider(client):
    assert client.post("/script/generate", json={**BODY, "narrativeBrief": "x" * 501}, headers=HEADERS).status_code == 422

@pytest.mark.parametrize("duration, expected", [(0,"4 ĐẾN 9"),(60,"4-5"),(90,"6-7"),(120,"8-10")])
def test_ported_narrative_guardrail_and_pacing(duration, expected):
    request = build_request("generate", ScriptInput(**BODY, targetDurationSec=duration))
    assert expected in request.prompt
    assert "gameshow" in request.prompt
    assert request.prompt.index("BƯỚC 1:") < request.prompt.index("BƯỚC 1.5:") < request.prompt.index("BƯỚC 2:")
    assert "COMPOSED" in request.prompt
    assert "STEM_QUIZ" in request.prompt

@pytest.mark.parametrize("action,result", [
    ("grade", {"readabilityScore":80,"estimatedGrade":"10","verdict":"OK"}),
    ("extract", {"concepts":[{"name":"Ohm"}]}),
    ("terms", {"issues":[]}),
    ("resegment", {"scenes":[{"type":"MATH_FORMULA","title":"Ohm","narration":"Ohm","suggestedDurationSec":5}]})])
def test_four_explicit_actions_and_legacy_action_contract(client, action, result):
    stub(result); body = {"scriptText":"Ohm", "subject":"Physics", "gradeLevel":"10"}
    for path, data in [("/script/" + action, body), ("/script/action", {**body,"action":action})]:
        response = client.post(path, json=data, headers=HEADERS)
        assert response.status_code == 200, response.text
        assert response.json()["result"] == result


def test_merged_approach_and_composed_scene_contract(client):
    result = copy.deepcopy(VALID)
    result["approachName"] = "interview"
    result["flow"] = ["hook", "explain"]
    result["scenes"][2] = {"type":"COMPOSED", "title":"Ohm", "narration":"Ohm", "durationInFrames":120,
        "frame":"split", "blocks":[{"id":"a","kind":"text","slot":"left","text":"Ohm"},
            {"id":"b","kind":"icon","slot":"right","icon":{"name":"atom"}}]}
    stub(result)
    body = {**BODY, "approach":"electron interview"}
    response = client.post("/script/generate", json=body, headers=HEADERS)
    assert response.status_code == 200, response.text
    assert response.json()["result"] == result
    prompt = build_request("generate", ScriptInput(**body)).prompt
    assert "gameshow" in prompt and "electron interview" in prompt
    result["scenes"][2]["blocks"][1]["icon"]["name"] = "unknown"
    assert client.post("/script/generate", json=body, headers=HEADERS).status_code == 502


def test_approach_limit_before_provider(client):
    assert client.post("/script/generate", json={**BODY,"approach":"a"*801}, headers=HEADERS).status_code == 422
