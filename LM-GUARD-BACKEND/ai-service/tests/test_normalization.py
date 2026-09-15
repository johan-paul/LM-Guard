"""Every example here is a real, plausible OCR reading of an Indian retail package -- these
are the exact "MRP ₹99/-" / "MRP Rs.99" / "Net Qty 500g" style variants the brief calls out."""
from app.normalization import (
    detect_scripts,
    normalize_batch_number,
    normalize_consumer_care,
    normalize_date,
    normalize_expiry_date,
    normalize_manufacture_date,
    normalize_mrp,
    normalize_quantity,
    try_regex_extract,
)


class TestMrpNormalization:
    def test_rupee_symbol_with_trailing_slash(self):
        result = normalize_mrp("MRP ₹99/-")
        assert result.normalized == "99"
        assert result.unit == "INR"
        assert result.pattern_confidence > 0

    def test_rs_dot_form(self):
        assert normalize_mrp("MRP Rs.99").normalized == "99"

    def test_rs_with_colon_and_space(self):
        assert normalize_mrp("MRP: Rs 99").normalized == "99"

    def test_full_stops_in_mrp_abbreviation(self):
        assert normalize_mrp("M.R.P. ₹ 99").normalized == "99"

    def test_decimal_amount(self):
        assert normalize_mrp("Rs. 149.50").normalized == "149.50"

    def test_comma_decimal_is_normalized_to_dot(self):
        result = normalize_mrp("Rs. 149,50")
        assert result.normalized == "149.50"

    def test_bare_mrp_with_no_currency_symbol_is_lower_confidence(self):
        result = normalize_mrp("MRP 99")
        assert result.normalized == "99"
        symbol_result = normalize_mrp("MRP Rs. 99")
        assert result.pattern_confidence < symbol_result.pattern_confidence

    def test_no_match_returns_none_not_a_guess(self):
        result = normalize_mrp("Best before 12 months from packing")
        assert result.normalized is None
        assert result.pattern_confidence == 0.0


class TestQuantityNormalization:
    def test_grams(self):
        assert normalize_quantity("Net Qty 500g").normalized == "500 g"

    def test_decimal_kilograms_with_abbreviation(self):
        result = normalize_quantity("Net Wt. 0.5 kg")
        assert result.normalized == "0.5 kg"

    def test_explicit_net_quantity_label(self):
        assert normalize_quantity("Net Quantity: 500 g").normalized == "500 g"

    def test_millilitres(self):
        assert normalize_quantity("250 ml").normalized == "250 ml"

    def test_number_symbol_unit(self):
        assert normalize_quantity("Contains 12 N").normalized == "12 N"

    def test_no_unit_no_match(self):
        assert normalize_quantity("Serves 4").normalized is None


class TestDateNormalization:
    def test_slash_month_year(self):
        assert normalize_date("MFD 03/2026").normalized == "03/2026"

    def test_month_name_year(self):
        result = normalize_date("Manufactured Mar 2026")
        assert result.normalized is not None
        assert "2026" in result.normalized

    def test_year_first_form(self):
        assert normalize_date("2026-03").normalized == "2026-03"

    def test_no_date_no_match(self):
        assert normalize_date("Store in a cool dry place").normalized is None


class TestManufactureVsExpiryDateDisambiguation:
    """This is the exact real bug found while smoke-testing the service end-to-end: a plain
    date regex cannot tell 'MFD 03/2026' from 'EXP 03/2028' apart, so it must not report the
    same date for both fields just because it appears once on the label."""

    def test_manufacture_date_on_a_line_labeled_mfd(self):
        assert normalize_manufacture_date("MFD 03/2026").normalized == "03/2026"

    def test_expiry_date_on_a_line_labeled_exp(self):
        assert normalize_expiry_date("EXP 03/2028").normalized == "03/2028"

    def test_manufacture_normalizer_refuses_a_line_explicitly_labeled_expiry(self):
        result = normalize_manufacture_date("Best before 03/2028")
        assert result.normalized is None

    def test_expiry_normalizer_refuses_a_line_explicitly_labeled_manufacture(self):
        result = normalize_expiry_date("Packed on 03/2026")
        assert result.normalized is None

    def test_unlabeled_date_is_usable_but_lower_confidence_for_either_field(self):
        mfg_result = normalize_manufacture_date("03/2026")
        exp_result = normalize_expiry_date("03/2026")
        assert mfg_result.normalized == "03/2026"
        assert exp_result.normalized == "03/2026"
        labeled = normalize_manufacture_date("MFD 03/2026")
        assert mfg_result.pattern_confidence < labeled.pattern_confidence

    def test_two_labeled_dates_on_the_same_package_are_kept_separate(self):
        text = "MFD 03/2026\nEXP 03/2028"
        assert normalize_manufacture_date(text.splitlines()[0]).normalized == "03/2026"
        assert normalize_expiry_date(text.splitlines()[1]).normalized == "03/2028"


class TestConsumerCareNormalization:
    def test_email_and_phone_both_present(self):
        result = normalize_consumer_care("care@abcfoods.example / 1800-123-456")
        assert "care@abcfoods.example" in result.normalized
        assert result.pattern_confidence == 0.9

    def test_email_only(self):
        result = normalize_consumer_care("Contact: support@brand.co.in")
        assert result.normalized == "support@brand.co.in"

    def test_toll_free_only(self):
        result = normalize_consumer_care("Call 1800 123 4567 for complaints")
        assert result.normalized is not None

    def test_mobile_number_only(self):
        result = normalize_consumer_care("Whatsapp 9876543210")
        assert result.normalized == "9876543210"

    def test_no_contact_info(self):
        assert normalize_consumer_care("Made in India").normalized is None


