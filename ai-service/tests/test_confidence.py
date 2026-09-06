from app.confidence import ConfidenceInputs, fuse


def test_no_signals_at_all_is_zero_confidence():
    confidence, signals = fuse(ConfidenceInputs())
    assert confidence == 0.0
    assert signals == []


def test_all_three_signals_agreeing_gives_high_confidence():
    confidence, _ = fuse(ConfidenceInputs(
        ocr_confidence=0.95, vlm_confidence=0.96, pattern_confidence=0.9, multi_pass_agreement=True,
    ))
    assert confidence >= 0.9


def test_single_weak_ocr_signal_stays_low():
    confidence, _ = fuse(ConfidenceInputs(ocr_confidence=0.3))
    assert confidence < 0.4


def test_vlm_only_claim_is_capped_even_if_the_model_is_very_confident():
    # No OCR text and no pattern validation backing it -- a lone VLM claim, however confident
    # the model says it is, must not be treated as fully corroborated evidence.
    confidence, _ = fuse(ConfidenceInputs(vlm_confidence=0.99))
    assert confidence <= 0.65


def test_multi_pass_agreement_gives_a_small_bounded_boost():
    without_boost, _ = fuse(ConfidenceInputs(ocr_confidence=0.8))
    with_boost, signals = fuse(ConfidenceInputs(ocr_confidence=0.8, multi_pass_agreement=True))
    assert with_boost > without_boost
    assert with_boost - without_boost <= 0.06
    assert "multi-pass agreement" in signals


def test_confidence_never_exceeds_one_or_drops_below_zero():
    high, _ = fuse(ConfidenceInputs(ocr_confidence=1.0, vlm_confidence=1.0, pattern_confidence=1.0,
                                     multi_pass_agreement=True))
    assert high <= 1.0
    low, _ = fuse(ConfidenceInputs(ocr_confidence=0.0))
    assert low >= 0.0
