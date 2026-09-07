package com.lmguard.dto.inspection;

import com.lmguard.entity.enums.CheckResult;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ChecklistEntryRequest(

        @NotBlank(message = "itemCode is required")
        @Size(max = 50, message = "itemCode must be at most 50 characters")
        String itemCode,

        @NotBlank(message = "title is required")
        @Size(max = 255, message = "title must be at most 255 characters")
        String title,

        String guidance,

        @NotNull(message = "result is required")
        CheckResult result,

        String note
) {
}
