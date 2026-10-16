import json
from pathlib import Path
from .models import GenerationRequest
from .schemas import schema_for

ROOT = Path(__file__).parent
ICONS = json.loads((ROOT / "contracts/icons.json").read_text(encoding="utf-8"))
def template(name):
    return (ROOT / "prompts" / (name + ".txt")).read_text(encoding="utf-8")

def build_request(operation, data):
    if operation == "generate":
        duration = data.targetDurationSec or 0
        if duration <= 0:
            pacing = template("pacing_auto") % data.topic
        else:
            count = "4-5" if duration <= 65 else "6-7" if duration <= 100 else "8-10"
            pacing = f"- Tạo {count} cảnh (scenes). Tổng duration các cảnh xấp xỉ {duration} giây (durationInFrames = giây * 30)."
        prompt = template("generate") % (data.topic, data.subject, data.gradeLevel,
            data.narrativeBrief or "không có — bạn tự quyết",
            ("CÁCH DẪN DẮT DO NGƯỜI VIẾT CHỌN: " + data.approach if data.approach else "Tự chọn cách dẫn dắt phù hợp.")
            + ' Ghi tên vào "approachName" và các bước vào "flow". Kết hợp với narrativeBrief nếu được cung cấp; không bỏ guardrail khoa học.', pacing, ", ".join(ICONS))
    else:
        prompt = template(operation) % (data.subject or "STEM", data.gradeLevel or "trung học", data.scriptText)
    instruction = template("system")
    if operation in {"generate", "resegment"}:
        instruction += "\nFor icons[].name and suggestedIcons[], use only these exact names: " + ", ".join(ICONS) + "."
    return GenerationRequest(systemInstruction=instruction, prompt=prompt,
        responseSchema=schema_for(operation), task=operation)
