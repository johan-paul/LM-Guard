package com.lmguard.dto.inspection;

import com.lmguard.entity.enums.InspectionType;
import com.lmguard.entity.enums.Priority;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

/**
 * Opens an inspection. There are two entry points into the same model and the same six-step
 * workflow that follows - which one applies is decided entirely by whether {@code inspectorId}
 * is present, not by role:
 *
 * <ul>
 *   <li><b>Inspector, directly</b>: opens their own inspection. {@code inspectorId} is omitted;
 *       the inspector becomes {@code inspector} on the record; no admin involvement, no
 *       {@code assignedBy}, no assignment-history row.</li>
 *   <li><b>Admin, assigning</b>: opens a case on behalf of a specific inspector via
 *       {@code inspectorId} (ADMIN role required - rejected with 403 otherwise). Writes
 *       {@code assignedBy}/{@code assignedAt} and an assignment-history row.</li>
 * </ul>
 *
 * <p>{@code zoneId} is independent of which of those two this is - either an inspector tagging
 * their own inspection's zone, or an admin setting the zone for a case they're assigning, and
 * needs no special authorisation either way.
 *
 * <p>{@code productId}/{@code productName} may both be omitted for either entry point: the
 * six-step workflow identifies the product as its own step, not necessarily at creation time.
 */
@Schema(name = "InspectionCreateRequest", description = "Open a new inspection")
public record InspectionCreateRequest(

        @Schema(description = "Existing product to inspect. Omit to create one from the fields below, "
                + "or omit entirely for an admin-opened case with no product identified yet.")
        UUID productId,

        @Schema(description = "Name for a product created inline.", example = "Classic Salted Chips")
        @Size(max = 255, message = "productName must be at most 255 characters")
        String productName,

        @Schema(description = "Brand for a product created inline", example = "ABC Foods")
        @Size(max = 255, message = "brand must be at most 255 characters")
        String brand,

        @Schema(description = "Category for a product created inline", example = "PACKAGED_FOOD")
        @Size(max = 100, message = "category must be at most 100 characters")
        String category,

        @Schema(description = "Barcode for a product created inline", example = "8901234567890")
        @Size(max = 100, message = "barcode must be at most 100 characters")
        String barcode,

        @Schema(description = "Free-text inspector notes")
        String notes,

        @Schema(description = """
                ADMIN only: assign this inspection to a specific inspector instead of the
                caller. Presence of this field is what makes the call an admin assignment;
                rejected with 403 if the caller is not an ADMIN.
                """)
        UUID inspectorId,

        @Schema(description = "The zone this inspection belongs to. Any caller may set this - "
                + "an inspector tagging their own inspection, or an admin assigning one.")
        UUID zoneId,

        @Schema(description = "Establishment/premises being inspected", example = "SunFresh Retail Outlet")
        @Size(max = 255, message = "establishment must be at most 255 characters")
        String establishment,

        @Schema(description = "Inspection address")
        String address,

        @Schema(description = "Why this inspection was opened", example = "ROUTINE")
        InspectionType inspectionType,

        @Schema(description = "Urgency", example = "MEDIUM")
        Priority priority,

        @Schema(description = "When this inspection is due")
        Instant dueDate
) {

    public boolean createsProductInline() {
        return productId == null;
    }

    /**
     * True only when the caller is naming a *different* inspector to do this work - the signal
     * for the ADMIN-assignment entry point. {@code zoneId} alone does not imply this: an
     * inspector tagging their own inspection's zone is not an assignment.
     */
    public boolean requestsAssignment() {
        return inspectorId != null;
    }

    /** No product reference at all - valid for either entry point; the six-step workflow
     * identifies the product as its own step, not necessarily at creation time. */
    public boolean hasNoProduct() {
        return productId == null && (productName == null || productName.isBlank());
    }
}
