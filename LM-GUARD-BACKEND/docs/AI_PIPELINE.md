# AI Pipeline

This document explains how `ai-service/` turns a package photo into the `ExtractedFact`s the
Java rule engine judges. For the service's own run instructions, see `ai-service/README.md`.
For the legal content the rule engine judges those facts against, see `legal-rules/`.

## The one rule that shapes everything else

**The AI decides what is visible. The deterministic rule engine decides what is legal.** Every
design choice below exists to keep that boundary intact -- see
`src/main/java/com/lmguard/ai/AIAnalysisService.java`'s own javadoc, which states this as the
seam's contract, not something this session invented.

## Stages

1. **Image quality** (`app/preprocessing.py::assess_quality`) -- Laplacian-variance blur
   detection, a glare heuristic (fraction of near-white/low-saturation pixels), a resolution
   floor, and a global-contrast floor. Produces a 0..1 score and boolean flags. Below
   `AI_MIN_IMAGE_QUALITY` (default 0.25), the service returns every requested field as
   *not detected* with a warning, rather than running OCR on an image that cannot support a
   reliable answer. This is intentionally conservative: **none of these thresholds are a
   metrological measurement** -- there is no physical calibration reference in an arbitrary
   phone photo, so "blur" and "glare" here are heuristics for *whether to trust this photo at
   all*, never a legal finding about the package itself.

2. **Preprocessing** (`app/preprocessing.py::enhance_for_ocr`) -- denoising, CLAHE contrast
   enhancement, and light unsharp-mask sharpening, tuned for printed label text rather than
   natural photography.

3. **Multi-pass OCR** (`app/ocr.py`) -- RapidOCR (ONNX export of the PP-OCR model family,
   CPU-only, no PaddlePaddle/system dependency -- see `ai-service/README.md` for why this was
   used instead of PaddleOCR itself) runs on both the original and the enhanced image. Results
   are merged by bounding-box IoU + exact text match, keeping the higher-confidence reading
   when the same region was read twice, and keeping both when two passes disagree (so neither
   reading is silently thrown away).

4. **Regex/pattern extraction** (`app/normalization.py`) -- fields with a reliable textual
   signature (MRP, net quantity, manufacture/expiry dates, consumer-care contact, batch
   number) are extracted with hand-written patterns that handle the real-world variants named
   in the brief: `MRP ₹99/-`, `MRP Rs.99`, `MRP: Rs 99`, `M.R.P. ₹ 99` all normalize to the
   same value; `Net Qty 500g`, `Net Wt. 0.5 kg`, `Net Quantity: 500 g` likewise. Manufacture
   date and expiry date are disambiguated by a nearby keyword ("MFD"/"packed" vs.
   "EXP"/"best before") -- a date on a line explicitly labeled the *other* kind is refused,
   not guessed at.

5. **VLM semantic extraction** (`app/vlm.py`) -- Claude vision, given the image and the OCR
   text as context, extracts fields a regex cannot identify without understanding meaning:
   manufacturer name, commodity name, country of origin. Uses forced tool-use so the response
   is always valid structured JSON (retry-on-malformed-JSON is therefore structural, not a
   hope). Every non-null value the model returns is checked against the OCR text
   (`_grounded_in_ocr`) -- a claim with no support in what OCR actually read is not discarded,
   but its confidence is capped, because the model is not the ground truth for what is
   physically printed on the package; OCR is.

6. **Evidence recovery** (`app/pipeline.py::_best_matching_block`) -- every extracted value is
   matched back to the OCR block that supports it (substring match, then token overlap), so
   every field carries a real bounding box into the image it came from, not just a value.

7. **Confidence fusion** (`app/confidence.py`) -- a weighted combination of OCR confidence
   (0.35), VLM confidence (0.40) and pattern-validation strength (0.25), of whichever signals
   actually fired, plus a small bounded boost for multi-pass agreement. A VLM claim with
   nothing else backing it is capped at 0.65 -- confident-sounding is not the same as
   corroborated.

## Failure handling

Every stage above is wrapped so a failure degrades the result rather than raising:

| Failure | Behaviour |
|---|---|
| Image URL unreachable | Every field returns `null`/`0.0`, with a warning. No exception reaches the Java caller as an unhandled 500 without a code. |
| Image undecodable | Same as above. |
| OCR raises | That pass is skipped and logged; the other pass (or regex-only) still runs. |
| VLM key missing/disabled | Regex-only extraction, with a warning explaining exactly which fields that weakens (see `ai-service/README.md`). |
| VLM API call fails/times out | Same graceful fallback; the specific error is included in the warning for operator debugging, never exposed as a stack trace. |
| VLM returns malformed JSON | Structurally prevented by forced tool-use; if the SDK still cannot parse it, treated the same as "VLM call failed." |
| Any other unhandled exception in `/analyze` | Caught at the FastAPI layer, returned as `500 {"detail":"AI_PROCESSING_ERROR"}` -- which `ExternalAIAnalysisService.java` already turns into `AiServiceException`, and `AiAnalysisRouter` already knows how to fall back to the mock analyser from (`AI_FALLBACK_TO_MOCK`). Nothing here is new Java-side behaviour; the Python service just fails in the shape the Java side was already built to expect. |

## What this pipeline cannot do, and does not pretend to

- **Millimetre-accurate text height / numeral height** (Rule 7 of the 2011 Rules). No
  calibration reference (a coin, a ruler, a known-size barcode) is present in an arbitrary
  photo, so pixel measurements cannot be converted to millimetres reliably. Not implemented;
  catalogued as `NOT_ASSESSABLE_FROM_IMAGE` in `legal-rules/RULE_COVERAGE.md`.
- **Contrast-ratio legibility** in the formal sense Rule 9 implies. OCR confidence is used as
  a proxy signal, documented as a proxy, not a calibrated measurement.
- **Barcode/GTIN decoding.** Not implemented this session; would be a self-contained addition
  (`pyzbar` or `zxing-cpp`) with no other pipeline changes needed, but there is currently
  nothing in this repository for a decoded GTIN to be checked against.
- **Multi-panel fusion** (front + back + label in one inspection). The Java schema is
  single-image per inspection today (`Inspection.imageUrl`); this service's contract mirrors
  that. See `docs/IMPLEMENTATION_AUDIT.md` §4 for the schema change this would require.
- **Physical/statistical tests** (Rules 19-23, First/Fifth/Sixth/Seventh Schedule). Correctly
  out of scope for a camera; catalogued, not automated.
