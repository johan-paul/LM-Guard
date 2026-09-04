package com.lmguard.rules;

import com.lmguard.ai.BoundingBox;

/**
 * One observed declaration, in the form the rule engine consumes.
 *
 * @param value      observed text, or null when the declaration was reported absent
 * @param confidence confidence in the observation (or in the absence), 0..1
 * @param box        where on the image it was read
 */
public record FactValue(String value, double confidence, BoundingBox box) {

    public boolean isPresent() {
        return value != null && !value.isBlank();
    }

    public String trimmed() {
        return value == null ? null : value.trim();
    }
}
