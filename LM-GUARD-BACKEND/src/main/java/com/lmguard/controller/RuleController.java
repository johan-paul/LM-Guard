package com.lmguard.controller;

import com.lmguard.common.ApiResponse;
import com.lmguard.dto.rule.RulePublishRequest;
import com.lmguard.dto.rule.RuleResponse;
import com.lmguard.dto.rule.RuleSetResponse;
import com.lmguard.dto.rule.RuleUpsertRequest;
import com.lmguard.service.RuleAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/rules")
@RequiredArgsConstructor
@Tag(name = "7. Rules", description = "Versioned rule catalog. Reading is open to any authenticated "
        + "user (the six-step workflow's Findings step cites rules); managing rules is ADMIN only.")
public class RuleController {

    private final RuleAdminService ruleAdminService;

    @GetMapping
    @Operation(summary = "Read a ruleset",
            description = """
                    Returns the rules for a version, along with its `status` and `disclaimer`.
                    Any authenticated user may read this - an inspector citing a rule against a
                    finding needs the catalog, not just an admin managing it.

                    The ruleset shipped with this build is marked `SAMPLE`: demo scaffolding,
                    not the Legal Metrology Act or Rules, and not legally verified. The
                    disclaimer travels with the data on purpose.
                    """)
    public ResponseEntity<ApiResponse<RuleSetResponse>> ruleSet(
            @Parameter(description = "Ruleset version. Defaults to the configured active version.")
            @RequestParam(required = false) String version) {
        return ResponseEntity.ok(ApiResponse.success(ruleAdminService.activeRuleSet(version)));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create or amend a rule", description = """
                    ADMIN only. Upserts by (`ruleCode`, `version`).

                    To change how inspections are judged, publish a **new version** rather than
                    editing an active one: completed inspections reference the version they were
                    judged under, and that reference is what keeps a past decision reproducible.
                    """)
    public ResponseEntity<ApiResponse<RuleResponse>> upsert(@Valid @RequestBody RuleUpsertRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Rule saved", ruleAdminService.upsert(request)));
    }

    @PostMapping("/publish")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Publish a new ruleset version by cloning an existing one", description = """
                    ADMIN only. Copies every rule from `sourceVersion` into a brand-new
                    `newVersion`, which starts fully editable via `POST /api/rules` - amend
                    whichever rules actually changed, leave the rest as cloned. The new version
                    becomes immutable itself only once an inspection is judged under it.

                    This is the intended way to change how inspections are judged: publish a new
                    version rather than editing an active one, since completed inspections
                    reference the exact version they were judged under.
                    """)
    public ResponseEntity<ApiResponse<RuleSetResponse>> publish(@Valid @RequestBody RulePublishRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Ruleset version published", ruleAdminService.publish(request)));
    }

    @PatchMapping("/{id}/active")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Activate or deactivate a rule",
            description = "ADMIN only. Deactivated rules stop participating in evaluation but remain on record.")
    public ResponseEntity<ApiResponse<RuleResponse>> setActive(
            @PathVariable UUID id,
            @RequestParam boolean active) {
        return ResponseEntity.ok(ApiResponse.success("Rule updated", ruleAdminService.setActive(id, active)));
    }
}
