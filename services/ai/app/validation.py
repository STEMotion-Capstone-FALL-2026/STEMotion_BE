import logging
import math
import re
from fastapi import HTTPException
from jsonschema import Draft202012Validator
from .prompting import ICONS
from .schemas import schema_for


log = logging.getLogger("ai.validation")


class InvalidResult(HTTPException):
    """A model result that breaks the contract. The reason goes to the log and, on a retry,
    back to the model; the client keeps the generic message."""
    def __init__(self, reason):
        super().__init__(502, "AI returned incomplete or invalid scientific content")
        self.reason = reason


def invalid(reason="unspecified"):
    log.warning("AI result refused: %s", reason)
    raise InvalidResult(reason)


def _nonempty_list(value):
    return isinstance(value, list) and len(value) > 0

def nonempty(value):
    return isinstance(value, str) and bool(value.strip())

# Fields that already name a scene, in order of preference, when the model leaves out its title,
# then a plain label for the template.
TITLE_SOURCES = ("headline", "diagramTitle", "processTitle", "theoremName")
TITLE_BY_TYPE = {"TITLE_HERO": "Mở đầu", "MATH_FORMULA": "Công thức", "DIAGRAM_EXPLAINER": "Sơ đồ",
    "DATA_CHART": "Biểu đồ", "ALGORITHM_WALKTHROUGH": "Thuật toán", "STEM_QUIZ": "Câu hỏi",
    "OUTRO": "Tổng kết", "CHEMICAL_REACTION": "Phản ứng hoá học", "COMPARISON_SPLIT": "So sánh",
    "PROCESS_TIMELINE": "Quy trình", "GEOMETRY_SPACE": "Hình học", "ILLUSTRATED_EXPLAINER": "Minh hoạ",
    "COMPOSED": "Phân cảnh"}


# Vietnamese narration is read at about 2.2 words per second (the API's QA uses the same rate).
WORDS_PER_SECOND = 2.2
NARRATION_PAUSE_SEC = 0.6
FPS = 30

_LATEX_WORDS = {r"\\Omega": " ôm", r"\\omega": " omega", r"\\Delta": " đen-ta ", r"\\alpha": " an-pha ",
    r"\\beta": " bê-ta ", r"\\pi": " pi ", r"\\cdot": " nhân ", r"\\times": " nhân ", r"\\div": " chia ",
    r"\\approx": " xấp xỉ ", r"\\leq": " nhỏ hơn hoặc bằng ", r"\\geq": " lớn hơn hoặc bằng ", r"\\to": " đến "}


def speakable(text):
    r"""Narration is read aloud and shown as captions, so LaTeX the model slipped in
    ($I = \dfrac{U}{R}$, $12\ \text{V}$) becomes words. Only notation changes."""
    if not isinstance(text, str) or ("$" not in text and "\\" not in text):
        return text
    out = text
    for _ in range(3):  # nested fractions
        out = re.sub(r"\\[dt]?frac\s*\{([^{}]*)\}\s*\{([^{}]*)\}", r"\1 chia \2", out)
    out = re.sub(r"\\sqrt\s*\{([^{}]*)\}", r"căn \1", out)
    out = re.sub(r"\\(?:text|mathrm|mathbf|operatorname)\s*\{([^{}]*)\}", r"\1", out)
    for command, word in _LATEX_WORDS.items():
        out = re.sub(command + r"(?![A-Za-z])", word, out)
    out = re.sub(r"\^\{?2\}?", " bình phương", out)
    out = re.sub(r"\^\{?3\}?", " lập phương", out)
    out = out.replace("\\,", " ").replace("\\ ", " ").replace("\\;", " ")
    out = re.sub(r"\\[A-Za-z]+", "", out)
    out = out.replace("$", "").replace("{", "").replace("}", "")
    return re.sub(r"\s+", " ", out).strip()


def normalize_scenes(operation, result):
    """Display labels and pacing the model got wrong, fixed without inventing content:
    - a missing title, or one that is just the template name (TITLE_HERO), is taken from the
      scene's own naming field, the lesson title for the opening scene, or a template label;
    - narration loses LaTeX notation so it can be read aloud and captioned;
    - a scene is never shorter than its narration takes to read (QA's narration pacing);
    - an icon outside the catalog becomes "sparkle", as the API does for Gemini drafts."""
    if operation not in {"generate", "resegment"} or not isinstance(result, dict):
        return result
    lesson_title = result.get("title") if nonempty(result.get("title")) else None
    for index, scene in enumerate(result.get("scenes") or []):
        if not isinstance(scene, dict):
            continue
        kind = scene.get("type")
        title = scene.get("title")
        if not nonempty(title) or (isinstance(kind, str) and title.strip().upper() == kind.upper()):
            source = next((scene[k].strip() for k in TITLE_SOURCES if nonempty(scene.get(k))), None)
            if not source and kind == "TITLE_HERO" and lesson_title:
                source = lesson_title.strip()
            scene["title"] = source[:80] if source else TITLE_BY_TYPE.get(kind, f"Phân cảnh {index + 1}")
        if nonempty(scene.get("narration")):
            scene["narration"] = speakable(scene["narration"])
            words = len(scene["narration"].split())
            needed = math.ceil((words / WORDS_PER_SECOND + NARRATION_PAUSE_SEC) * FPS)
            frames = scene.get("durationInFrames")
            if isinstance(frames, int) and not isinstance(frames, bool) and frames < needed:
                scene["durationInFrames"] = min(needed, 18000)
        if kind == "DIAGRAM_EXPLAINER" and not nonempty(scene.get("diagramTitle")) and nonempty(scene.get("title")):
            scene["diagramTitle"] = scene["title"]
        for icon in scene.get("icons") or []:
            if isinstance(icon, dict) and icon.get("name") not in ICONS:
                icon["name"] = "sparkle"
        if isinstance(scene.get("suggestedIcons"), list):
            scene["suggestedIcons"] = [i if i in ICONS else "sparkle" for i in scene["suggestedIcons"]]
        for block in scene.get("blocks") or []:
            if not isinstance(block, dict):
                continue
            if block.get("kind") == "icon" and isinstance(block.get("icon"), dict) and block["icon"].get("name") not in ICONS:
                block["icon"]["name"] = "sparkle"
            for icon in block.get("icons") or []:
                if isinstance(icon, dict) and icon.get("name") not in ICONS:
                    icon["name"] = "sparkle"
    return result


