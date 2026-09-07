"""VLM extraction logic with the Gemini client mocked -- no network, no API key needed.
Covers the parts that matter most: graceful degradation when no key is configured, that a
model claim not backed by the OCR text is not trusted at full confidence (see vlm.py's
`_grounded_in_ocr`), and that a malformed/failed response never becomes a fabricated fact."""
import json
from types import SimpleNamespace
from unittest.mock import MagicMock, patch

import httpx
from google.genai import errors

from app.config import settings
from app.vlm import extract_fields


def test_returns_empty_with_error_when_no_api_key(monkeypatch):
    monkeypatch.setattr(settings, "gemini_api_key", "")

    fields, error = extract_fields(b"fake-image-bytes", "MRP Rs.99", ["MRP"])

    assert fields == []
    assert error is not None


def test_returns_empty_with_error_when_vlm_disabled(monkeypatch):
    monkeypatch.setattr(settings, "gemini_api_key", "fake-key")
    monkeypatch.setattr(settings, "enable_vlm", False)

    fields, error = extract_fields(b"fake-image-bytes", "MRP Rs.99", ["MRP"])

    assert fields == []
    assert error is not None


def _fake_gemini_response(fields_payload):
    return SimpleNamespace(text=json.dumps({"fields": fields_payload}))


def _fake_client(response):
    client = MagicMock()
    client.models.generate_content.return_value = response
    return client


def test_grounded_value_keeps_full_confidence(monkeypatch):
    monkeypatch.setattr(settings, "gemini_api_key", "fake-key")
    monkeypatch.setattr(settings, "enable_vlm", True)

    response = _fake_gemini_response([
        {"name": "MRP", "value": "99", "confidence": 0.95, "quoted_text": "Rs. 99.00"},
    ])

    with patch("google.genai.Client", return_value=_fake_client(response)):
        fields, error = extract_fields(b"fake-image-bytes", "MRP Rs. 99.00\nNet Qty 500 g", ["MRP"])

    assert error is None
    assert fields[0].value == "99"
    assert fields[0].confidence == 0.95
    assert fields[0].grounded is True


def test_ungrounded_value_is_capped_not_discarded(monkeypatch):
    monkeypatch.setattr(settings, "gemini_api_key", "fake-key")
    monkeypatch.setattr(settings, "enable_vlm", True)

    response = _fake_gemini_response([
        {"name": "MANUFACTURER", "value": "Totally Invented Foods Inc",
         "confidence": 0.99, "quoted_text": "text that does not appear in the OCR corpus"},
    ])

    with patch("google.genai.Client", return_value=_fake_client(response)):
        fields, error = extract_fields(b"fake-image-bytes", "MRP Rs. 99.00\nNet Qty 500 g", ["MANUFACTURER"])

    assert error is None
    assert fields[0].value == "Totally Invented Foods Inc"  # not discarded outright
    assert fields[0].grounded is False
    assert fields[0].confidence <= 0.5  # but not trusted at the model's own high confidence


def test_absence_claim_needs_no_grounding(monkeypatch):
    monkeypatch.setattr(settings, "gemini_api_key", "fake-key")
    monkeypatch.setattr(settings, "enable_vlm", True)

    response = _fake_gemini_response([
        {"name": "EXPIRY_DATE", "value": None, "confidence": 0.88, "quoted_text": None},
    ])

    with patch("google.genai.Client", return_value=_fake_client(response)):
        fields, error = extract_fields(b"fake-image-bytes", "MRP Rs. 99.00", ["EXPIRY_DATE"])

    assert error is None
    assert fields[0].value is None
    assert fields[0].confidence == 0.88


def test_api_failure_degrades_instead_of_raising(monkeypatch):
    monkeypatch.setattr(settings, "gemini_api_key", "fake-key")
    monkeypatch.setattr(settings, "enable_vlm", True)

    fake_client = MagicMock()
    fake_client.models.generate_content.side_effect = RuntimeError("simulated API outage")

    with patch("google.genai.Client", return_value=fake_client):
        fields, error = extract_fields(b"fake-image-bytes", "MRP Rs. 99.00", ["MRP"])

    assert fields == []
    assert "simulated API outage" in error


def test_empty_response_text_degrades_instead_of_raising(monkeypatch):
    monkeypatch.setattr(settings, "gemini_api_key", "fake-key")
    monkeypatch.setattr(settings, "enable_vlm", True)

    response = SimpleNamespace(text="")

    with patch("google.genai.Client", return_value=_fake_client(response)):
        fields, error = extract_fields(b"fake-image-bytes", "MRP Rs. 99.00", ["MRP"])

    assert fields == []
    assert error is not None


