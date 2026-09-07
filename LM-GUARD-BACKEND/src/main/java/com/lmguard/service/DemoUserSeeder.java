package com.lmguard.service;

import com.lmguard.entity.InspectorProfile;
import com.lmguard.entity.User;
import com.lmguard.entity.Zone;
import com.lmguard.entity.enums.Role;
import com.lmguard.repository.InspectorProfileRepository;
import com.lmguard.repository.UserRepository;
import com.lmguard.repository.ZoneRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Seeds one administrator and the eleven inspecting officers the admin console originally
 * shipped as mock data - so the Inspector Management screen shows the same roster it always
 * has on first run against a real backend, just backed by real accounts (workload/records
 * figures correctly start at zero rather than the old fabricated numbers).
 *
 * <p>Only two passwords are worth publishing: {@code admin@lmguard.gov.in / admin123} and
 * {@code s.kumar@legalmetrology.example / inspect123} - the exact demo credentials both
 * frontends advertise on their sign-in screens. The other ten officers exist as roster
 * entries with a random password nobody needs, matching how only one or two accounts were
 * ever "the demo login" before this seeder existed.
 */
@Component
@Order(2)
@ConditionalOnProperty(name = "lmguard.demo.seed-demo-accounts", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class DemoUserSeeder implements ApplicationRunner {

    private final UserRepository userRepository;
    private final InspectorProfileRepository inspectorProfileRepository;
    private final ZoneRepository zoneRepository;
    private final PasswordEncoder passwordEncoder;

    private record SeedInspector(String officerCode, String shortName, String fullName, String rank,
                                  String email, String phone, String zone, boolean active,
                                  String joinedOn, String password) {
    }

    private static final List<SeedInspector> INSPECTORS = List.of(
            new SeedInspector("LM-INS-014", "S. Kumar", "Suresh Kumar", "Inspecting Officer",
                    "s.kumar@legalmetrology.example", "+91 98430 11204", "Coimbatore North", true,
                    "2023-06-12", "inspect123"),
            new SeedInspector("LM-INS-022", "A. Sharma", "Anita Sharma", "Inspecting Officer",
                    "a.sharma@legalmetrology.example", "+91 98430 22871", "Coimbatore South", true,
                    "2022-11-03", null),
            new SeedInspector("LM-INS-031", "R. Nair", "Rajiv Nair", "Senior Inspector",
                    "r.nair@legalmetrology.example", "+91 98430 30119", "Tiruppur", true,
                    "2019-02-18", null),
            new SeedInspector("LM-INS-047", "M. Iqbal", "Mohammed Iqbal", "Inspecting Officer",
                    "m.iqbal@legalmetrology.example", "+91 98430 47332", "Erode", true,
                    "2021-08-09", null),
            new SeedInspector("LM-INS-055", "P. Venkatesh", "Prabhu Venkatesh", "Field Inspector",
                    "p.venkatesh@legalmetrology.example", "+91 98430 55908", "Coimbatore West", true,
                    "2024-01-22", null),
            new SeedInspector("LM-INS-061", "K. Priya", "Kavitha Priya", "Inspecting Officer",
                    "k.priya@legalmetrology.example", "+91 98430 61447", "Coimbatore North", true,
                    "2022-04-15", null),
            new SeedInspector("LM-INS-069", "V. Lakshmi", "Vaishnavi Lakshmi", "Field Inspector",
                    "v.lakshmi@legalmetrology.example", "+91 98430 69215", "Coimbatore South", true,
                    "2024-07-01", null),
            new SeedInspector("LM-INS-078", "D. Rajesh", "Dinesh Rajesh", "Inspecting Officer",
                    "d.rajesh@legalmetrology.example", "+91 98430 78560", "Coimbatore North", false,
                    "2020-09-28", null),
            new SeedInspector("LM-INS-084", "T. Anand", "Thirumal Anand", "Inspecting Officer",
                    "t.anand@legalmetrology.example", "+91 98430 84073", "Coimbatore West", true,
                    "2023-03-06", null),
            new SeedInspector("LM-INS-090", "S. Fatima", "Sana Fatima", "Inspecting Officer",
                    "s.fatima@legalmetrology.example", "+91 98430 90188", "Tiruppur", true,
                    "2021-12-11", null),
            new SeedInspector("LM-INS-096", "G. Prakash", "Ganesh Prakash", "Senior Inspector",
                    "g.prakash@legalmetrology.example", "+91 98430 96724", "Erode", true,
                    "2018-05-30", null),
            new SeedInspector("LM-INS-102", "N. Devi", "Nandhini Devi", "Field Inspector",
                    "n.devi@legalmetrology.example", "+91 98430 10237", "Erode", false,
                    "2025-02-17", null)
    );

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedAdmin();
        seedInspectors();
    }

    private void seedAdmin() {
        String email = "admin@lmguard.gov.in";
        if (userRepository.existsByEmailIgnoreCase(email)) {
            return;
        }
        userRepository.save(User.builder()
                .name("Raj Kumar")
                .email(email)
                .passwordHash(passwordEncoder.encode("admin123"))
                .role(Role.ADMIN)
                .enabled(true)
                .build());
        log.info("Seeded demo administrator account {}", email);
    }

    private void seedInspectors() {
        int created = 0;
        for (SeedInspector seed : INSPECTORS) {
            if (userRepository.existsByEmailIgnoreCase(seed.email())) {
                continue;
            }
            String password = seed.password() != null ? seed.password() : randomPassword();

            User user = userRepository.save(User.builder()
                    .name(seed.shortName())
                    .email(seed.email())
                    .passwordHash(passwordEncoder.encode(password))
                    .role(Role.INSPECTOR)
                    .enabled(seed.active())
                    .build());

            Zone zone = zoneRepository.findByNameIgnoreCase(seed.zone()).orElse(null);

            inspectorProfileRepository.save(InspectorProfile.builder()
                    .user(user)
                    .officerCode(seed.officerCode())
                    .zone(zone)
                    .fullName(seed.fullName())
                    .rank(seed.rank())
                    .phone(seed.phone())
                    .joinedOn(LocalDate.parse(seed.joinedOn()))
                    .build());

            created++;
            if (seed.password() == null) {
                log.info("Seeded inspector {} ({}) with a generated password - not logged, "
                        + "reset it with PATCH /api/inspectors/{}/status or re-register if needed",
                        seed.officerCode(), seed.email(), seed.officerCode());
            }
        }
        if (created > 0) {
            log.info("Seeded {} demo inspector account(s)", created);
        }
    }

    private String randomPassword() {
        return java.util.UUID.randomUUID().toString();
    }
}
