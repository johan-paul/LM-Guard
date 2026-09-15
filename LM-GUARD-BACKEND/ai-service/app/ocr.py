"""Multi-pass OCR on top of RapidOCR (an ONNX export of the PP-OCR model family -- the same
detector/recognizer lineage as PaddleOCR, but pip-installable with no PaddlePaddle/system
dependency, which is why it was chosen -- see docs/AI_PIPELINE.md).

Every OCR block keeps its bounding box and confidence; nothing here decides what a piece of
text *means* (that is normalization.py and vlm.py's job) or whether it satisfies a legal
requirement (that is exclusively the Java rule engine's job).
"""
from __future__ import annotations

import logging
from dataclasses import dataclass
from functools import lru_cache

import numpy as np

from .config import settings

logger = logging.getLogger("ai_service.ocr")


@dataclass
class OcrBlock:
    text: str
    confidence: float
    x: int
    y: int
    width: int
    height: int
    pass_name: str  # which pass produced it: "original" | "enhanced"
    source: str = "rapidocr"  # which OCR engine produced it: "rapidocr" | "tesseract"

    def bbox_tuple(self) -> tuple[int, int, int, int]:
        return self.x, self.y, self.width, self.height


@lru_cache(maxsize=1)
def _engine():
    # Loaded lazily and cached: constructing RapidOCR loads its ONNX models from disk, which
    # is the expensive part -- this makes every request after the first one fast.
    from rapidocr_onnxruntime import RapidOCR

    return RapidOCR()


def _run_single_pass(image_bgr: np.ndarray, pass_name: str) -> list[OcrBlock]:
    try:
        result, _elapsed = _engine()(image_bgr)
    except Exception:  # noqa: BLE001 - OCR must never crash the whole inspection
        logger.exception("OCR pass '%s' failed", pass_name)
        return []

    blocks: list[OcrBlock] = []
    for item in result or []:
        polygon, text, confidence = item
        xs = [p[0] for p in polygon]
        ys = [p[1] for p in polygon]
        x, y = int(min(xs)), int(min(ys))
        width, height = int(max(xs) - x), int(max(ys) - y)
        if not text or not text.strip():
            continue
        blocks.append(OcrBlock(
            text=text.strip(),
            confidence=float(confidence),
            x=x, y=y, width=max(width, 1), height=max(height, 1),
            pass_name=pass_name,
        ))
    return blocks


def indic_ocr_available() -> bool:
    """Whether the Tesseract binary + language packs are actually usable -- exposed for
    GET /health (see main.py) so an operator can tell the Indic OCR pass is silently
    degrading without reading logs, same as vlmEnabled already does for the VLM step.

    Deliberately NOT cached (unlike `_engine()` above): it reads `settings.enable_indic_ocr`,
    which tests toggle via `monkeypatch.setattr` per the same pattern `enable_vlm` already
    uses elsewhere -- caching would make that monkeypatching silently ineffective after the
    first call in a test process. The underlying `get_tesseract_version()` subprocess call is
    fast (single-digit ms) and only runs once per /analyze request or /health check, not in a
    hot loop, so the perf cost of not caching is negligible.
    """
    if not settings.enable_indic_ocr:
        return False
    try:
        import pytesseract

        if settings.tesseract_cmd:
            pytesseract.pytesseract.tesseract_cmd = settings.tesseract_cmd
        pytesseract.get_tesseract_version()
        return True
    except Exception:  # noqa: BLE001 - binary/langpacks missing: degrade, never crash
        logger.warning(
            "Tesseract not available (binary and/or language packs missing); Indic-script OCR "
            "pass will be skipped. See ai-service/README.md for install instructions.",
            exc_info=True,
        )
        return False


