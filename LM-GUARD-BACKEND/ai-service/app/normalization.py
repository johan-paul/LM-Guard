"""Turns raw OCR text into normalized values for the fields LM-GUARD's rule engine checks.

The rule is the same everywhere in this module: never lose the raw text, never invent a value
that is not supported by a regex match, and return (normalized_value, pattern_confidence) so
the caller can fuse it with OCR/VLM confidence rather than treating a regex hit as certainty.
"""
from __future__ import annotations

import re
from dataclasses import dataclass
from typing import Optional


@dataclass
class NormalizedValue:
    raw_text: str
    normalized: Optional[str]
    unit: Optional[str] = None
    pattern_confidence: float = 0.0  # 0 = no pattern matched at all


_MRP_PATTERN = re.compile(
    r"(?:M\.?\s*R\.?\s*P\.?|Max(?:imum)?\.?\s*Retail\s*Price)?\s*"
    r"(?:₹|Rs\.?|INR)\s*"
    r"(?P<amount>\d{1,7}(?:[.,]\d{1,2})?)\s*(?:/-)?",
    re.IGNORECASE,
)
# Fallback: "MRP 99" with no currency symbol at all, still common on cheap labels.
_MRP_BARE_PATTERN = re.compile(
    r"(?:M\.?\s*R\.?\s*P\.?)\s*[:\-]?\s*(?P<amount>\d{1,7}(?:[.,]\d{1,2})?)", re.IGNORECASE
)

_QUANTITY_PATTERN = re.compile(
    r"(?P<value>\d{1,7}(?:[.,]\d{1,3})?)\s*"
    r"(?P<unit>kg|gm|g|mg|ml|mL|l|L|N|U|pcs|pieces|cm|mm|m)\b",
)

# MM/YYYY, Month YYYY, MM-YYYY, or plain YYYY-MM
_DATE_PATTERN = re.compile(
    r"(?P<md>(?:0?[1-9]|1[0-2])[/\-.](?:19|20)\d{2})"
    r"|(?P<my>(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\.?\s+(?:19|20)\d{2})"
    r"|(?P<ym>(?:19|20)\d{2}[/\-.](?:0?[1-9]|1[0-2]))",
    re.IGNORECASE,
)

# A bare date pattern cannot tell a manufacture date from an expiry date apart -- "MFD
# 03/2026" and "EXP 03/2028" both just look like "a date" to _DATE_PATTERN. These keyword
# sets let normalize_manufacture_date/normalize_expiry_date prefer a line that actually says
# which kind of date it is, falling back to the first date found only when no labeled line
# exists (better than nothing, but the caller should treat that fallback as weaker evidence).
_MANUFACTURE_KEYWORDS = re.compile(r"\b(mfd|mfg|manufactured|packed|pkd|pre-?packed)\b", re.IGNORECASE)
_EXPIRY_KEYWORDS = re.compile(r"\b(exp|expiry|expires?|use\s*before|best\s*before)\b", re.IGNORECASE)

_EMAIL_PATTERN = re.compile(r"[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}")
_PHONE_PATTERN = re.compile(r"(?:\+91[\-\s]?)?\b[6-9]\d{9}\b|1800[\-\s]?\d{3}[\-\s]?\d{3,4}")
_BATCH_PATTERN = re.compile(r"(?:Batch|B\.?No\.?|Lot)\s*[:\-]?\s*([A-Z0-9\-/]{3,20})", re.IGNORECASE)


def normalize_mrp(text: str) -> NormalizedValue:
    match = _MRP_PATTERN.search(text) or _MRP_BARE_PATTERN.search(text)
    if not match:
        return NormalizedValue(raw_text=text, normalized=None, pattern_confidence=0.0)
    amount = match.group("amount").replace(",", ".")
    confidence = 0.9 if match.re is _MRP_PATTERN else 0.65  # bare "MRP 99" is a weaker signal
    return NormalizedValue(raw_text=text, normalized=amount, unit="INR", pattern_confidence=confidence)


def normalize_quantity(text: str) -> NormalizedValue:
    match = _QUANTITY_PATTERN.search(text)
    if not match:
        return NormalizedValue(raw_text=text, normalized=None, pattern_confidence=0.0)
    value = match.group("value").replace(",", ".")
    unit = match.group("unit")
    return NormalizedValue(raw_text=text, normalized=f"{value} {unit}", unit=unit, pattern_confidence=0.85)


