"""Semantic extraction via a vision-language model (Gemini).

Strict boundary: this module extracts STRUCTURED FACTS ("what is visible"), never a compliance
verdict. It is given the OCR text as context (so it can ground its answer in what OCR actually
read, not invent text) and must return null for anything not visible -- there is a system
prompt instruction to that effect, but untrusted model output is still validated, never trusted
blindly: every value returned is checked against the OCR corpus before being trusted (see
`_grounded_in_ocr` below), and a field the model invents that appears nowhere in the OCR text is
downgraded rather than passed through.

Provider note: this was originally implemented against Claude (tool-use forced structured
output); it now calls Gemini (`response_schema`-constrained JSON output) instead, because the
Anthropic account used during development ran out of credit. The provider-specific code is
confined to this module -- everything below `extract_fields()`'s call site (pipeline.py,
confidence.py, normalization.py) is unchanged and unaware of which vendor answered.
"""
from __future__ import annotations

import json
import logging
import time
from dataclasses import dataclass
from typing import Optional

import httpx

from .config import settings

logger = logging.getLogger("ai_service.vlm")

# Gemini has shown real transient failures in production use (read timeouts, 503 "high
# demand"). A small, bounded retry absorbs those without risking a hung request: at most 2
# retries (3 attempts total), exponential backoff, and it still respects settings.vlm_timeout_s
# on every individual attempt. A permanent failure (bad key, bad model name, 4xx) is not
# retried -- retrying those wastes the retry budget on something that will never succeed.
_MAX_RETRIES = 2
_RETRY_BASE_DELAY_S = 1.0

FIELD_DESCRIPTIONS = {
    "MRP": "Maximum/retail sale price printed on the package, inclusive of taxes (e.g. 'Rs. 99.00').",
    "NET_QUANTITY": "Net quantity of the commodity (e.g. '500 g', '1 L').",
    "MANUFACTURER": "Name and address of the manufacturer, or manufacturer+packer, or importer for an imported package.",
    "ORIGIN": "Country of origin, if explicitly printed on the package (do not infer from language or brand).",
    "CONSUMER_CARE": "Consumer-care contact: name/address/phone/e-mail for complaints.",
    "MANUFACTURE_DATE": "Month and year of manufacture, packing, or import.",
    "EXPIRY_DATE": "Expiry / use-by / best-before date, if present.",
    "BATCH_NUMBER": "Batch or lot number, if present.",
    "COMMODITY_NAME": "Common or generic name of the commodity (e.g. 'Potato Chips', not the brand name).",
    "PACKAGE_CONDITION": (
        "Physical condition of the visible package panel only - not a declaration to read, an "
        "observation to make. Reply exactly 'ACCEPTABLE' if the panel looks flat, intact and "
        "fully legible. Otherwise describe the specific issue in a few words, e.g. 'torn near "
        "the price declaration', 'creased across the manufacturer address', 'a sticker "
        "obscures part of the net quantity'. This is advisory only, for a human inspector to "
        "read - never state or imply a compliance verdict."
    ),
}

# Fields that are a visual judgement call rather than a piece of printed text - there is
# nothing in the OCR corpus for `_grounded_in_ocr` to check a claim like "torn near the price
# declaration" against, since it is the model's own observation of the whole image, not a
# transcription of something printed on it. Without this, every such field would be silently
# capped at confidence 0.5 by the same rule that (correctly) distrusts an ungrounded text claim.
NON_TEXTUAL_FIELDS = frozenset({"PACKAGE_CONDITION"})

# Gemini's response_schema is a select subset of OpenAPI 3.0 (not full JSON Schema): a nullable
# field is `{"type": "string", "nullable": true}`, not `{"type": ["string", "null"]}`.
_RESPONSE_SCHEMA = {
    "type": "object",
    "properties": {
        "fields": {
            "type": "array",
            "items": {
                "type": "object",
                "properties": {
                    "name": {"type": "string", "description": "One of the requested field names."},
                    "value": {
                        "type": "string",
                        "nullable": True,
                        "description": "The value exactly as printed, or null if not visible on this image.",
                    },
                    "confidence": {
                        "type": "number",
                        "description": "0..1, your confidence that the value (or its absence) is correct.",
                    },
                    "quoted_text": {
                        "type": "string",
                        "nullable": True,
                        "description": "The exact substring from the OCR text below that supports this value, or null.",
                    },
                },
                "required": ["name", "value", "confidence"],
            },
        }
    },
    "required": ["fields"],
}


@dataclass
class VlmField:
    name: str
    value: Optional[str]
    confidence: float
    quoted_text: Optional[str]
    grounded: bool


def _media_type(image_bytes: bytes) -> str:
    if image_bytes[:3] == b"\xff\xd8\xff":
        return "image/jpeg"
    if image_bytes[:8] == b"\x89PNG\r\n\x1a\n":
        return "image/png"
    if image_bytes[:4] == b"RIFF":
        return "image/webp"
    return "image/jpeg"


def _grounded_in_ocr(quoted_text: Optional[str], ocr_text: str) -> bool:
    if not quoted_text:
        return False
    needle = quoted_text.strip().lower()
    return len(needle) >= 2 and needle in ocr_text.lower()