def _run_tesseract_pass(image_bgr: np.ndarray, pass_name: str) -> list[OcrBlock]:
    """Reads Devanagari/Tamil/Telugu/Kannada/Malayalam text RapidOCR's bundled English/Latin
    model cannot. Runs as a single combined-language pass (see app/config.py for why 'mar'/
    'nep' are deliberately excluded from the language list) against the enhanced image only.
    """
    if not indic_ocr_available():
        return []

    try:
        import pytesseract

        data = pytesseract.image_to_data(
            image_bgr,
            lang=settings.tesseract_indic_langs,
            config=f"--psm {settings.tesseract_psm}",
            output_type=pytesseract.Output.DICT,
            timeout=settings.tesseract_timeout_s,
        )
    except Exception:  # noqa: BLE001 - OCR must never crash the whole inspection
        logger.exception("Tesseract OCR pass '%s' failed", pass_name)
        return []

    blocks: list[OcrBlock] = []
    n = len(data.get("text", []))
    for i in range(n):
        text = (data["text"][i] or "").strip()
        if not text:
            continue
        try:
            conf = float(data["conf"][i])
        except (TypeError, ValueError):
            conf = -1.0
        if conf < 0:  # Tesseract uses -1 for non-text layout elements (blocks/paragraphs/lines)
            continue
        blocks.append(OcrBlock(
            text=text,
            confidence=max(0.0, min(1.0, conf / 100.0)),
            x=int(data["left"][i]), y=int(data["top"][i]),
            width=max(int(data["width"][i]), 1), height=max(int(data["height"][i]), 1),
            pass_name=pass_name,
            source="tesseract",
        ))
    return blocks


def _iou(a: OcrBlock, b: OcrBlock) -> float:
    ax2, ay2 = a.x + a.width, a.y + a.height
    bx2, by2 = b.x + b.width, b.y + b.height
    ix1, iy1 = max(a.x, b.x), max(a.y, b.y)
    ix2, iy2 = min(ax2, bx2), min(ay2, by2)
    if ix2 <= ix1 or iy2 <= iy1:
        return 0.0
    intersection = (ix2 - ix1) * (iy2 - iy1)
    union = a.width * a.height + b.width * b.height - intersection
    return intersection / union if union > 0 else 0.0


def merge_passes(*passes: list[OcrBlock]) -> list[OcrBlock]:
    """Deduplicates near-identical detections across passes (same text region read twice),
    keeping the higher-confidence reading, scoped to same-engine (`source`) pairs only: exact
    text match required, same as always -- two passes of the *same* engine disagreeing on a
    region (e.g. one misread it) are deliberately both kept, never merged, since one could be
    the correct reading (see test_ocr_merge.py).

    Blocks from *different* engines (RapidOCR vs Tesseract) are never merged against each other
    at all, fuzzy or exact: their tokenization/normalization differs enough that a text match
    would rarely fire correctly, and the risk of a bad heuristic discarding a correct Tesseract
    reading in favor of a bogus RapidOCR one over the same region outweighs the noise cost of
    leaving both -- unmerged blocks only add corpus text, which can only make
    `_grounded_in_ocr`'s substring check more permissive downstream, never less. Passes are
    complementary, not redundant, so a region only one pass/engine detected is kept as-is.
    """
    merged: list[OcrBlock] = []
    for blocks in passes:
        for block in blocks:
            duplicate = next(
                (existing for existing in merged
                 if existing.source == block.source
                 and _iou(existing, block) > 0.5
                 and existing.text.lower() == block.text.lower()),
                None,
            )
            if duplicate is None:
                merged.append(block)
            elif block.confidence > duplicate.confidence:
                merged.remove(duplicate)
                merged.append(block)
    return merged


def run_multi_pass_ocr(original_bgr: np.ndarray, enhanced_bgr: np.ndarray) -> list[OcrBlock]:
    original_blocks = _run_single_pass(original_bgr, "original")
    enhanced_blocks = _run_single_pass(enhanced_bgr, "enhanced")
    # Indic pass runs only against the enhanced image (keeps total OCR passes at 3, not 4+;
    # see app/config.py).
    indic_blocks = _run_tesseract_pass(enhanced_bgr, "enhanced")
    merged = merge_passes(original_blocks, enhanced_blocks, indic_blocks)
    logger.info(
        "OCR: %d blocks from original pass, %d from enhanced pass, %d from Indic (Tesseract) "
        "pass, %d after merge",
        len(original_blocks), len(enhanced_blocks), len(indic_blocks), len(merged),
    )
    return merged


def full_text(blocks: list[OcrBlock]) -> str:
    return "\n".join(block.text for block in blocks)
