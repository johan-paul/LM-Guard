"""Semantic extraction via a vision-language model (Claude).

Strict boundary: this module extracts STRUCTURED FACTS ("what is visible"), never a compliance
verdict. It is given the OCR text as context (so it can ground its answer in what OCR actually
read, not invent text) and must return null for anything not visible -- there is a system
prompt instruction to that effect, but untrusted model output is still validated, never trusted
blindly: every value returned is checked against the OCR corpus before being trusted (see
`_grounded_in_ocr` below), and a field the model invents that appears nowhere in the OCR text is
downgraded rather than passed through.
"""
from __future__ import annotations

import base64
import json
import logging
from dataclasses import dataclass
from typing import Optional

from .config import settings

logger = logging.getLogger("ai_service.vlm")

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
}

_EXTRACTION_TOOL = {
    "name": "record_package_facts",
    "description": "Record the declarations visible on this package image.",
    "input_schema": {
        "type": "object",
        "properties": {
            "fields": {
                "type": "array",
                "items": {
                    "type": "object",
                    "properties": {
                        "name": {"type": "string", "description": "One of the requested field names."},
                        "value": {
                            "type": ["string", "null"],
                            "description": "The value exactly as printed, or null if not visible on this image.",
                        },
                        "confidence": {
                            "type": "number",
                            "description": "0..1, your confidence that the value (or its absence) is correct.",
                        },
                        "quoted_text": {
                            "type": ["string", "null"],
                            "description": "The exact substring from the OCR text below that supports this value, or null.",
                        },
                    },
                    "required": ["name", "value", "confidence"],
                },
            }
        },
        "required": ["fields"],
    },
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


def extract_fields(
    image_bytes: bytes,
    ocr_text: str,
    requested_fields: list[str],
) -> tuple[list[VlmField], Optional[str]]:
    """Returns (fields, error). `error` is set (and `fields` empty) when the VLM could not be
    used at all -- caller falls back to OCR/regex-only extraction, never crashes."""
    if not settings.enable_vlm or not settings.anthropic_api_key:
        return [], "VLM disabled or ANTHROPIC_API_KEY not set"

    try:
        import anthropic
    except ImportError:
        return [], "anthropic package not installed"

    client = anthropic.Anthropic(api_key=settings.anthropic_api_key, timeout=settings.vlm_timeout_s)

    field_list = "\n".join(f"- {f}: {FIELD_DESCRIPTIONS.get(f, 'see field name')}" for f in requested_fields)
    prompt = (
        "You are looking at a photograph of a retail package. Below is the OCR text already "
        "read from this image (it may contain errors or be incomplete).\n\n"
        f"OCR TEXT:\n{ocr_text or '(no text was read by OCR)'}\n\n"
        "Extract exactly these fields by calling record_package_facts:\n"
        f"{field_list}\n\n"
        "Rules: (1) Only report a value if it is actually visible in the image. "
        "(2) If a field is not visible, set value to null and give your confidence in that "
        "ABSENCE (e.g. 0.85 confident it is genuinely not printed here, or 0.2 if it might be "
        "on a panel not shown in this photo). (3) Never guess or invent a value. "
        "(4) For every non-null value, quote the exact supporting text in `quoted_text`. "
        "(5) You are extracting facts, not making a legal compliance judgement."
    )

    try:
        response = client.messages.create(
            model=settings.anthropic_model,
            max_tokens=2000,
            tools=[_EXTRACTION_TOOL],
            tool_choice={"type": "tool", "name": "record_package_facts"},
            messages=[{
                "role": "user",
                "content": [
                    {
                        "type": "image",
                        "source": {
                            "type": "base64",
                            "media_type": _media_type(image_bytes),
                            "data": base64.b64encode(image_bytes).decode("ascii"),
                        },
                    },
                    {"type": "text", "text": prompt},
                ],
            }],
        )
    except Exception as exc:  # noqa: BLE001 - network/API failure must degrade, not crash
        logger.warning("VLM call failed: %s", exc)
        return [], f"VLM call failed: {exc}"

    tool_use = next((block for block in response.content if block.type == "tool_use"), None)
    if tool_use is None:
        return [], "VLM did not return a tool_use block"

    try:
        raw_fields = tool_use.input.get("fields", [])
    except (AttributeError, json.JSONDecodeError) as exc:
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
        grounded = _grounded_in_ocr(quoted, ocr_text) if value else True  # absence needs no grounding
        if value is not None and not grounded:
            # The model claimed a value OCR never read. Do not discard it outright (OCR can
            # miss things a VLM reads correctly, e.g. stylised fonts) but do not let it pass
            # as confidently as a grounded reading either.
            confidence = min(confidence, 0.5)
            logger.info("VLM value for %s not found verbatim in OCR text; confidence capped at 0.5", name)
        results.append(VlmField(name=name, value=value, confidence=confidence, quoted_text=quoted, grounded=grounded))

    return results, None
