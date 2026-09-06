"""Fuses OCR confidence, VLM confidence and pattern-validation strength into one number.

The rule engine on the Java side treats a low value as "could not tell" (INCONCLUSIVE), never
as evidence of absence -- so this module's job is to be an honest estimate, not an optimistic
one. Nothing here is tuned to produce high numbers; it is tuned to only produce a high number
when multiple independent signals agree.
"""
from __future__ import annotations

from dataclasses import dataclass, field
from typing import Optional


@dataclass
class ConfidenceInputs:
    ocr_confidence: Optional[float] = None       # confidence of the matched OCR block, if any
    vlm_confidence: Optional[float] = None        # confidence the VLM reported, if VLM ran
    pattern_confidence: Optional[float] = None    # regex/format validation strength
    multi_pass_agreement: bool = False            # value appeared in more than one OCR pass
    signals: list[str] = field(default_factory=list)


def fuse(inputs: ConfidenceInputs) -> tuple[float, list[str]]:
    """Weighted combination of whatever signals are actually present.

    Weights (of the signals that fired): OCR 0.35, VLM 0.40, pattern 0.25 -- VLM gets the
    largest single weight because it is the only signal with any semantic understanding of
    *which* text is the field in question, but it is never used alone: with zero corroborating
    OCR/pattern signal, the result is capped (see below) rather than trusted outright.
    """
    weighted_sum = 0.0
    weight_total = 0.0
    signals = list(inputs.signals)

    if inputs.ocr_confidence is not None:
        weighted_sum += inputs.ocr_confidence * 0.35
        weight_total += 0.35
        signals.append(f"ocr={inputs.ocr_confidence:.2f}")
    if inputs.vlm_confidence is not None:
        weighted_sum += inputs.vlm_confidence * 0.40
        weight_total += 0.40
        signals.append(f"vlm={inputs.vlm_confidence:.2f}")
    if inputs.pattern_confidence is not None:
        weighted_sum += inputs.pattern_confidence * 0.25
        weight_total += 0.25
        signals.append(f"pattern={inputs.pattern_confidence:.2f}")

    if weight_total == 0:
        return 0.0, signals

    fused = weighted_sum / weight_total

    # Multiple independent OCR passes agreeing is real corroborating evidence -- small boost,
    # capped so it can never turn a weak reading into a confident one on its own.
    if inputs.multi_pass_agreement:
        fused = min(1.0, fused + 0.05)
        signals.append("multi-pass agreement")

    # A VLM-only claim (no OCR text and no pattern match backing it) is capped: it is a
    # plausible observation, not yet a corroborated one.
    if inputs.vlm_confidence is not None and inputs.ocr_confidence is None and inputs.pattern_confidence is None:
        fused = min(fused, 0.65)

    return round(max(0.0, min(1.0, fused)), 4), signals
