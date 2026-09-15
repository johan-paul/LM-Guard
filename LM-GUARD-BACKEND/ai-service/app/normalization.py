"""Turns raw OCR text into normalized values for the fields LM-GUARD's rule engine checks.

The rule is the same everywhere in this module: never lose the raw text, never invent a value
that is not supported by a regex match, and return (normalized_value, pattern_confidence) so
the caller can fuse it with OCR/VLM confidence rather than treating a regex hit as certainty.
"""
from __future__ import annotations

import re
from dataclasses import dataclass
from typing import Optional

# Most `\d` uses below (used throughout the patterns in this module) already match Unicode
# decimal digits under default (non-ASCII) matching -- including Devanagari, Tamil, Telugu,
# Kannada and Malayalam digit ranges. _DATE_PATTERN is the exception: its day/month components
# use literal ASCII digit-class ranges (`0?[1-9]`, `1[0-2]`, `19|20`), not `\d`, so a
# native-digit date would never match at all if left as-is. Rather than special-case that one
# pattern, every normalize_* function below matches against an ASCII-translated COPY of its
# input, uniformly, and keeps `raw_text` as the genuine original line -- so evidence-matching
# back to the real OCR block (see pipeline.py::_best_matching_block) still works against
# untranslated text, while `normalized` always comes back as an ASCII string the Java rule
# engine can parse as a number.
_INDIC_DIGIT_RANGES = (
    0x0966,  # Devanagari 0-9 (Hindi, Marathi, Nepali)
    0x0BE6,  # Tamil 0-9
    0x0C66,  # Telugu 0-9
    0x0CE6,  # Kannada 0-9
    0x0D66,  # Malayalam 0-9
)
_INDIC_DIGIT_TRANSLATION = {
    ord(chr(base + digit)): str(digit) for base in _INDIC_DIGIT_RANGES for digit in range(10)
}


def _to_ascii_digits(s: str) -> str:
    return s.translate(_INDIC_DIGIT_TRANSLATION)


# Unicode codepoint ranges for the five target scripts, for detect_scripts() below.
_SCRIPT_RANGES = {
    "devanagari": (0x0900, 0x097F),
    "tamil": (0x0B80, 0x0BFF),
    "telugu": (0x0C00, 0x0C7F),
    "kannada": (0x0C80, 0x0CFF),
    "malayalam": (0x0D00, 0x0D7F),
}


def detect_scripts(text: str) -> set[str]:
    """Which of the five target scripts appear anywhere in `text`. Logging/telemetry only --
    deliberately never used to gate which patterns run (see module docstring in the plan this
    implements: mixed-script lines, e.g. an English 'MRP' next to a Devanagari price, are
    common on Indian packaging, so gating on detected script is a false-negative risk)."""
    found: set[str] = set()
    for ch in text:
        code = ord(ch)
        for script, (lo, hi) in _SCRIPT_RANGES.items():
            if lo <= code <= hi:
                found.add(script)
                break
    return found


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

