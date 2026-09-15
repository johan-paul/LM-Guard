from app.ocr import OcrBlock, merge_passes


def block(text, confidence, x=0, y=0, w=100, h=20, pass_name="original", source="rapidocr"):
    return OcrBlock(text=text, confidence=confidence, x=x, y=y, width=w, height=h,
                     pass_name=pass_name, source=source)


def test_same_region_detected_twice_keeps_the_higher_confidence_reading():
    original = [block("MRP Rs.99", 0.6, x=10, y=10)]
    enhanced = [block("MRP Rs.99", 0.92, x=11, y=10)]  # near-identical box, better reading

    merged = merge_passes(original, enhanced)

    assert len(merged) == 1
    assert merged[0].confidence == 0.92


def test_region_seen_in_only_one_pass_is_kept():
    original = [block("Net Qty 500 g", 0.8, x=10, y=50)]
    enhanced = [block("MRP Rs.99", 0.9, x=10, y=10)]

    merged = merge_passes(original, enhanced)

    assert {b.text for b in merged} == {"Net Qty 500 g", "MRP Rs.99"}


def test_overlapping_boxes_with_different_text_are_not_merged():
    # Different text in an overlapping region -- e.g. one pass misread it -- both are kept
    # rather than one silently discarding the other's (possibly correct) reading.
    original = [block("MRP Rs.99", 0.5, x=10, y=10)]
    enhanced = [block("MRP Rs.9 9", 0.6, x=11, y=10)]  # misread by the enhanced pass

    merged = merge_passes(original, enhanced)

    assert len(merged) == 2


def test_empty_passes_produce_no_blocks():
    assert merge_passes([], []) == []


def test_same_region_read_by_two_engines_keeps_both_never_cross_merged():
    # RapidOCR (English) and Tesseract (Indic) reading the same physical region will almost
    # never produce identical text, but even when they coincidentally do, they must never be
    # merged against each other -- a real Tesseract reading of Devanagari text must not be
    # discarded in favor of a RapidOCR misread of the same region just because IoU is high.
    rapidocr_block = [block("MRP Rs.99", 0.4, x=10, y=10, source="rapidocr")]
    tesseract_block = [block("MRP Rs.99", 0.9, x=10, y=10, source="tesseract")]

    merged = merge_passes(rapidocr_block, tesseract_block)

    assert len(merged) == 2
    assert {b.source for b in merged} == {"rapidocr", "tesseract"}


def test_same_source_duplicate_still_collapses_to_higher_confidence():
    original = [block("MRP Rs.99", 0.5, x=10, y=10, source="tesseract")]
    enhanced = [block("MRP Rs.99", 0.85, x=11, y=10, source="tesseract")]

    merged = merge_passes(original, enhanced)

    assert len(merged) == 1
    assert merged[0].confidence == 0.85
