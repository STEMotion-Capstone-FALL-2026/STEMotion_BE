from typing import Any, Literal
from pydantic import BaseModel, Field, ConfigDict

Operation = Literal["generate", "resegment", "grade", "extract", "terms"]
Subject = Literal["Math", "Physics", "Chemistry", "Biology", "ComputerScience", "CS"]

class GenerationRequest(BaseModel):
    systemInstruction: str = Field(min_length=1, max_length=20000)
    prompt: str = Field(min_length=1, max_length=80000)
    responseSchema: dict[str, Any]
    task: Operation = "generate"

class GenerationResponse(BaseModel):
    result: dict[str, Any]
    model: str
    elapsedMs: int
    inputTokens: int | None = Field(default=None, ge=0)
    outputTokens: int | None = Field(default=None, ge=0)
    totalTokens: int | None = Field(default=None, ge=0)

class ScriptInput(BaseModel):
    model_config = ConfigDict(str_strip_whitespace=True, extra="forbid")
    topic: str = Field(min_length=1, max_length=1000)
    subject: Subject
    gradeLevel: str = Field(min_length=1, max_length=64)
    targetDurationSec: int | None = Field(default=None, ge=0, le=300)
    narrativeBrief: str | None = Field(default=None, max_length=500)
    approach: str | None = Field(default=None, max_length=800)

class AnalysisInput(BaseModel):
    model_config = ConfigDict(str_strip_whitespace=True, extra="forbid")
    scriptText: str = Field(min_length=1, max_length=60000)
    subject: Subject | None = None
    gradeLevel: str | None = Field(default=None, max_length=64)

class ActionInput(AnalysisInput):
    action: Literal["resegment", "grade", "extract", "terms"]
