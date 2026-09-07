package com.lmguard.controller;

import com.lmguard.common.ApiResponse;
import com.lmguard.dto.zone.ZoneResponse;
import com.lmguard.mapper.ZoneMapper;
import com.lmguard.service.ZoneService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/zones")
@RequiredArgsConstructor
@Tag(name = "8. Zones", description = "Legal Metrology zones/jurisdictions")
public class ZoneController {

    private final ZoneService zoneService;
    private final ZoneMapper zoneMapper;

    @GetMapping
    @Operation(summary = "List zones", description = "Reference data for zone selectors. Seeded on startup.")
    public ResponseEntity<ApiResponse<List<ZoneResponse>>> list() {
        List<ZoneResponse> zones = zoneService.listAll().stream().map(zoneMapper::toResponse).toList();
        return ResponseEntity.ok(ApiResponse.success(zones));
    }
}
