package com.lmguard.dto.rule;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "RulePublishRequest",
        description = "Clones every rule of an existing version into a brand-new version, so an "
                + "admin doesn't have to resupply every rule by hand just to change one of them. "
                + "The new version starts as an editable draft - it becomes immutable itself the "
                + "moment an inspection is judged under it.")
public record RulePublishRequest(

        @Schema(description = "Version to clone rules from", example = "LM-PC-2011-v1")
        @NotBlank(message = "sourceVersion is required")
        @Size(max = 50) String sourceVersion,

        @Schema(description = "New version identifier - must not already exist", example = "LM-PC-2011-v2")
        @NotBlank(message = "newVersion is required")
        @Size(max = 50) String newVersion
) {
}
