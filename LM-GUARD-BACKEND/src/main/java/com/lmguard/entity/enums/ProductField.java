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

    /** Fields mirrored onto {@code product_versions} for change tracking. */
    public static final Set<String> VERSIONED_FIELDS =
            Set.of(MRP, NET_QUANTITY, MANUFACTURER, ORIGIN, CONSUMER_CARE);

    private ProductField() {
    }
}
