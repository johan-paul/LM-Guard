package com.lmguard.service;

import com.lmguard.entity.Zone;
import com.lmguard.repository.ZoneRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Seeds the Legal Metrology zones the admin console's zone selectors and Inspector Management
 * screen expect. Without this, a fresh database has nowhere to post an inspector to and no
 * dropdown to assign an inspection's zone from.
 *
 * <p>Idempotent by name; runs before {@link DemoUserSeeder} (see {@code @Order}) since
 * inspector accounts reference these zones.
 */
@Component
@Order(1)
@ConditionalOnProperty(name = "lmguard.demo.seed-demo-zones", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class DemoZoneSeeder implements ApplicationRunner {

    private final ZoneRepository zoneRepository;

    private record SeedZone(String name, String code, String office, String district) {
    }

    private static final List<SeedZone> ZONES = List.of(
            new SeedZone("Coimbatore North", "CBE-N", "Zonal Office, Gandhipuram", "Coimbatore"),
            new SeedZone("Coimbatore South", "CBE-S", "Zonal Office, Sundarapuram", "Coimbatore"),
            new SeedZone("Coimbatore West", "CBE-W", "Zonal Office, Thadagam Road", "Coimbatore"),
            new SeedZone("Tiruppur", "TUP", "District Office, Kumaran Road", "Tiruppur"),
            new SeedZone("Erode", "ERD", "District Office, Perundurai Road", "Erode")
    );

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int created = 0;
        for (SeedZone seed : ZONES) {
            if (zoneRepository.existsByNameIgnoreCase(seed.name())) {
                continue;
            }
            zoneRepository.save(Zone.builder()
                    .name(seed.name())
                    .code(seed.code())
                    .office(seed.office())
                    .district(seed.district())
                    .build());
            created++;
        }
        if (created > 0) {
            log.info("Seeded {} Legal Metrology zone(s)", created);
        }
    }
}
