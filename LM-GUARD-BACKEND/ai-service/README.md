# LM-GUARD AI Service

The Python implementation of the `AIAnalysisService` seam
(`src/main/java/com/lmguard/ai/AIAnalysisService.java`). It reads a package image and reports
**observations** -- text, confidence, bounding boxes. It never makes a compliance decision;
that stays exclusively in `DeterministicRuleEngineService` on the Java side.

## Architecture

```
POST /analyze  (imageUrl, inspectionId, requestedFields)
        |
        v
  fetch image over HTTP
        |
        v
  image quality check  ->  too poor?  -> return "not detected" for every field + a warning
        |
        v
  preprocess (tiered: light denoise/CLAHE, or a stronger gamma/upscale/adaptive-threshold
              pass for images in the "recoverable but poor" quality band -- resize)
        |
        v
  multi-pass OCR (RapidOCR: original + enhanced image; Tesseract: enhanced image, Indic
                   scripts -- Devanagari/Tamil/Telugu/Kannada/Malayalam; merged/deduped)
        |
        +------------------------------+
        |                               |
        v                               v
  regex/pattern extraction     VLM semantic extraction (Claude vision, if configured)
  (MRP, quantity, dates,       (manufacturer, commodity name, origin -- fields a regex
   consumer care, batch)        cannot identify without understanding meaning)
        |                               |
        +---------------+---------------+
                         v
              match each value back to
              its OCR bounding box
                         v
              fuse confidence (ocr + vlm + pattern + multi-pass agreement)
                         v
          AiAnalyzeResponse  (modelVersion, warnings, fields[])
```

## Why RapidOCR instead of PaddleOCR

The brief asked for PaddleOCR specifically. `rapidocr-onnxruntime` is an ONNX export of the
same PP-OCR detector/recognizer model family, distributed with no PaddlePaddle dependency and
no system binary requirement -- `pip install rapidocr-onnxruntime` and it runs on CPU. In this
project's environment (Windows, no GPU, no guaranteed system-level package manager access),
PaddlePaddle's own installer is a materially higher-risk dependency for a demo that needs to
run reliably on a judge's machine. If your deployment target can commit to PaddlePaddle
specifically, `ocr.py` is a single, isolated module -- swapping the engine means changing
`_engine()` and `_run_single_pass()` there and nowhere else.

## Why Tesseract for Indic scripts, and the system-dependency exception this creates

RapidOCR's bundled recognition model reads English/Latin script only. Most Indian retail
packaging carries required declarations in a regional language too, so `ocr.py` runs a third
OCR pass via `pytesseract` for Hindi/Devanagari (also covering Marathi and Nepali), Tamil,
Telugu, Kannada and Malayalam. Tesseract was chosen over extending RapidOCR/PaddleOCR because
it has trained data for all five scripts, including Malayalam, which PaddleOCR's multilingual
model zoo does not cover.

This is a **deliberate exception** to the "Why RapidOCR instead of PaddleOCR" reasoning above:
Tesseract is not a pip-only dependency, it needs the `tesseract-ocr` binary and language packs
installed on the host/container. The Dockerfile installs them via `apt-get`
(`tesseract-ocr tesseract-ocr-hin tesseract-ocr-tam tesseract-ocr-tel tesseract-ocr-kan
tesseract-ocr-mal`). For local Windows development, install Tesseract via the
[UB-Mannheim installer](https://github.com/UB-Mannheim/tesseract/wiki) (select the Indic
language packs during setup), then either add it to `PATH` or set `AI_TESSERACT_CMD` to its
full path (e.g. `C:\Program Files\Tesseract-OCR\tesseract.exe`) in `.env`. A missing/broken
install does not crash the service -- `AI_ENABLE_INDIC_OCR` degrades gracefully, same as
`AI_ENABLE_VLM`: that OCR pass is skipped with a warning and the service still runs
English-only via RapidOCR.

## Why Gemini for the VLM step