# The standard way a multi-component pack states its combined declaration:
# "11.9 g + 3.6 g = 15.5 g". Matches only the value right after the "=", never a bare number -
# this is what tells the total apart from an unrelated number elsewhere on the label (a
# nutrition table's grams-of-carbohydrate/sugar/fat figures, for instance, are never written
# with a leading "=").
_QUANTITY_TOTAL_PATTERN = re.compile(
    r"=\s*(?P<value>\d{1,7}(?:[.,]\d{1,3})?)\s*(?P<unit>kg|gm|g|mg|ml|mL|l|L|N|U|pcs|pieces|cm|mm|m)\b",
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
#
# Regional-language terms below are the common words used on Indian packaging (FSSAI-regulated
# food labels routinely print these alongside or instead of the English MFD/EXP abbreviations);
# kept as single distinguishing roots rather than full multi-word phrases, since OCR spacing
# around a multi-word Indic phrase is not reliable enough to match verbatim. Root, not gate:
# these are additive alternatives in the same pattern, tried regardless of any other script on
# the line (see detect_scripts() above for why gating by detected script is not done).
_MANUFACTURE_KEYWORDS = re.compile(
    r"\b(mfd|mfg|manufactured|packed|pkd|pre-?packed"
    r"|निर्माण|उत्पादन|पैकिंग"          # Hindi/Marathi/Nepali (Devanagari): manufacture/production/packing
    r"|உற்பத்தி|தயாரிப்பு"              # Tamil: manufacture/production
    r"|తయారీ|ఉత్పత్తి"                  # Telugu: manufacture/production
    r"|ತಯಾರಿಕಾ|ಉತ್ಪಾದನೆ"                # Kannada: manufacture/production
    r"|നിർമ്മാണ|ഉത്പാദന"                # Malayalam: manufacture/production
    r")\b",
    re.IGNORECASE,
)
_EXPIRY_KEYWORDS = re.compile(
    r"\b(exp|expiry|expires?|use\s*before|best\s*before"
    r"|समाप्ति|एक्सपायरी"               # Hindi/Marathi/Nepali (Devanagari): expiry
    r"|காலாவதி"                          # Tamil: expiry
    r"|గడువు"                            # Telugu: expiry/deadline
    r"|ಮುಕ್ತಾಯ"                          # Kannada: expiry/end
    r"|കാലഹരണ"                           # Malayalam: expiry
    r")\b",
    re.IGNORECASE,
)

_EMAIL_PATTERN = re.compile(r"[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}")
_PHONE_PATTERN = re.compile(r"(?:\+91[\-\s]?)?\b[6-9]\d{9}\b|1800[\-\s]?\d{3}[\-\s]?\d{3,4}")
_BATCH_PATTERN = re.compile(r"(?:Batch|B\.?No\.?|Lot)\s*[:\-]?\s*([A-Z0-9\-/]{3,20})", re.IGNORECASE)


def normalize_mrp(text: str) -> NormalizedValue:
    # Matched against an ASCII-digit copy, not `text` itself: _DATE_PATTERN elsewhere in this
    # module uses literal ASCII digit-class ranges (not `\d`) for its day/month components, so
    # translating only a captured group *after* matching would miss native-digit dates
    # entirely (the match would never happen in the first place). Matching everything against
    # a pre-translated copy, while keeping `raw_text` as the genuine original line, fixes that
    # uniformly and still leaves the original line intact for evidence-matching back to its OCR
    # block (see pipeline.py::_best_matching_block, which needs the real OCR text, not a
    # translated stand-in, to find a substring/token match).
    ascii_text = _to_ascii_digits(text)
    match = _MRP_PATTERN.search(ascii_text) or _MRP_BARE_PATTERN.search(ascii_text)
    if not match:
        return NormalizedValue(raw_text=text, normalized=None, pattern_confidence=0.0)
    amount = match.group("amount").replace(",", ".")
    confidence = 0.9 if match.re is _MRP_PATTERN else 0.65  # bare "MRP 99" is a weaker signal
    return NormalizedValue(raw_text=text, normalized=amount, unit="INR", pattern_confidence=confidence)


def normalize_quantity(text: str) -> NormalizedValue:
    match = _QUANTITY_PATTERN.search(_to_ascii_digits(text))
    if not match:
        return NormalizedValue(raw_text=text, normalized=None, pattern_confidence=0.0)
    value = match.group("value").replace(",", ".")
    unit = match.group("unit")
    return NormalizedValue(raw_text=text, normalized=f"{value} {unit}", unit=unit, pattern_confidence=0.85)


def normalize_date(text: str) -> NormalizedValue:
    """Context-free date extraction: finds *a* date, without knowing which kind. Kept for
    fields where that ambiguity doesn't matter; MANUFACTURE_DATE/EXPIRY_DATE use the
    keyword-aware variants below instead."""
    match = _DATE_PATTERN.search(_to_ascii_digits(text))
    if not match:
        return NormalizedValue(raw_text=text, normalized=None, pattern_confidence=0.0)
    return NormalizedValue(raw_text=text, normalized=match.group(0), pattern_confidence=0.8)


def _normalize_date_with_keyword(text: str, keyword_pattern: re.Pattern, other_keyword_pattern: re.Pattern) -> NormalizedValue:
    # Digit translation doesn't touch letters, so it's safe to run the keyword checks below
    # against the same translated copy the date pattern needs (see normalize_mrp for why
    # translating before matching, not after, is required).
    ascii_text = _to_ascii_digits(text)
    date_match = _DATE_PATTERN.search(ascii_text)
    if not date_match:
        return NormalizedValue(raw_text=text, normalized=None, pattern_confidence=0.0)

    has_own_keyword = bool(keyword_pattern.search(ascii_text))
    has_other_keyword = bool(other_keyword_pattern.search(ascii_text))

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

    if field == "NET_QUANTITY":
        return _best_quantity(full_text, normalizer)

    for line in full_text.splitlines():
        result = normalizer(line)
        if result.normalized is not None:
            return result
    # Some fields (date, quantity) occasionally split across OCR line breaks; try the whole
    # blob as a last resort.
    return normalizer(full_text)


def _best_quantity(full_text: str, normalizer) -> Optional[NormalizedValue]:
    """A multi-component pack ("11.9 g + 3.6 g = 15.5 g") declares each part's quantity as well
    as the total; Rule 6(1)(c) requires the *total*. Returning the first line's match (as every
    other field above does) grabs a component instead, because OCR usually reads a declaration
    like that as more than one line/block.

    An earlier version of this function picked the *largest* number+unit match anywhere in the
    OCR text on the theory that the total is always the biggest figure in the declaration - true
    within the declaration itself, but not safe across the *whole* label: a nutrition table a
    few lines below (carbohydrate/sugar/fat, all given in grams) can easily contain a number
    larger than the true net quantity, and got wrongly preferred over it. Looking specifically
    for the "= <value> <unit>" total notation is unambiguous - nothing else on a label is
    written that way - and only falling back to the plain first-match behaviour (identical to
    every other regex field) when no such total is present keeps the ordinary single-quantity
    case exactly as reliable as before.
    """
    for line in full_text.splitlines():
        total_match = _QUANTITY_TOTAL_PATTERN.search(line)
        if total_match:
            value = _to_ascii_digits(total_match.group("value")).replace(",", ".")
            unit = total_match.group("unit")
            return NormalizedValue(raw_text=line, normalized=f"{value} {unit}", unit=unit, pattern_confidence=0.9)

    total_match = _QUANTITY_TOTAL_PATTERN.search(full_text)
    if total_match:
        value = _to_ascii_digits(total_match.group("value")).replace(",", ".")
        unit = total_match.group("unit")
        return NormalizedValue(raw_text=full_text, normalized=f"{value} {unit}", unit=unit, pattern_confidence=0.9)

    for line in full_text.splitlines():
        result = normalizer(line)
        if result.normalized is not None:
            return result
    return normalizer(full_text)
