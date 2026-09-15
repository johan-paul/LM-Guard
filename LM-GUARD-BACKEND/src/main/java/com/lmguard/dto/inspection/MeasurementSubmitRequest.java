package com.lmguard.dto.inspection;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;

/**
 * A physical measurement an inspector captures directly (currently only numeral height via AR
 * depth measurement), independent of the AI vision pipeline. {@code fieldName} must be one of
 * {@code ProductField.INSPECTOR_MEASURED_FIELDS} - this endpoint is deliberately narrow, not a
 * generic field-override backdoor.
 */
public record MeasurementSubmitRequest(

        @NotBlank(message = "fieldName is required")
        String fieldName,

        @NotBlank(message = "value is required")
        String value,

        @DecimalMin(value = "0.0", message = "confidence must be between 0 and 1")
        @DecimalMax(value = "1.0", message = "confidence must be between 0 and 1")
        Double confidence
) {
}