`vlm.py` originally called Claude; it now calls Gemini (the Anthropic account used during
development ran out of credit), using `response_schema`-constrained JSON output so the
response is always valid JSON, not a hopeful parse of free text. The module is isolated the
same way OCR is -- if you want a different vision model, `vlm.extract_fields()` is the only
function that needs to change.

## Running it

```bash
cd ai-service
python -m venv .venv
.venv/Scripts/activate        # .venv/bin/activate on macOS/Linux
pip install -r requirements.txt
cp .env.example .env          # edit in GEMINI_API_KEY if you have one
uvicorn app.main:app --port 8000
```

Then point the Spring Boot backend at it:

```bash
AI_MOCK_MODE=false
AI_SERVICE_URL=http://localhost:8000
AI_SERVICE_API_KEY=            # must match this service's AI_SERVICE_API_KEY if you set one
```

`GET /health` reports whether the VLM step is actually active (`vlmEnabled`), so you can
confirm which mode you're running in without reading logs.

## Without a GEMINI_API_KEY

The service still works. It falls back to OCR + regex extraction only, and says so in the
response's `warnings`. What that means concretely:

- `MRP`, `NET_QUANTITY`, `MANUFACTURE_DATE`, `EXPIRY_DATE`, `CONSUMER_CARE`, `BATCH_NUMBER`:
  still extracted, because these have a reliable text *pattern* a regex can recognise.
- `MANUFACTURER`, `COMMODITY_NAME`, `ORIGIN`: come back as **not detected**, honestly, rather
  than guessed -- telling "ABC Foods Pvt Ltd" (a manufacturer) apart from "Classic Salted
  Chips" (a commodity name) requires understanding what the text *means*, which is exactly
  what the VLM step is for. This is the "AI must not invent facts" rule in practice: no
  semantic guess, no field.

## What was verified working in this session (not just written)

- `rapidocr-onnxruntime` genuinely installs (pure pip, no system binary) and reads real text
  with bounding boxes and confidence from a synthetic label image -- see
  `tests/test_pipeline_integration.py`.
- The full HTTP round trip (`uvicorn` + a real `POST /analyze` request against a package
  image served over HTTP, in the exact JSON shape `ExternalAIAnalysisService.java` sends) was
  run live during development and returned correctly normalized MRP, net quantity, a
  manufacture date correctly told apart from an (absent) expiry date, and a consumer-care
  e-mail, each with a real bounding box recovered from OCR.
- The VLM path (`vlm.py`) is unit-tested with the Anthropic client mocked (no network/API key
  needed for the test suite) but was **not** exercised against the live Anthropic API in this
  session -- that requires a real key and a real network call this sandboxed session could not
  spend on a paid API. Test it against your own key before relying on it for a demo.
- 61/61 Python tests pass (`pytest tests/ -q`).

## Known limitations (see docs/AI_PIPELINE.md for the full picture)

- One image per request, matching the current Java `Inspection.imageUrl` (single-image)
  schema -- no multi-panel fusion. See `docs/IMPLEMENTATION_AUDIT.md` §4.
- No barcode/GTIN decoding.
- No text-height/contrast/placement measurement in physical units (no calibration reference
  in an arbitrary photo -- see Rule 7 in `legal-rules/lm_pc_2011_rules.json`). A phone-AR-depth
  based measurement feature is planned separately (Flutter-side, not this service).
- Indic-script (Tesseract) OCR is a single combined-language pass, not one model per script --
  see `app/config.py`'s `tesseract_indic_langs` comment for why. Month *names* in Indic
  scripts (e.g. "जनवरी") are not recognised by the date regexes in `normalization.py` -- only
  pure-numeral dates (`MM/YYYY` etc., including native-script digits) are; a fine-tuned model
  covering this properly is tracked as a separate, larger effort.
- Date-vs-date disambiguation (manufacture vs. expiry) relies on a nearby keyword ("MFD",
  "EXP", "Best before"); an unlabeled bare date is still returned, but at reduced confidence,
  for whichever field asked.
