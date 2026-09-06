"""LM-GUARD AI service -- the Python side of the AIAnalysisService seam.

Implements POST {ANALYZE_PATH} exactly per src/main/java/com/lmguard/ai/dto/AiAnalyzeRequest
and AiAnalyzeResponse, so it is a drop-in for ExternalAIAnalysisService.java once
AI_MOCK_MODE=false and AI_SERVICE_URL points here. See ai-service/README.md to run it.
"""
from __future__ import annotations

import logging

from fastapi import FastAPI, Header, HTTPException, Request
from fastapi.responses import JSONResponse

from .config import settings
from .pipeline import analyze
from .schema import AnalyzeRequest, AnalyzeResponse

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")
logger = logging.getLogger("ai_service")

app = FastAPI(title="LM-GUARD AI Service", version="1.0.0")


def _check_api_key(x_api_key: str | None) -> None:
    if settings.api_key and x_api_key != settings.api_key:
        raise HTTPException(status_code=401, detail="Invalid or missing X-API-Key")


@app.get("/health")
def health() -> dict:
    return {
        "status": "ok",
        "vlmEnabled": bool(settings.enable_vlm and settings.anthropic_api_key),
        "apiKeyRequired": bool(settings.api_key),
    }


@app.post("/analyze", response_model=AnalyzeResponse)
def analyze_endpoint(payload: AnalyzeRequest, x_api_key: str | None = Header(default=None)) -> AnalyzeResponse:
    _check_api_key(x_api_key)

    if not payload.imageUrl or not payload.imageUrl.strip():
        raise HTTPException(status_code=400, detail="imageUrl is required")

    requested_fields = payload.requestedFields or []
    logger.info("Analyzing inspection %s (%d requested fields)", payload.inspectionId, len(requested_fields))

    try:
        return analyze(payload.imageUrl, requested_fields)
    except Exception:  # noqa: BLE001 - last-resort guard; a 500 here still lets Java fall back to mock
        logger.exception("Unhandled error analysing inspection %s", payload.inspectionId)
        raise HTTPException(status_code=500, detail="AI_PROCESSING_ERROR") from None


@app.exception_handler(Exception)
async def unhandled_exception_handler(request: Request, exc: Exception) -> JSONResponse:  # noqa: ARG001
    logger.exception("Unhandled exception")
    return JSONResponse(status_code=500, content={"detail": "AI_PROCESSING_ERROR"})
