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
    keeping the higher-confidence reading. Passes are complementary, not redundant, so a
    region only one pass detected is kept as-is."""
    merged: list[OcrBlock] = []
    for blocks in passes:
        for block in blocks:
            duplicate = next(
                (existing for existing in merged
                 if _iou(existing, block) > 0.5
                 and (existing.text.lower() == block.text.lower())),
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
    merged = merge_passes(original_blocks, enhanced_blocks)
    logger.info(
        "OCR: %d blocks from original pass, %d from enhanced pass, %d after merge",
        len(original_blocks), len(enhanced_blocks), len(merged),
    )
    return merged


def full_text(blocks: list[OcrBlock]) -> str:
    return "\n".join(block.text for block in blocks)
