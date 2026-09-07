"""Orchestrates one image analysis end to end:

    fetch image -> quality check -> preprocess -> multi-pass OCR
        -> VLM semantic extraction (if available) + regex fallback
        -> match each value back to an OCR bounding box for evidence
        -> fuse confidence
        -> build the AiAnalyzeResponse-shaped result

This is the only module that talks to all the others; every step is wrapped so a failure in
one stage degrades the result (lower confidence, a warning) rather than raising -- an
inspection must never be lost to an AI-service exception (see docs/AI_PIPELINE.md, "failure
handling").
"""
from __future__ import annotations

import io
import logging
import re
import time
from typing import Optional

import cv2
import httpx
import numpy as np
from PIL import Image as PILImage

from . import normalization, vlm
from .confidence import ConfidenceInputs, fuse
from .config import settings
from .ocr import OcrBlock, full_text, run_multi_pass_ocr
from .preprocessing import assess_quality, enhance_for_ocr, resize_if_needed
from .schema import AnalyzeResponse, BoundingBoxOut, FieldOut

logger = logging.getLogger("ai_service.pipeline")

MODEL_VERSION = "lmguard-ai-service-1.0.0 (rapidocr-onnxruntime + claude-vision)"


def fetch_image(image_url: str) -> bytes:
    with httpx.Client(timeout=settings.image_fetch_timeout_s, follow_redirects=True) as client:
        response = client.get(image_url)
        response.raise_for_status()
        return response.content


_EXIF_ORIENTATION_TAG = 0x0112  # standard EXIF tag id for "Orientation"


def _read_exif_orientation(image_bytes: bytes) -> int:
    """Reads just the EXIF Orientation tag - deliberately metadata-only, never a pixel decode.

    `Image.open()` is lazy in Pillow; `getexif()` only parses the header, so this never invokes
    Pillow's own JPEG pixel decoder. That distinction matters: running Pillow's full decode (via
    `.convert()`/`np.array()`) back to back with OpenCV's `cv2.imdecode` on the same request was
    crashing the ai-service worker outright on Render's Linux containers - a bare 502 with no
    Python traceback, consistent with the two libraries' independently-bundled libjpeg builds
    colliding at the native level. Reading only the header sidesteps that entirely.
    """
    try:
        with PILImage.open(io.BytesIO(image_bytes)) as pil_image:
            return pil_image.getexif().get(_EXIF_ORIENTATION_TAG, 1)
    except Exception:  # noqa: BLE001 - no/corrupt EXIF header: treat as already upright
        return 1


def _apply_exif_orientation(image_bgr: np.ndarray, orientation: int) -> np.ndarray:
    """Applies the rotation/flip an EXIF orientation value (1-8) implies, via cv2 alone."""
    if orientation == 2:
        return cv2.flip(image_bgr, 1)
    if orientation == 3:
        return cv2.rotate(image_bgr, cv2.ROTATE_180)
    if orientation == 4:
        return cv2.flip(image_bgr, 0)
    if orientation == 5:
        return cv2.transpose(image_bgr)
    if orientation == 6:
        return cv2.rotate(image_bgr, cv2.ROTATE_90_CLOCKWISE)
    if orientation == 7:
        return cv2.transpose(cv2.rotate(image_bgr, cv2.ROTATE_180))
    if orientation == 8:
        return cv2.rotate(image_bgr, cv2.ROTATE_90_COUNTERCLOCKWISE)
    return image_bgr


def decode_image(image_bytes: bytes) -> np.ndarray:
    """Decodes to a BGR array, upright.

    Phone/browser cameras routinely save a JPEG with the sensor's native (often sideways or
    upside-down) pixel data plus an EXIF orientation tag telling a viewer how to rotate it for
    display -- they do not bake the rotation into the pixels themselves. `cv2.imdecode` ignores
    that tag entirely, so without correcting for it OCR, Gemini and every bounding box downstream
    would all be computed against a rotated frame while the app itself displays the same JPEG the
    right way up (browsers and Flutter's own image codec both honour EXIF orientation on
    display) -- a silent mismatch between what the pipeline "sees" and what the inspector sees,
    which both degrades text recognition (rotated text reads far worse) and throws off every
    evidence rectangle. Pixel decoding itself stays on the single cv2.imdecode path already in
    production use; see _read_exif_orientation for why Pillow is only asked to read the header.
    """
    array = np.frombuffer(image_bytes, dtype=np.uint8)
    image = cv2.imdecode(array, cv2.IMREAD_COLOR)
    if image is None:
        raise ValueError("Could not decode image bytes (unsupported or corrupt format)")
    return _apply_exif_orientation(image, _read_exif_orientation(image_bytes))


