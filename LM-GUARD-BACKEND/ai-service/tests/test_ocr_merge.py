from app.ocr import OcrBlock, merge_passes


def block(text, confidence, x=0, y=0, w=100, h=20, pass_name="original"):
    return OcrBlock(text=text, confidence=confidence, x=x, y=y, width=w, height=h, pass_name=pass_name)


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
