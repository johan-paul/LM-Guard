package com.lmguard.dto.inspection;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/** Replaces the inspection's whole checklist in one call - the wizard edits several items
 * between navigations and saves/submits the set together, not one item at a time. */
public record ChecklistBulkUpsertRequest(

        @NotEmpty(message = "items must not be empty")
        @Valid
        List<ChecklistEntryRequest> items
) {
}