def _encode_jpeg(image_bgr: np.ndarray) -> bytes:
    ok, buffer = cv2.imencode(".jpg", image_bgr, [cv2.IMWRITE_JPEG_QUALITY, 92])
    if not ok:
        raise ValueError("Could not re-encode the upright image to JPEG")
    return buffer.tobytes()


def _word_tokens(text: str) -> set[str]:
    # Alphanumeric runs only, punctuation stripped, so "wecare@in.nestle.com," and
    # "WECARE@IN.NESTLE.COM" (or "1800-103-1947" and "1800 103 1947") tokenize identically -
    # a plain `.split()` treats those as unrelated strings and silently drops a real match.
    return set(re.findall(r"[a-z0-9]+", text.lower()))


def _best_matching_block(needle: Optional[str], blocks: list[OcrBlock]) -> Optional[OcrBlock]:
    if not needle:
        return None
    needle_lower = needle.strip().lower()
    if not needle_lower:
        return None

    # Exact-ish containment first.
    for block in blocks:
        if needle_lower in block.text.lower() or block.text.lower() in needle_lower:
            return block

    # Fall back to token overlap for values the VLM paraphrased slightly, or that OCR and the
    # VLM punctuated differently (a joined "email, phone" value vs. two separate OCR blocks).
    needle_tokens = _word_tokens(needle_lower)
    best, best_overlap = None, 0
    for block in blocks:
        overlap = len(needle_tokens & _word_tokens(block.text))
        if overlap > best_overlap:
            best, best_overlap = block, overlap
    return best if best_overlap > 0 else None


def _count_passes(block: Optional[OcrBlock], all_original: int, all_enhanced: int) -> bool:
    # merge_passes already collapses duplicates; "agreement" is approximated by whether the
    # matched block's text also appears in both raw pass counts being non-trivial. Kept
    # deliberately simple and conservative -- see confidence.py for why this is a small boost.
    return block is not None and all_original > 0 and all_enhanced > 0


def _to_bbox(block: Optional[OcrBlock]) -> Optional[BoundingBoxOut]:
    if block is None:
        return None
    return BoundingBoxOut(x=block.x, y=block.y, width=block.width, height=block.height)


def _choose_value(
    regex_result: Optional[normalization.NormalizedValue],
    vlm_field: Optional[vlm.VlmField],
) -> tuple[Optional[str], Optional[str]]:
    """Picks the value (and the raw text used to locate its OCR bounding box) for one field.

    A confidently-extracted regex normalization wins over the VLM's own phrasing whenever one
    exists: Gemini may report a numeric/date field's value with its printed label still
    attached (e.g. "Net Qty 200 g" instead of "200 g"), which reads fine to a human but fails a
    strict downstream format rule (Rule 13's SI-unit check) that the regex's clean "200 g"
    would have passed. `try_regex_extract` only ever returns a result for fields it has a
    normalizer for (MRP, NET_QUANTITY, dates, consumer care, batch number) -- so this never
    touches semantic-only fields like MANUFACTURER or COMMODITY_NAME, where Gemini remains the
    only source of a value.
    """
    if regex_result is not None and regex_result.normalized is not None:
        return regex_result.normalized, regex_result.raw_text
    if vlm_field is not None and vlm_field.value:
        return vlm_field.value, (vlm_field.quoted_text or vlm_field.value)
    return None, None


