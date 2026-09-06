"""End-to-end tests against the REAL OCR engine (RapidOCR) on synthetic label images.

No network and no ANTHROPIC_API_KEY are needed: image fetch is mocked (a local file stands
in for the URL Java would normally give us) and the VLM step is left disabled, so these tests
prove the OCR + regex + confidence path genuinely works, degrading correctly with no VLM
available -- exactly the failure-handling behaviour docs/AI_PIPELINE.md describes.
"""
import cv2
import numpy as np
import pytest

from app import pipeline
from app.config import settings


def _label_image(lines: list[str]) -> np.ndarray:
    # Large, bold, well-spaced text: representative of a real printed label rather than a
    # stress test of OCR's font-rendering robustness, which is not what this test is for.
    img = np.full((100 + 110 * len(lines), 1100, 3), 255, dtype=np.uint8)
    for i, line in enumerate(lines):
        cv2.putText(img, line, (40, 100 + 110 * i), cv2.FONT_HERSHEY_DUPLEX, 1.8, (0, 0, 0), 3)
    return img


def _blurry(img: np.ndarray) -> np.ndarray:
    return cv2.GaussianBlur(img, (35, 35), 0)


@pytest.fixture(autouse=True)
def _no_vlm(monkeypatch):
    # These tests exercise the OCR/regex path specifically; the VLM path is covered by
    # unit tests in test_vlm.py with the anthropic client mocked (a real vision call would
    # need network + a paid API key, which this test suite must not depend on).
    monkeypatch.setattr(settings, "anthropic_api_key", "")


def test_reads_real_text_with_bounding_boxes_and_confidence(monkeypatch):
    image = _label_image(["MRP Rs. 99.00", "Net Qty 500 g", "care@abcfoods.example"])
    ok, encoded = cv2.imencode(".png", image)
    assert ok
    monkeypatch.setattr(pipeline, "fetch_image", lambda url: encoded.tobytes())

    result = pipeline.analyze("http://example.test/label.png", ["MRP", "NET_QUANTITY", "CONSUMER_CARE", "ORIGIN"])

    by_name = {f.name: f for f in result.fields}

    assert by_name["MRP"].value == "99.00"
    assert by_name["MRP"].confidence > 0
    assert by_name["MRP"].boundingBox is not None
    assert by_name["MRP"].boundingBox.width > 0

    assert by_name["NET_QUANTITY"].value == "500 g"

    assert by_name["CONSUMER_CARE"].value is not None
    assert "@" in by_name["CONSUMER_CARE"].value

    # ORIGIN has no reliable regex signature and the VLM is disabled in this test -- it must
    # come back as genuinely not detected, never guessed.
    assert by_name["ORIGIN"].value is None
    assert by_name["ORIGIN"].confidence == 0.0

    assert any("VLM" in w or "Semantic" in w for w in result.warnings)


def test_blank_image_is_caught_by_the_quality_gate_before_ocr_even_runs(monkeypatch):
    # A featureless white image scores as blurred, glare-affected AND low-contrast all at
    # once -- it should be rejected as too poor to analyse rather than OCR'd for nothing.
    blank = np.full((900, 900, 3), 255, dtype=np.uint8)
    ok, encoded = cv2.imencode(".png", blank)
    assert ok
    monkeypatch.setattr(pipeline, "fetch_image", lambda url: encoded.tobytes())

    result = pipeline.analyze("http://example.test/blank.png", ["MRP"])

    assert result.fields[0].value is None
    assert result.fields[0].confidence == 0.0
    assert any("too low" in w.lower() for w in result.warnings)


def test_textless_but_readable_image_reports_no_text_found(monkeypatch):
    # A moderate-quality image (passes the quality gate) with no text on it at all -- this is
    # the genuine "OCR read nothing" path, distinct from the quality gate above.
    image = np.full((900, 900, 3), 255, dtype=np.uint8)
    cv2.rectangle(image, (100, 100), (800, 800), (180, 180, 180), thickness=-1)
    cv2.rectangle(image, (300, 300), (600, 600), (60, 60, 60), thickness=-1)
    ok, encoded = cv2.imencode(".png", image)
    assert ok
    monkeypatch.setattr(pipeline, "fetch_image", lambda url: encoded.tobytes())
    monkeypatch.setattr(settings, "min_usable_quality", 0.0)  # isolate the "no text" path

    result = pipeline.analyze("http://example.test/shapes.png", ["MRP"])

    assert result.fields[0].value is None
    assert any("no text" in w.lower() for w in result.warnings)


def test_severely_blurred_image_is_reported_as_too_poor_to_analyse(monkeypatch):
    image = _label_image(["MRP Rs. 99.00"])
    very_blurry = _blurry(_blurry(_blurry(image)))
    ok, encoded = cv2.imencode(".png", very_blurry)
    assert ok
    monkeypatch.setattr(pipeline, "fetch_image", lambda url: encoded.tobytes())
    monkeypatch.setattr(settings, "min_usable_quality", 0.9)  # force the gate to trip

    result = pipeline.analyze("http://example.test/blurry.png", ["MRP"])

    assert result.fields[0].value is None
    assert any("too low" in w.lower() for w in result.warnings)


def test_unfetchable_image_degrades_to_not_detected_rather_than_raising(monkeypatch):
    def failing_fetch(url):
        raise ConnectionError("simulated network failure")

    monkeypatch.setattr(pipeline, "fetch_image", failing_fetch)

    result = pipeline.analyze("http://example.test/unreachable.png", ["MRP", "NET_QUANTITY"])

    assert all(f.value is None and f.confidence == 0.0 for f in result.fields)
    assert any("could not retrieve" in w.lower() for w in result.warnings)
