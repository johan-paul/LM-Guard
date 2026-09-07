package com.lmguard.controller;

import com.lmguard.common.ApiResponse;
import com.lmguard.dto.inspector.InspectorCreateRequest;
import com.lmguard.dto.inspector.InspectorResponse;
import com.lmguard.dto.inspector.InspectorStatusRequest;
import com.lmguard.dto.inspector.InspectorUpdateRequest;
import com.lmguard.dto.inspector.InspectorZoneRequest;
import com.lmguard.dto.zone.ZoneRosterResponse;
import com.lmguard.service.InspectorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Inspecting-officer directory management. ADMIN only - this is the console's Inspector
 * Management screen, not something an inspector uses about themselves.
 */
@RestController
@RequestMapping("/api/inspectors")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "9. Inspectors (ADMIN)", description = "Inspecting-officer directory, zones and workload")
public class InspectorController {

    private final InspectorService inspectorService;

    @GetMapping
    @Operation(summary = "List/search the inspector directory")
    public ResponseEntity<ApiResponse<List<InspectorResponse>>> list(
            @Parameter(description = "Free-text match on name, officer code, email or phone")
            @RequestParam(required = false) String search,
            @Parameter(description = "Exact zone name filter") @RequestParam(required = false) String zone,
            @Parameter(description = "ACTIVE, INACTIVE or ALL") @RequestParam(required = false) String status) {
        return ResponseEntity.ok(ApiResponse.success(inspectorService.list(search, zone, status)));
    }

    @GetMapping("/zones")
    @Operation(summary = "Zone roster", description = "Every zone with the officers posted to it and their workload.")
    public ResponseEntity<ApiResponse<List<ZoneRosterResponse>>> zoneRoster() {
        return ResponseEntity.ok(ApiResponse.success(inspectorService.zoneRoster()));
    }

    @GetMapping("/{officerCode}")
    @Operation(summary = "Get one inspector", description = "Includes their five most recent inspection records.")
    public ResponseEntity<ApiResponse<InspectorResponse>> get(@PathVariable String officerCode) {
        return ResponseEntity.ok(ApiResponse.success(inspectorService.getByCode(officerCode)));
    }

    @PostMapping
    @Operation(summary = "Add an inspector",
            description = """
                    Creates a real account (role INSPECTOR) with a generated password, since this
                    form collects no password of its own. The response's `temporaryPassword` is
                    shown once - hand it to the officer, it is never returned again.
                    """)
    public ResponseEntity<ApiResponse<InspectorResponse>> create(
            @Valid @RequestBody InspectorCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Inspector added", inspectorService.create(request)));
    }

    @PutMapping("/{officerCode}")
    @Operation(summary = "Edit inspector information")
    public ResponseEntity<ApiResponse<InspectorResponse>> update(
            @PathVariable String officerCode,
            @Valid @RequestBody InspectorUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Inspector updated", inspectorService.update(officerCode, request)));
    }

    @PostMapping("/{officerCode}/reset-password")
    @Operation(summary = "Reset an inspector's password",
            description = """
                    Issues a brand-new generated password and invalidates the old one
                    immediately. There was previously no way to recover access once the
                    one-time password shown at account creation was lost or forgotten - the
                    stored hash cannot be reversed, so this mints a fresh password rather than
                    revealing the old one. The response's `temporaryPassword` is shown once,
                    exactly like creation - hand it to the officer directly.
                    """)
    public ResponseEntity<ApiResponse<InspectorResponse>> resetPassword(@PathVariable String officerCode) {
        return ResponseEntity.ok(ApiResponse.success("Password reset", inspectorService.resetPassword(officerCode)));
    }

    @PatchMapping("/{officerCode}/status")
    @Operation(summary = "Activate or deactivate an inspector",
            description = "Deactivating disables the account outright - the officer loses field-app access immediately.")
    public ResponseEntity<ApiResponse<InspectorResponse>> setStatus(
            @PathVariable String officerCode,
            @Valid @RequestBody InspectorStatusRequest request) {
        return ResponseEntity.ok(ApiResponse.success(inspectorService.setStatus(officerCode, request.status())));
    }

    @PatchMapping("/{officerCode}/zone")
    @Operation(summary = "Change an inspector's posted zone")
    public ResponseEntity<ApiResponse<InspectorResponse>> setZone(
            @PathVariable String officerCode,
            @Valid @RequestBody InspectorZoneRequest request) {
        return ResponseEntity.ok(ApiResponse.success(inspectorService.setZone(officerCode, request.zone())));
    }
}