def test_malformed_json_response_is_rejected_not_hallucinated(monkeypatch):
    monkeypatch.setattr(settings, "gemini_api_key", "fake-key")
    monkeypatch.setattr(settings, "enable_vlm", True)

    response = SimpleNamespace(text="this is not valid JSON {{{")

    with patch("google.genai.Client", return_value=_fake_client(response)):
        fields, error = extract_fields(b"fake-image-bytes", "MRP Rs. 99.00", ["MRP"])

    assert fields == []
    assert "malformed" in error.lower()


def _server_error(status=503, message="high demand"):
    return errors.ServerError(status, {"error": {"code": status, "message": message, "status": "UNAVAILABLE"}})


def test_retries_and_succeeds_after_a_transient_timeout(monkeypatch):
    monkeypatch.setattr(settings, "gemini_api_key", "fake-key")
    monkeypatch.setattr(settings, "enable_vlm", True)
    monkeypatch.setattr("app.vlm.time.sleep", lambda seconds: None)  # no real waiting in tests

    response = _fake_gemini_response([
        {"name": "MRP", "value": "99", "confidence": 0.95, "quoted_text": "Rs. 99.00"},
    ])
    fake_client = MagicMock()
    fake_client.models.generate_content.side_effect = [
        httpx.ReadTimeout("simulated read timeout"),
        response,
    ]

    with patch("google.genai.Client", return_value=fake_client):
        fields, error = extract_fields(b"fake-image-bytes", "Rs. 99.00", ["MRP"])

    assert error is None
    assert fields[0].value == "99"
    assert fake_client.models.generate_content.call_count == 2  # 1 failure + 1 successful retry


def test_retries_and_succeeds_after_a_transient_503(monkeypatch):
    monkeypatch.setattr(settings, "gemini_api_key", "fake-key")
    monkeypatch.setattr(settings, "enable_vlm", True)
    monkeypatch.setattr("app.vlm.time.sleep", lambda seconds: None)

    response = _fake_gemini_response([
        {"name": "MRP", "value": "99", "confidence": 0.95, "quoted_text": "Rs. 99.00"},
    ])
    fake_client = MagicMock()
    fake_client.models.generate_content.side_effect = [_server_error(), response]

    with patch("google.genai.Client", return_value=fake_client):
        fields, error = extract_fields(b"fake-image-bytes", "Rs. 99.00", ["MRP"])

    assert error is None
    assert fields[0].value == "99"
    assert fake_client.models.generate_content.call_count == 2


def test_gives_up_after_exhausting_retries_and_degrades(monkeypatch):
    monkeypatch.setattr(settings, "gemini_api_key", "fake-key")
    monkeypatch.setattr(settings, "enable_vlm", True)
    monkeypatch.setattr("app.vlm.time.sleep", lambda seconds: None)

    fake_client = MagicMock()
    # Persistently transient: every attempt times out. Must stop retrying eventually.
    fake_client.models.generate_content.side_effect = httpx.ReadTimeout("simulated read timeout")

    with patch("google.genai.Client", return_value=fake_client):
        fields, error = extract_fields(b"fake-image-bytes", "Rs. 99.00", ["MRP"])

    assert fields == []
    assert "simulated read timeout" in error
    # 1 initial attempt + at most 2 retries = at most 3 calls, never unbounded.
    assert fake_client.models.generate_content.call_count == 3


def test_permanent_client_error_is_not_retried(monkeypatch):
    monkeypatch.setattr(settings, "gemini_api_key", "fake-key")
    monkeypatch.setattr(settings, "enable_vlm", True)
    monkeypatch.setattr("app.vlm.time.sleep", lambda seconds: None)

    fake_client = MagicMock()
    fake_client.models.generate_content.side_effect = errors.ClientError(
        404, {"error": {"code": 404, "message": "model not found", "status": "NOT_FOUND"}})

    with patch("google.genai.Client", return_value=fake_client):
        fields, error = extract_fields(b"fake-image-bytes", "Rs. 99.00", ["MRP"])

    assert fields == []
    assert "model not found" in error
    # A permanent (4xx) failure must not burn the retry budget -- it will never succeed.
    assert fake_client.models.generate_content.call_count == 1


def test_confidence_is_clamped_to_0_1(monkeypatch):
    monkeypatch.setattr(settings, "gemini_api_key", "fake-key")
    monkeypatch.setattr(settings, "enable_vlm", True)

    response = _fake_gemini_response([
        {"name": "MRP", "value": "99", "confidence": 1.7, "quoted_text": "Rs. 99.00"},
    ])

    with patch("google.genai.Client", return_value=_fake_client(response)):
        fields, error = extract_fields(b"fake-image-bytes", "Rs. 99.00", ["MRP"])

    assert error is None
    assert fields[0].confidence == 1.0
