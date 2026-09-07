package com.lmguard.service;

import com.lmguard.dto.inspector.InspectorCreateRequest;
import com.lmguard.dto.inspector.InspectorInspectionSummary;
import com.lmguard.dto.inspector.InspectorResponse;
import com.lmguard.dto.inspector.InspectorUpdateRequest;
import com.lmguard.entity.Inspection;
import com.lmguard.entity.InspectorProfile;
import com.lmguard.entity.User;
import com.lmguard.entity.Zone;
import com.lmguard.entity.enums.Role;
import com.lmguard.exception.ConflictException;
import com.lmguard.exception.ErrorCode;
import com.lmguard.exception.ResourceNotFoundException;
import com.lmguard.mapper.InspectorMapper;
import com.lmguard.repository.InspectionRepository;
import com.lmguard.repository.InspectorProfileRepository;
import com.lmguard.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Locale;

/**
 * The inspecting-officer directory the admin console's Inspector Management screen manages.
 *
 * <p>Every officer is a real {@link User} account (role {@code INSPECTOR}) plus an
 * {@link InspectorProfile} carrying the directory fields auth doesn't need. Creating one here
 * mints a fresh account with a generated password, since the console's "Add Inspector" form
 * collects no password of its own - there was nowhere else for credentials to come from.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InspectorService {

    private static final String CODE_PREFIX = "LM-INS-";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String PASSWORD_ALPHABET =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";

    private final InspectorProfileRepository inspectorProfileRepository;
    private final UserRepository userRepository;
    private final InspectionRepository inspectionRepository;
    private final ZoneService zoneService;
    private final PasswordEncoder passwordEncoder;
    private final InspectorMapper inspectorMapper;

    @Transactional(readOnly = true)
    public List<InspectorResponse> list(String search, String zone, String status) {
        String needle = trimToNull(search);
        String needleLower = needle == null ? null : needle.toLowerCase(Locale.ROOT);

        return inspectorProfileRepository.findAllDetailed().stream()
                .filter(profile -> zone == null || zone.isBlank()
                        || (profile.getZone() != null && profile.getZone().getName().equalsIgnoreCase(zone)))
                .filter(profile -> status == null || status.isBlank() || status.equalsIgnoreCase("ALL")
                        || statusOf(profile).equalsIgnoreCase(status))
                .filter(profile -> needleLower == null || matches(profile, needleLower))
                .map(profile -> toResponse(profile, null, false))
                .toList();
    }

    @Transactional(readOnly = true)
    public InspectorResponse getByCode(String officerCode) {
        return toResponse(requireByCode(officerCode), null, true);
    }

    /** Every zone with the officers posted to it - the "Zone roster" cards on Inspector Management. */
    @Transactional(readOnly = true)
    public List<com.lmguard.dto.zone.ZoneRosterResponse> zoneRoster() {
        List<InspectorProfile> all = inspectorProfileRepository.findAllDetailed();

        return zoneService.listAll().stream().map(zone -> {
            List<InspectorResponse> roster = all.stream()
                    .filter(profile -> profile.getZone() != null && profile.getZone().getId().equals(zone.getId()))
                    .map(profile -> toResponse(profile, null, false))
                    .toList();

            int active = (int) roster.stream().filter(i -> "ACTIVE".equals(i.status())).count();
            long workload = roster.stream()
                    .filter(i -> "ACTIVE".equals(i.status()))
                    .mapToLong(InspectorResponse::activeAssignments)
                    .sum();

            return new com.lmguard.dto.zone.ZoneRosterResponse(
                    zone.getId(), zone.getName(), zone.getCode(), zone.getOffice(), zone.getDistrict(),
                    roster, roster.size(), active, roster.size() - active, workload);
        }).toList();
    }

    @Transactional
    public InspectorResponse create(InspectorCreateRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException(ErrorCode.EMAIL_ALREADY_REGISTERED,
                    "An account with this email already exists");
        }

        String temporaryPassword = generateTemporaryPassword();
        User user = userRepository.save(User.builder()
                .name(request.name().trim())
                .email(email)
                .passwordHash(passwordEncoder.encode(temporaryPassword))
                .role(Role.INSPECTOR)
                .enabled(!"INACTIVE".equalsIgnoreCase(trimToNull(request.status())))
                .build());

        InspectorProfile profile = InspectorProfile.builder()
                .user(user)
                .officerCode(nextOfficerCode())
                .zone(resolveZoneOrNull(request.zone()))
                .fullName(trimToNull(request.fullName()))
                .rank(trimToNull(request.rank()) == null ? "Inspecting Officer" : request.rank().trim())
                .phone(request.phone().trim())
                .joinedOn(LocalDate.now())
                .build();
        profile = inspectorProfileRepository.save(profile);

        log.info("Created inspector {} ({}) in zone {}", profile.getOfficerCode(), email,
                profile.getZone() == null ? "none" : profile.getZone().getName());

        return toResponse(profile, temporaryPassword, false);
    }

    @Transactional
    public InspectorResponse update(String officerCode, InspectorUpdateRequest request) {
        InspectorProfile profile = requireByCode(officerCode);
        User user = profile.getUser();

        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (!email.equalsIgnoreCase(user.getEmail()) && userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException(ErrorCode.EMAIL_ALREADY_REGISTERED,
                    "An account with this email already exists");
        }
        user.setName(request.name().trim());
        user.setEmail(email);
        if (request.status() != null && !request.status().isBlank()) {
            user.setEnabled(!"INACTIVE".equalsIgnoreCase(request.status()));
        }
        userRepository.save(user);

        profile.setFullName(trimToNull(request.fullName()));
        if (trimToNull(request.rank()) != null) {
            profile.setRank(request.rank().trim());
        }
        profile.setPhone(request.phone().trim());
        if (trimToNull(request.zone()) != null) {
            profile.setZone(zoneService.requireByName(request.zone()));
        }
        profile = inspectorProfileRepository.save(profile);

        return toResponse(profile, null, false);
    }

    @Transactional
    public InspectorResponse setStatus(String officerCode, String status) {
        InspectorProfile profile = requireByCode(officerCode);
        User user = profile.getUser();
        user.setEnabled("ACTIVE".equalsIgnoreCase(status));
        userRepository.save(user);
        log.info("Inspector {} status set to {}", officerCode, status.toUpperCase(Locale.ROOT));
        return toResponse(profile, null, false);
    }

    @Transactional
    public InspectorResponse setZone(String officerCode, String zoneName) {
        InspectorProfile profile = requireByCode(officerCode);
        profile.setZone(zoneService.requireByName(zoneName));
        profile = inspectorProfileRepository.save(profile);
        log.info("Inspector {} reassigned to zone {}", officerCode, zoneName);
        return toResponse(profile, null, false);
    }

    /** Called on successful login so "last active" reflects real sign-ins. */
    @Transactional
    public void recordLogin(java.util.UUID userId) {
        inspectorProfileRepository.findDetailedByUserId(userId).ifPresent(profile -> {
            profile.setLastActiveAt(Instant.now());
            inspectorProfileRepository.save(profile);
        });
    }

    // ------------------------------------------------------------------

    private InspectorProfile requireByCode(String officerCode) {
        return inspectorProfileRepository.findDetailedByOfficerCode(officerCode)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.INSPECTOR_NOT_FOUND,
                        ErrorCode.INSPECTOR_NOT_FOUND.getDefaultMessage() + ": " + officerCode));
    }

    private InspectorResponse toResponse(InspectorProfile profile, String temporaryPassword, boolean includeInspections) {
        java.util.UUID userId = profile.getUser().getId();
        long activeAssignments = inspectionRepository.countOpenByInspector(userId);
        long completedThisMonth = inspectionRepository.countCompletedByInspectorSince(userId, startOfMonth());
        long recordsFiled = inspectionRepository.countByInspector_Id(userId);
        long openRecords = inspectionRepository.countAwaitingReviewByInspector(userId);

        List<InspectorInspectionSummary> inspections = includeInspections
                ? inspectionRepository.findTop5ByInspector_IdOrderByCreatedAtDesc(userId).stream()
                        .map(this::toInspectionSummary)
                        .toList()
                : null;

        return inspectorMapper.toResponse(profile, activeAssignments, completedThisMonth,
                recordsFiled, openRecords, temporaryPassword, inspections);
    }

    private InspectorInspectionSummary toInspectionSummary(Inspection inspection) {
        return new InspectorInspectionSummary(
                inspection.getId(),
                inspection.getProduct() == null ? null : inspection.getProduct().getProductName(),
                inspection.getStatus(),
                inspection.getRiskScore(),
                inspection.getCreatedAt());
    }

    private Zone resolveZoneOrNull(String zoneName) {
        String trimmed = trimToNull(zoneName);
        return trimmed == null ? null : zoneService.requireByName(trimmed);
    }

    private String statusOf(InspectorProfile profile) {
        return profile.getUser().isEnabled() ? "ACTIVE" : "INACTIVE";
    }

    private boolean matches(InspectorProfile profile, String needleLower) {
        User user = profile.getUser();
        return contains(user.getName(), needleLower)
                || contains(profile.getFullName(), needleLower)
                || contains(profile.getOfficerCode(), needleLower)
                || contains(user.getEmail(), needleLower)
                || contains(profile.getPhone(), needleLower);
    }

    private boolean contains(String haystack, String needleLower) {
        return haystack != null && haystack.toLowerCase(Locale.ROOT).contains(needleLower);
    }

    private String nextOfficerCode() {
        int max = 100;
        for (String code : inspectorProfileRepository.findAllOfficerCodes()) {
            int dash = code.lastIndexOf('-');
            if (dash < 0) {
                continue;
            }
            try {
                int n = Integer.parseInt(code.substring(dash + 1));
                if (n > max) {
                    max = n;
                }
            } catch (NumberFormatException ignored) {
                // Non-numeric suffix; skip it rather than fail account creation over it.
            }
        }
        return CODE_PREFIX + (max + 1);
    }

    private String generateTemporaryPassword() {
        StringBuilder sb = new StringBuilder(12);
        for (int i = 0; i < 12; i++) {
            sb.append(PASSWORD_ALPHABET.charAt(RANDOM.nextInt(PASSWORD_ALPHABET.length())));
        }
        return sb.toString();
    }

    private Instant startOfMonth() {
        return LocalDate.now().with(TemporalAdjusters.firstDayOfMonth())
                .atStartOfDay(java.time.ZoneOffset.UTC).toInstant().truncatedTo(ChronoUnit.SECONDS);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