def _describe_error(exc: Exception, errors_module) -> str:
    """A one-line, human-readable summary of a failed Gemini call - never the raw API error
    object. This string ends up in a warning banner shown directly to the inspector in the app
    (see pipeline.py's `warnings` list), not just a server log, so a multi-hundred-character
    nested dict of code/status/details/links/retry-info is noise, not information."""
    api_error = getattr(errors_module, "APIError", ())
    if isinstance(exc, api_error):
        code = getattr(exc, "code", None)
        if code == 429:
            return "the daily request quota for the configured Gemini API key has been used up (free-tier limit)"
        message = getattr(exc, "message", None) or getattr(exc, "status", None) or "request failed"
        return f"Gemini API error {code}: {message}"
    text = str(exc)
    return text if len(text) <= 160 else text[:157] + "..."


def extract_fields(
    image_bytes: bytes,
    ocr_text: str,
    requested_fields: list[str],
) -> tuple[list[VlmField], Optional[str]]:
    """Returns (fields, error). `error` is set (and `fields` empty) when the VLM could not be
    used at all -- caller falls back to OCR/regex-only extraction, never crashes."""
    if not settings.enable_vlm or not settings.gemini_api_key:
        return [], "VLM disabled or GEMINI_API_KEY not set"

    try:
        from google import genai
        from google.genai import errors, types
    except ImportError:
        return [], "google-genai package not installed"

    client = genai.Client(api_key=settings.gemini_api_key)

    field_list = "\n".join(f"- {f}: {FIELD_DESCRIPTIONS.get(f, 'see field name')}" for f in requested_fields)
    prompt = (
        "You are looking at a photograph of a retail package. Below is the OCR text already "
        "read from this image (it may contain errors or be incomplete).\n\n"
        f"OCR TEXT:\n{ocr_text or '(no text was read by OCR)'}\n\n"
        "Extract exactly these fields as a `fields` array, one entry per field listed below:\n"
        f"{field_list}\n\n"
        "Rules: (1) Only report a value if it is actually visible in the image. "
        "(2) If a field is not visible, set value to null and give your confidence in that "
        "ABSENCE (e.g. 0.85 confident it is genuinely not printed here, or 0.2 if it might be "
        "on a panel not shown in this photo). (3) Never guess or invent a value. "
        "(4) For every non-null value, quote the exact supporting text in `quoted_text`. "
        "(5) You are extracting facts, not making a legal compliance judgement."
    )

    response = None
    last_error: Optional[Exception] = None
    for attempt in range(_MAX_RETRIES + 1):
        try:
            response = client.models.generate_content(
                model=settings.gemini_model,
                contents=[
                    types.Part.from_bytes(data=image_bytes, mime_type=_media_type(image_bytes)),
                    prompt,
                ],
                config=types.GenerateContentConfig(
                    response_mime_type="application/json",
                    response_schema=_RESPONSE_SCHEMA,
                    http_options=types.HttpOptions(timeout=int(settings.vlm_timeout_s * 1000)),
                ),
            )
            last_error = None
            break
        except Exception as exc:  # noqa: BLE001 - network/API failure must degrade, not crash
            last_error = exc
            transient = isinstance(exc, (httpx.TimeoutException, errors.ServerError))
            if transient and attempt < _MAX_RETRIES:
                delay = _RETRY_BASE_DELAY_S * (2**attempt)
                logger.warning(
                    "Gemini call failed (attempt %d/%d, %s: %s); retrying in %.1fs",
                    attempt + 1, _MAX_RETRIES + 1, exc.__class__.__name__, exc, delay)
                time.sleep(delay)
                continue
            break

    if last_error is not None:
        logger.warning("VLM call failed after %d attempt(s): %s", attempt + 1, last_error)
        return [], f"VLM call failed: {_describe_error(last_error, errors)}"

    if not response.text:
        return [], "VLM returned an empty response"

    try:
        raw_fields = json.loads(response.text).get("fields", [])
    except (json.JSONDecodeError, AttributeError) as exc:
        return [], f"VLM returned malformed structured output: {exc}"

    results: list[VlmField] = []
    for item in raw_fields:
        name = str(item.get("name", "")).strip().upper()
        if not name:
            continue
        value = item.get("value")
        value = value.strip() if isinstance(value, str) and value.strip() else None
        confidence = float(item.get("confidence", 0.0) or 0.0)
        confidence = max(0.0, min(1.0, confidence))
        quoted = item.get("quoted_text")
        # absence needs no grounding; neither does a non-textual field, which was never a claim
        # about printed text to begin with
        grounded = True if (not value or name in NON_TEXTUAL_FIELDS) else _grounded_in_ocr(quoted, ocr_text)
        if value is not None and not grounded:
            # The model claimed a value OCR never read. Do not discard it outright (OCR can
            # miss things a VLM reads correctly, e.g. stylised fonts) but do not let it pass
            # as confidently as a grounded reading either.
            confidence = min(confidence, 0.5)
            logger.info("VLM value for %s not found verbatim in OCR text; confidence capped at 0.5", name)
        results.append(VlmField(name=name, value=value, confidence=confidence, quoted_text=quoted, grounded=grounded))

    return results, None