# Kept for callers of the earlier name.
fill_scene_titles = normalize_scenes


def validate_result(operation, result):
    if not isinstance(result, dict):
        invalid("result is not an object")
    errors = sorted(Draft202012Validator(schema_for(operation)).iter_errors(result), key=lambda e: list(e.path))
    if errors:
        first = errors[0]
        invalid(f"schema: {'/'.join(map(str, first.path)) or '(root)'}: {first.message[:120]} ({len(errors)} errors)")
    if operation == "generate":
        if result.get("isValidStemTopic") is False:
            if result.get("scenes") != []:
                invalid()
            return
        scenes = result.get("scenes", [])
        if not 4 <= len(scenes) <= 10 or scenes[-1].get("type") != "OUTRO":
            invalid(f"{len(scenes)} scenes, last {scenes[-1].get('type') if scenes else None}")
        for scene in scenes:
            if not all(nonempty(scene.get(k)) for k in ("title", "narration")):
                invalid(f"{scene.get('type')} scene without title or narration")
            frames = scene.get("durationInFrames")
            if isinstance(frames, bool) or not isinstance(frames, int) or not 1 <= frames <= 18000:
                invalid()
            kind = scene["type"]
            required = {"MATH_FORMULA": ["latex"], "CHEMICAL_REACTION": ["equation"],
                "ILLUSTRATED_EXPLAINER": ["headline"], "PROCESS_TIMELINE": ["processTitle"]}.get(kind, [])
            if not all(nonempty(scene.get(k)) for k in required):
                invalid(f"{kind} scene without {required}")
            if kind == "COMPOSED" and (not scene.get("frame") or not 2 <= len(scene.get("blocks", [])) <= 6):
                invalid("COMPOSED scene without frame or 2-6 blocks")
            if kind == "MATH_FORMULA" and not scene.get("steps"):
                invalid("MATH_FORMULA scene without steps")
            if kind == "DIAGRAM_EXPLAINER" and not (nonempty(scene.get("diagramTitle"))
                    and _nonempty_list(scene.get("labels"))
                    and all(isinstance(l, dict) and nonempty(l.get("name")) for l in scene["labels"])):
                invalid("DIAGRAM_EXPLAINER scene without diagramTitle or labels")
            if kind == "ALGORITHM_WALKTHROUGH" and not (nonempty(scene.get("language"))
                    and nonempty(scene.get("codeSnippet")) and _nonempty_list(scene.get("steps"))):
                invalid("ALGORITHM_WALKTHROUGH scene without language, codeSnippet or steps")
            if kind == "STEM_QUIZ":
                options, answer = scene.get("options"), scene.get("correctIndex")
                if (not nonempty(scene.get("question")) or not isinstance(options, list) or len(options) < 2
                        or isinstance(answer, bool) or not isinstance(answer, int) or not 0 <= answer < len(options)):
                    invalid("STEM_QUIZ scene without question, two options or a valid correctIndex")
            if kind == "ILLUSTRATED_EXPLAINER" and not scene.get("icons"):
                invalid("ILLUSTRATED_EXPLAINER scene without icons")
            if kind == "DATA_CHART":
                points = scene.get("dataPoints")
                if not points or any(not nonempty(p.get("label")) or isinstance(p.get("value"), bool)
                    or not isinstance(p.get("value"), (int, float)) or not math.isfinite(p["value"]) for p in points):
                    invalid("DATA_CHART scene without valid dataPoints")
            if kind == "OUTRO" and (not scene.get("summaryPoints") or not nonempty(scene.get("nextLessonSuggestion"))):
                invalid("OUTRO scene without summaryPoints or nextLessonSuggestion")
    if operation == "resegment" and not result.get("scenes"):
        invalid()
    if operation in {"generate", "resegment"}:
        for scene in result.get("scenes", []):
            for block in scene.get("blocks", []):
                if block.get("kind") == "icon" and block.get("icon", {}).get("name") not in ICONS:
                    invalid()
                if any(i.get("name") not in ICONS for i in block.get("icons", [])):
                    invalid()
            if any(i.get("name") not in ICONS for i in scene.get("icons", [])):
                invalid()
            if any(i not in ICONS for i in scene.get("suggestedIcons", [])):
                invalid()