def normalize_date(text: str) -> NormalizedValue:
    """Context-free date extraction: finds *a* date, without knowing which kind. Kept for
    fields where that ambiguity doesn't matter; MANUFACTURE_DATE/EXPIRY_DATE use the
    keyword-aware variants below instead."""
    match = _DATE_PATTERN.search(text)
    if not match:
        return NormalizedValue(raw_text=text, normalized=None, pattern_confidence=0.0)
    return NormalizedValue(raw_text=text, normalized=match.group(0), pattern_confidence=0.8)


def _normalize_date_with_keyword(text: str, keyword_pattern: re.Pattern, other_keyword_pattern: re.Pattern) -> NormalizedValue:
    date_match = _DATE_PATTERN.search(text)
    if not date_match:
        return NormalizedValue(raw_text=text, normalized=None, pattern_confidence=0.0)

    has_own_keyword = bool(keyword_pattern.search(text))
    has_other_keyword = bool(other_keyword_pattern.search(text))

    if has_own_keyword:
        confidence = 0.85
    elif has_other_keyword:
        # This line is explicitly labeled as the OTHER kind of date -- do not claim it.
        return NormalizedValue(raw_text=text, normalized=None, pattern_confidence=0.0)
    else:
        # A bare date with no keyword at all on this line: usable, but weaker -- it could be
        # either kind, or neither (e.g. a batch code that happens to look like MM/YYYY).
        confidence = 0.45

    return NormalizedValue(raw_text=text, normalized=date_match.group(0), pattern_confidence=confidence)


def normalize_manufacture_date(text: str) -> NormalizedValue:
    return _normalize_date_with_keyword(text, _MANUFACTURE_KEYWORDS, _EXPIRY_KEYWORDS)


def normalize_expiry_date(text: str) -> NormalizedValue:
    return _normalize_date_with_keyword(text, _EXPIRY_KEYWORDS, _MANUFACTURE_KEYWORDS)


def normalize_consumer_care(text: str) -> NormalizedValue:
    email = _EMAIL_PATTERN.search(text)
    phone = _PHONE_PATTERN.search(text)
    if email and phone:
        return NormalizedValue(raw_text=text, normalized=f"{email.group(0)} / {phone.group(0)}",
                                pattern_confidence=0.9)
    if email:
        return NormalizedValue(raw_text=text, normalized=email.group(0), pattern_confidence=0.75)
    if phone:
        return NormalizedValue(raw_text=text, normalized=phone.group(0), pattern_confidence=0.7)
    return NormalizedValue(raw_text=text, normalized=None, pattern_confidence=0.0)


def normalize_batch_number(text: str) -> NormalizedValue:
    match = _BATCH_PATTERN.search(text)
    if not match:
        return NormalizedValue(raw_text=text, normalized=None, pattern_confidence=0.0)
    return NormalizedValue(raw_text=text, normalized=match.group(1).upper(), pattern_confidence=0.8)


# Fields with a reliable regex signature. Fields NOT in this map (MANUFACTURER, ORIGIN,
# COMMODITY_NAME) require semantic understanding of *which line is which* -- a regex cannot
# tell "ABC Foods Pvt Ltd" (manufacturer) apart from "Classic Salted Chips" (commodity name)
# without meaning, which is exactly why the VLM step exists (see vlm.py).
REGEX_NORMALIZERS = {
    "MRP": normalize_mrp,
    "NET_QUANTITY": normalize_quantity,
    "MANUFACTURE_DATE": normalize_manufacture_date,
    "EXPIRY_DATE": normalize_expiry_date,
    "CONSUMER_CARE": normalize_consumer_care,
    "BATCH_NUMBER": normalize_batch_number,
}


def try_regex_extract(field: str, full_text: str) -> Optional[NormalizedValue]:
    normalizer = REGEX_NORMALIZERS.get(field)
    if normalizer is None:
        return None
    for line in full_text.splitlines():
        result = normalizer(line)
        if result.normalized is not None:
            return result
    # Some fields (date, quantity) occasionally split across OCR line breaks; try the whole
    # blob as a last resort.
    return normalizer(full_text)
