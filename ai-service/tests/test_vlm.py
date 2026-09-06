"""VLM extraction logic with the anthropic client mocked -- no network, no API key needed.
Covers the parts that matter most: graceful degradation when no key is configured, and that a
model claim not backed by the OCR text is not trusted at full confidence (see vlm.py's
`_grounded_in_ocr`)."""
from types import SimpleNamespace
from unittest.mock import MagicMock, patch

from app.config import settings
from app.vlm import extract_fields


def test_returns_empty_with_error_when_no_api_key(monkeypatch):
    monkeypatch.setattr(settings, "anthropic_api_key", "")

    fields, error = extract_fields(b"fake-image-bytes", "MRP Rs.99", ["MRP"])

    assert fields == []
    assert error is not None


def test_returns_empty_with_error_when_vlm_disabled(monkeypatch):
    monkeypatch.setattr(settings, "anthropic_api_key", "sk-fake")
    monkeypatch.setattr(settings, "enable_vlm", False)

    fields, error = extract_fields(b"fake-image-bytes", "MRP Rs.99", ["MRP"])

    assert fields == []
    assert error is not None


def _fake_tool_use_response(fields_payload):
    tool_use_block = SimpleNamespace(type="tool_use", input={"fields": fields_payload})
    return SimpleNamespace(content=[tool_use_block])


def test_grounded_value_keeps_full_confidence(monkeypatch):
    monkeypatch.setattr(settings, "anthropic_api_key", "sk-fake")
    monkeypatch.setattr(settings, "enable_vlm", True)

    fake_client = MagicMock()
    fake_client.messages.create.return_value = _fake_tool_use_response([
        {"name": "MRP", "value": "99", "confidence": 0.95, "quoted_text": "Rs. 99.00"},
    ])

    with patch("anthropic.Anthropic", return_value=fake_client):
        fields, error = extract_fields(b"fake-image-bytes", "MRP Rs. 99.00\nNet Qty 500 g", ["MRP"])

    assert error is None
    assert fields[0].value == "99"
    assert fields[0].confidence == 0.95
    assert fields[0].grounded is True


def test_ungrounded_value_is_capped_not_discarded(monkeypatch):
    monkeypatch.setattr(settings, "anthropic_api_key", "sk-fake")
    monkeypatch.setattr(settings, "enable_vlm", True)

    fake_client = MagicMock()
    fake_client.messages.create.return_value = _fake_tool_use_response([
        {"name": "MANUFACTURER", "value": "Totally Invented Foods Inc",
         "confidence": 0.99, "quoted_text": "text that does not appear in the OCR corpus"},
    ])

    with patch("anthropic.Anthropic", return_value=fake_client):
        fields, error = extract_fields(b"fake-image-bytes", "MRP Rs. 99.00\nNet Qty 500 g", ["MANUFACTURER"])

    assert error is None
    assert fields[0].value == "Totally Invented Foods Inc"  # not discarded outright
    assert fields[0].grounded is False
    assert fields[0].confidence <= 0.5  # but not trusted at the model's own high confidence


def test_absence_claim_needs_no_grounding(monkeypatch):
    monkeypatch.setattr(settings, "anthropic_api_key", "sk-fake")
    monkeypatch.setattr(settings, "enable_vlm", True)

    fake_client = MagicMock()
    fake_client.messages.create.return_value = _fake_tool_use_response([
        {"name": "EXPIRY_DATE", "value": None, "confidence": 0.88, "quoted_text": None},
    ])

    with patch("anthropic.Anthropic", return_value=fake_client):
        fields, error = extract_fields(b"fake-image-bytes", "MRP Rs. 99.00", ["EXPIRY_DATE"])

    assert error is None
    assert fields[0].value is None
    assert fields[0].confidence == 0.88


def test_api_failure_degrades_instead_of_raising(monkeypatch):
    monkeypatch.setattr(settings, "anthropic_api_key", "sk-fake")
    monkeypatch.setattr(settings, "enable_vlm", True)

    fake_client = MagicMock()
    fake_client.messages.create.side_effect = RuntimeError("simulated API outage")

    with patch("anthropic.Anthropic", return_value=fake_client):
        fields, error = extract_fields(b"fake-image-bytes", "MRP Rs. 99.00", ["MRP"])

    assert fields == []
    assert "simulated API outage" in error
