"""Wire-format models. These MUST match com.lmguard.ai.dto.AiAnalyzeRequest /
AiAnalyzeResponse exactly -- this is the one contract this service does not get to change.
See src/main/java/com/lmguard/ai/dto/ in the Spring Boot backend for the authoritative side.
"""
from __future__ import annotations

from typing import Optional

from pydantic import BaseModel, Field


class AnalyzeRequest(BaseModel):
    imageUrl: str
    inspectionId: Optional[str] = None
    requestedFields: list[str] = Field(default_factory=list)


class BoundingBoxOut(BaseModel):
    x: int
    y: int
    width: int
    height: int


class FieldOut(BaseModel):
    name: str
    value: Optional[str] = None
    confidence: float = 0.0
    boundingBox: Optional[BoundingBoxOut] = None
    rawText: Optional[str] = None


class AnalyzeResponse(BaseModel):
    modelVersion: str
    warnings: list[str] = Field(default_factory=list)
    fields: list[FieldOut] = Field(default_factory=list)
