"""All configuration comes from the environment. Nothing here is a secret default."""
from __future__ import annotations

import os


def _bool(name: str, default: bool) -> bool:
    raw = os.environ.get(name)
    if raw is None:
        return default
    return raw.strip().lower() in ("1", "true", "yes", "on")


def _float(name: str, default: float) -> float:
    raw = os.environ.get(name)
    try:
        return float(raw) if raw else default
    except ValueError:
        return default


class Settings:
    """Read once at import time; a restart is required to pick up changes, same as the
    Spring Boot backend's own configuration model."""

    # Shared secret the Spring Boot backend sends as `X-API-Key`. Empty = auth disabled
    # (fine for local dev; the operator is expected to set this before exposing the service).
    api_key: str = os.environ.get("AI_SERVICE_API_KEY", "")

    # Gemini vision key for semantic extraction. Absent -> VLM step is skipped and the
    # service degrades to OCR + regex extraction only (lower confidence, never a crash).
    gemini_api_key: str = os.environ.get("GEMINI_API_KEY", "")
    gemini_model: str = os.environ.get("GEMINI_MODEL", "gemini-3.6-flash")

    # Below this image-quality score, the service returns everything as not-detected with a
    # warning rather than guessing from an unreadable photo.
    min_usable_quality: float = _float("AI_MIN_IMAGE_QUALITY", 0.25)

    # Timeout for downloading the package image from the URL the backend gives us.
    image_fetch_timeout_s: float = _float("AI_IMAGE_FETCH_TIMEOUT_S", 15.0)

    # Timeout for the VLM vision call.
    vlm_timeout_s: float = _float("AI_VLM_TIMEOUT_S", 30.0)

    enable_vlm: bool = _bool("AI_ENABLE_VLM", True)


settings = Settings()
