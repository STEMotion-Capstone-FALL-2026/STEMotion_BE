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


def _is_object(node: dict) -> bool:
    return node.get("type") == "object" or "properties" in node


def _nullable(node: dict) -> dict:
    if "anyOf" in node:
        return node if any(o.get("type") == "null" for o in node["anyOf"]) else {**node, "anyOf": node["anyOf"] + [{"type": "null"}]}
    return {"anyOf": [node, {"type": "null"}]}


def strict_schema(value: Any) -> Any:
    """OpenAI strict structured outputs need every property listed in "required" and
    additionalProperties false. Properties that were optional become nullable, so the model
    can still leave them out (as null), but can no longer drop a required one."""
    if isinstance(value, list):
        return [strict_schema(item) for item in value]
    if not isinstance(value, dict):
        return value
    node = {key: strict_schema(item) for key, item in value.items() if key != "properties"}
    if _is_object(value):
        required = set(value.get("required", []))
        properties = {}
        for key, child in (value.get("properties") or {}).items():
            child = strict_schema(child)
            properties[key] = child if key in required else _nullable(child)
        node["properties"] = properties
        node["required"] = list(properties)
        node["additionalProperties"] = False
    return node


def drop_optional_nulls(value: Any, schema: Any) -> Any:
    """Undo the strict-mode nulls: remove a null only where the original schema made the
    property optional. Required properties, nullable or not, are left as they are."""
    if isinstance(value, list) and isinstance(schema, dict):
        return [drop_optional_nulls(item, schema.get("items", {})) for item in value]
    if not isinstance(value, dict) or not isinstance(schema, dict):
        return value
    if "anyOf" in schema:
        options = [o for o in schema["anyOf"] if isinstance(o, dict) and o.get("type") != "null"]
        return drop_optional_nulls(value, options[0]) if options else value
    required = set(schema.get("required", []))
    properties = schema.get("properties") or {}
    return {key: drop_optional_nulls(item, properties.get(key, {}))
            for key, item in value.items() if item is not None or key in required}
