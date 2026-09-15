package com.lmguard.entity.enums;

import java.util.Set;

/**
 * Canonical field names used across the AI layer, the rule engine and the database.
 *
 * <p>Field names are stored as {@code VARCHAR} rather than a DB enum so an AI service can
 * report a declaration this backend does not know about yet without a migration. These
 * constants keep the known ones spelled consistently.
 */
public final class ProductField {

    public static final String MRP = "MRP";
    public static final String NET_QUANTITY = "NET_QUANTITY";
    public static final String MANUFACTURER = "MANUFACTURER";
    public static final String ORIGIN = "ORIGIN";
    public static final String CONSUMER_CARE = "CONSUMER_CARE";
    public static final String MANUFACTURE_DATE = "MANUFACTURE_DATE";
    public static final String EXPIRY_DATE = "EXPIRY_DATE";
    public static final String BATCH_NUMBER = "BATCH_NUMBER";
    public static final String COMMODITY_NAME = "COMMODITY_NAME";

    /** A visual observation, not a declaration - see docs on why this never gets a rule
     * (RuleCatalog/lm-pc-2011-rules.json has none for it): whether the panel looks torn,
     * folded, or obscured is a judgement call, and this codebase deliberately never lets the
     * AI decide a subjective compliance question on its own (see legal-rules/RULE_REVIEW.md).
     * Reported purely as an advisory hint the inspector can read before answering by hand. */
    public static final String PACKAGE_CONDITION = "PACKAGE_CONDITION";

    /** Minimum printed numeral height on the principal display panel, in millimetres (Rule
     * 7(2)-(3)) - unlike every field above, this is never reported by the AI/OCR pipeline. No
     * physical scale can be recovered from an ordinary photo without a calibration reference, so
     * this is captured separately by the inspector via an AR depth-measurement action
     * (see InspectionAnalysisService#submitMeasurement) and stored the same way, but through a
     * different path. */
    public static final String NUMERAL_HEIGHT_MM = "NUMERAL_HEIGHT_MM";

    /** Fields mirrored onto {@code product_versions} for change tracking. */
    public static final Set<String> VERSIONED_FIELDS =
            Set.of(MRP, NET_QUANTITY, MANUFACTURER, ORIGIN, CONSUMER_CARE);

    /** Fields an inspector submits directly (not derived from the AI vision pipeline), so
     * {@code persistExtractedFields}'s wipe-and-replace-from-fresh-analysis must never delete
     * them - a photo re-analysis has nothing to say about a physical measurement the inspector
     * took separately, and must not silently erase it. */
    public static final Set<String> INSPECTOR_MEASURED_FIELDS = Set.of(NUMERAL_HEIGHT_MM);

    private ProductField() {
    }
}