class TestBatchNumberNormalization:
    def test_batch_prefix(self):
        assert normalize_batch_number("Batch: AB12C3").normalized == "AB12C3"

    def test_lot_prefix(self):
        assert normalize_batch_number("Lot L-2026-09").normalized == "L-2026-09"


class TestRegexExtractDispatch:
    def test_scans_line_by_line_and_returns_first_hit(self):
        text = "Classic Salted Chips\nNet Qty 500 g\nMRP Rs. 99.00"
        result = try_regex_extract("NET_QUANTITY", text)
        assert result is not None
        assert result.normalized == "500 g"

    def test_unknown_field_has_no_normalizer(self):
        assert try_regex_extract("MANUFACTURER", "ABC Foods Pvt Ltd") is None

    def test_falls_back_to_whole_blob_when_no_single_line_matches(self):
        # Neither line alone matches (line 1 has no digits, line 2 has no currency marker);
        # only scanning the whole joined blob finds it -- exactly the multi-line OCR split
        # this fallback exists for.
        text = "Rs.\n99.00"
        result = try_regex_extract("MRP", text)
        assert result is not None
        assert result.normalized == "99.00"


class TestIndicDigitNormalization:
    """Every normalize_* function below matches against an ASCII-digit-translated copy of its
    input (see normalization.py::normalize_mrp's docstring for why this has to happen BEFORE
    matching, not just on the captured group afterwards -- the date pattern's day/month
    components use literal ASCII digit-class ranges, not `\\d`, so a native-digit date would
    never match at all otherwise). What's verified here is that native-script digits are read
    correctly end to end and the returned value is ASCII, which is what the Java rule engine's
    numeric comparisons need."""

    def test_devanagari_digits_in_mrp(self):
        # Devanagari 2=२ 9=९ -> "MRP ₹२९९" is a real MRP of 299.
        assert normalize_mrp("MRP ₹२९९").normalized == "299"

    def test_tamil_digits_in_quantity(self):
        # Tamil 5=௫ 0=௦ -> "௫௦௦ g" is 500 g.
        result = normalize_quantity("௫௦௦ g")
        assert result.normalized == "500 g"

    def test_devanagari_digits_in_manufacture_date(self):
        result = normalize_manufacture_date("निर्माण ०३/२०२६")
        assert result.normalized == "03/2026"
        assert result.pattern_confidence > 0

    def test_telugu_digits_in_mrp(self):
        # Telugu 4=౪ 9=౯ -> "౪౯" is 49.
        assert normalize_mrp("Rs. ౪౯").normalized == "49"

    def test_kannada_digits_in_mrp(self):
        # Kannada 7=೭ 5=೫ -> "೭೫" is 75.
        assert normalize_mrp("Rs. ೭೫").normalized == "75"

    def test_malayalam_digits_in_mrp(self):
        # Malayalam 1=൧ 2=൨ 0=൦ -> "൧൨൦" is 120.
        assert normalize_mrp("Rs. ൧൨൦").normalized == "120"


class TestIndicKeywords:
    """MFD/EXP disambiguation must work with the regional-language words actually printed on
    Indian packaging, not just the English abbreviations -- see normalization.py's
    _MANUFACTURE_KEYWORDS / _EXPIRY_KEYWORDS for why these are additive alternatives, tried
    regardless of any other script on the line."""

    def test_hindi_manufacture_keyword_is_recognised(self):
        result = normalize_manufacture_date("निर्माण तिथि 03/2026")
        assert result.normalized == "03/2026"
        assert result.pattern_confidence >= 0.85

    def test_hindi_expiry_keyword_is_recognised(self):
        result = normalize_expiry_date("समाप्ति तिथि 03/2028")
        assert result.normalized == "03/2028"

    def test_tamil_manufacture_keyword_is_recognised(self):
        result = normalize_manufacture_date("உற்பத்தி தேதி 03/2026")
        assert result.normalized == "03/2026"

    def test_telugu_expiry_keyword_is_recognised(self):
        result = normalize_expiry_date("గడువు తేదీ 03/2028")
        assert result.normalized == "03/2028"

    def test_kannada_manufacture_keyword_is_recognised(self):
        result = normalize_manufacture_date("ತಯಾರಿಕಾ ದಿನಾಂಕ 03/2026")
        assert result.normalized == "03/2026"

    def test_malayalam_expiry_keyword_is_recognised(self):
        result = normalize_expiry_date("കാലഹരണ തീയതി 03/2028")
        assert result.normalized == "03/2028"

    def test_a_line_explicitly_labeled_manufacture_is_refused_as_expiry(self):
        # Same disambiguation guarantee the English keywords already have (see
        # TestManufactureDateNormalization/TestExpiryDateNormalization above), now for Hindi.
        result = normalize_expiry_date("निर्माण तिथि 03/2026")
        assert result.normalized is None


class TestDetectScripts:
    def test_detects_devanagari(self):
        assert detect_scripts("निर्माण तिथि 03/2026") == {"devanagari"}

    def test_detects_multiple_scripts_on_a_mixed_line(self):
        # Common on real packaging: English MRP label next to a Tamil price.
        assert detect_scripts("MRP ரூ. 99") == {"tamil"}

    def test_pure_english_line_detects_nothing(self):
        assert detect_scripts("MRP Rs. 99.00") == set()
