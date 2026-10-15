from typing import Any
import json
from pathlib import Path

def json_schema(value: Any) -> Any:
    """Translate the existing BE Gemini schema to LangChain JSON Schema."""
    if isinstance(value, list):
        return [json_schema(item) for item in value]
    if not isinstance(value, dict):
        return value
    result = {key: json_schema(item) for key, item in value.items() if key != "nullable"}
    if isinstance(result.get("type"), str):
        result["type"] = result["type"].lower()
    if value.get("nullable"):
        result = {"anyOf": [result, {"type": "null"}]}
    return result


def schema_for(operation: str) -> dict:
    name = "generatedScript" if operation == "generate" else operation
    return json_schema(json.loads((Path(__file__).parent / "contracts" / (name + ".json")).read_text(encoding="utf-8")))