def analyze(image_url: str, requested_fields: list[str]) -> AnalyzeResponse:
    warnings: list[str] = []
    started = time.time()

    # --- 1. fetch + decode (EXIF-normalized to upright, matching what the app displays) ---
    try:
        raw_bytes = fetch_image(image_url)
        image = decode_image(raw_bytes)
    except Exception as exc:  # noqa: BLE001
        logger.warning("Could not fetch/decode image %s: %s", image_url, exc)
        return AnalyzeResponse(
            modelVersion=MODEL_VERSION,
            warnings=[f"Could not retrieve or decode the package image: {exc}"],
            fields=[FieldOut(name=f, value=None, confidence=0.0) for f in requested_fields],
        )

    image = resize_if_needed(image)
    # Gemini must see the same upright, resized frame OCR and every bounding box below are
    # computed against -- not the original (possibly sideways) bytes straight off the wire.
    image_bytes = _encode_jpeg(image)

    # --- 2. image quality ---
    quality = assess_quality(image)
    warnings.extend(quality.warnings())

    if quality.score < settings.min_usable_quality:
        warnings.append(
            f"Image quality too low to analyse reliably (score={quality.score:.2f}). "
            "Request a sharper, better-lit photo of the principal display panel."
        )
        return AnalyzeResponse(
            modelVersion=MODEL_VERSION,
            warnings=warnings,
            fields=[FieldOut(name=f, value=None, confidence=0.0) for f in requested_fields],
        )

    # --- 3. preprocessing + multi-pass OCR ---
    enhanced = enhance_for_ocr(image)
    ocr_blocks = run_multi_pass_ocr(image, enhanced)
    ocr_text = full_text(ocr_blocks)
    original_count = sum(1 for b in ocr_blocks if b.pass_name == "original")
    enhanced_count = sum(1 for b in ocr_blocks if b.pass_name == "enhanced")

    if not ocr_blocks:
        warnings.append("OCR read no text at all from this image.")

    # --- 4. VLM semantic extraction (graceful degradation if unavailable) ---
    vlm_fields, vlm_error = vlm.extract_fields(image_bytes, ocr_text, requested_fields)
    if vlm_error:
        warnings.append(f"Semantic (VLM) extraction unavailable: {vlm_error}. "
                         "Falling back to OCR pattern matching only, which cannot reliably "
                         "identify free-text fields such as manufacturer name or commodity name.")
    vlm_by_field = {f.name: f for f in vlm_fields}

    # --- 5. build each requested field ---
    fields: list[FieldOut] = []
    for name in requested_fields:
        vlm_field = vlm_by_field.get(name)
        regex_result = normalization.try_regex_extract(name, ocr_text)

        pattern_confidence = regex_result.pattern_confidence if regex_result else None

        value, raw_text = _choose_value(regex_result, vlm_field)
        # A non-textual field (PACKAGE_CONDITION's own visual observation, not a printed value)
        # has no OCR text region to point to - matching it anyway risks a coincidental
        # token-overlap hit against an unrelated block, which would both draw a meaningless
        # rectangle and (worse) let that spurious OCR corroboration inflate confidence for an
        # observation confidence.py otherwise correctly treats as VLM-only and caps.
        matched_block = (
            _best_matching_block(raw_text, ocr_blocks)
            if raw_text and name not in vlm.NON_TEXTUAL_FIELDS
            else None
        )
        # value stays None when neither path detected anything -- a first-class, honest
        # outcome (see ExtractedFact.notDetected on the Java side), not an error.

        ocr_confidence = matched_block.confidence if matched_block else None
        multi_pass_agreement = _count_passes(matched_block, original_count, enhanced_count)

        confidence, signals = fuse(ConfidenceInputs(
            ocr_confidence=ocr_confidence,
            vlm_confidence=vlm_field.confidence if vlm_field is not None else None,
            pattern_confidence=pattern_confidence if value is not None else None,
            multi_pass_agreement=multi_pass_agreement,
        ))

        if value is None and vlm_field is not None:
            # An explicit "not detected" from the VLM carries its own (absence) confidence.
            confidence = vlm_field.confidence

        fields.append(FieldOut(
            name=name,
            value=value,
            confidence=confidence,
            boundingBox=_to_bbox(matched_block),
            rawText=raw_text,
        ))
        logger.debug("Field %s: value=%r confidence=%.2f signals=%s", name, value, confidence, signals)

    elapsed_ms = int((time.time() - started) * 1000)
    logger.info(
        "Analysis complete in %d ms: %d OCR blocks, %d/%d fields detected, vlm_used=%s",
        elapsed_ms, len(ocr_blocks), sum(1 for f in fields if f.value), len(fields), vlm_error is None,
    )

    return AnalyzeResponse(modelVersion=MODEL_VERSION, warnings=warnings, fields=fields)
