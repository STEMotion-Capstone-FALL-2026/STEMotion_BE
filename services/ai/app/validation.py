import math
from fastapi import HTTPException
from jsonschema import Draft202012Validator
from .prompting import ICONS
from .schemas import schema_for


def invalid():
    raise HTTPException(502, "AI returned incomplete or invalid scientific content")

def nonempty(value):
    return isinstance(value, str) and bool(value.strip())

def validate_result(operation, result):
    if not isinstance(result, dict) or not Draft202012Validator(schema_for(operation)).is_valid(result):
        invalid()
    if operation == "generate":
        if result.get("isValidStemTopic") is False:
            if result.get("scenes") != []:
                invalid()
            return
        scenes = result.get("scenes", [])
        if not 4 <= len(scenes) <= 10 or scenes[0].get("type") != "TITLE_HERO" or scenes[-1].get("type") != "OUTRO":
            invalid()
        for scene in scenes:
            if not all(nonempty(scene.get(k)) for k in ("title", "narration")):
                invalid()
            frames = scene.get("durationInFrames")
            if isinstance(frames, bool) or not isinstance(frames, int) or not 1 <= frames <= 18000:
                invalid()
            kind = scene["type"]
            required = {"MATH_FORMULA": ["latex"], "CHEMICAL_REACTION": ["equation"],
                "ILLUSTRATED_EXPLAINER": ["headline"], "PROCESS_TIMELINE": ["processTitle"]}.get(kind, [])
            if not all(nonempty(scene.get(k)) for k in required):
                invalid()
            if kind == "MATH_FORMULA" and not scene.get("steps"):
                invalid()
            if kind == "ILLUSTRATED_EXPLAINER" and not scene.get("icons"):
                invalid()
            if kind == "DATA_CHART":
                points = scene.get("dataPoints")
                if not points or any(not nonempty(p.get("label")) or isinstance(p.get("value"), bool)
                    or not isinstance(p.get("value"), (int, float)) or not math.isfinite(p["value"]) for p in points):
                    invalid()
            if kind == "OUTRO" and (not scene.get("summaryPoints") or not nonempty(scene.get("nextLessonSuggestion"))):
                invalid()
    if operation == "resegment" and not result.get("scenes"):
        invalid()
    if operation in {"generate", "resegment"}:
        for scene in result.get("scenes", []):
            if any(i.get("name") not in ICONS for i in scene.get("icons", [])):
                invalid()
            if any(i not in ICONS for i in scene.get("suggestedIcons", [])):
                invalid()
