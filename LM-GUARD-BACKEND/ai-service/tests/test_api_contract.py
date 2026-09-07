"""Verifies /analyze matches com.lmguard.ai.dto.AiAnalyzeRequest / AiAnalyzeResponse exactly.

The pipeline itself (OCR/VLM/image fetch) is mocked here -- this file's job is the wire
contract and the failure-handling behaviour, not the vision pipeline (covered in
test_normalization.py, test_confidence.py, test_ocr_merge.py and
test_pipeline_integration.py).
"""
from fastapi.testclient import TestClient

from app.config import settings
from app.main import app
from app.schema import AnalyzeResponse, FieldOut

client = TestClient(app)


def test_health_endpoint():
    response = client.get("/health")
    assert response.status_code == 200
    assert response.json()["status"] == "ok"


def test_analyze_request_and_response_shape(monkeypatch):
    def fake_analyze(image_url, requested_fields):
        assert image_url == "http://localhost:8080/files/package-images/test.jpg"
        assert requested_fields == ["MRP", "NET_QUANTITY"]
        return AnalyzeResponse(
            modelVersion="test-1.0",
            warnings=["glare on lower panel"],
            fields=[
                FieldOut(name="MRP", value="99", confidence=0.97,
                         boundingBox={"x": 64, "y": 210, "width": 150, "height": 54}),
                FieldOut(name="NET_QUANTITY", value=None, confidence=0.91),
            ],
        )

    monkeypatch.setattr("app.main.analyze", fake_analyze)

    response = client.post("/analyze", json={
        "imageUrl": "http://localhost:8080/files/package-images/test.jpg",
        "inspectionId": "9344dcd9-0356-41fe-b4c2-af66eb22181e",
        "requestedFields": ["MRP", "NET_QUANTITY"],
    })

    assert response.status_code == 200
    body = response.json()
    assert body["modelVersion"] == "test-1.0"
    assert body["warnings"] == ["glare on lower panel"]
    assert body["fields"][0] == {
        "name": "MRP", "value": "99", "confidence": 0.97,
        "boundingBox": {"x": 64, "y": 210, "width": 150, "height": 54}, "rawText": None,
    }
    # A field the AI genuinely could not detect: null value, non-null confidence, no box.
    assert body["fields"][1]["value"] is None
    assert body["fields"][1]["boundingBox"] is None


def test_missing_image_url_is_a_400_not_a_500():
    response = client.post("/analyze", json={"imageUrl": "", "requestedFields": ["MRP"]})
    assert response.status_code == 400


def test_pipeline_exception_becomes_500_ai_processing_error_not_a_crash(monkeypatch):
    def boom(image_url, requested_fields):
        raise RuntimeError("simulated OCR engine crash")

    monkeypatch.setattr("app.main.analyze", boom)

    response = client.post("/analyze", json={
        "imageUrl": "http://example.test/x.jpg", "requestedFields": ["MRP"],
    })

    assert response.status_code == 500
    assert response.json()["detail"] == "AI_PROCESSING_ERROR"


def test_api_key_enforced_when_configured(monkeypatch):
    monkeypatch.setattr(settings, "api_key", "expected-secret")
    monkeypatch.setattr("app.main.analyze", lambda *_: AnalyzeResponse(modelVersion="t", fields=[]))

    unauthorized = client.post("/analyze", json={"imageUrl": "http://example.test/x.jpg"})
    assert unauthorized.status_code == 401

    authorized = client.post(
        "/analyze",
        json={"imageUrl": "http://example.test/x.jpg"},
        headers={"X-API-Key": "expected-secret"},
    )
    assert authorized.status_code == 200


def test_api_key_not_enforced_when_unset(monkeypatch):
    monkeypatch.setattr(settings, "api_key", "")
    monkeypatch.setattr("app.main.analyze", lambda *_: AnalyzeResponse(modelVersion="t", fields=[]))

    response = client.post("/analyze", json={"imageUrl": "http://example.test/x.jpg"})
    assert response.status_code == 200
