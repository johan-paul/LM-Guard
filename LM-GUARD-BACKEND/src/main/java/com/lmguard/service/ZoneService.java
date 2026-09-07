package com.lmguard.service;

import com.lmguard.entity.Zone;
import com.lmguard.exception.ErrorCode;
import com.lmguard.exception.ResourceNotFoundException;
import com.lmguard.repository.ZoneRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Zone reference data. Zones are seeded on startup (see {@link DemoZoneSeeder}); there is
 * no create/edit UI for them yet, so this service is read-only by design. */
@Service
@RequiredArgsConstructor
public class ZoneService {

    private final ZoneRepository zoneRepository;

    @Transactional(readOnly = true)
    public List<Zone> listAll() {
        return zoneRepository.findAllOrderByName();
    }

    @Transactional(readOnly = true)
    public Zone requireById(UUID id) {
        return zoneRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of(ErrorCode.ZONE_NOT_FOUND, id));
    }

    @Transactional(readOnly = true)
    public Zone requireByName(String name) {
        return zoneRepository.findByNameIgnoreCase(name)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.ZONE_NOT_FOUND,
                        ErrorCode.ZONE_NOT_FOUND.getDefaultMessage() + ": " + name));
    }
}
