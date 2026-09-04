package com.lmguard;

import com.lmguard.ai.AIAnalysisService;
import com.lmguard.evidence.EvidenceService;
import com.lmguard.risk.RiskEngineService;
import com.lmguard.rules.RuleEngineService;
import com.lmguard.security.JwtService;
import com.lmguard.service.InspectionService;
import com.lmguard.storage.FileStorageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke test: the application context starts and every seam is wired.
 *
 * <p>Runs against in-memory H2 with the schema generated from the entities, so it needs no
 * database, no Docker and no Supabase account. The Flyway migrations are PostgreSQL-specific
 * and are verified against a real PostgreSQL rather than here.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Application context")
class LmGuardApplicationTests {

    @Autowired private AIAnalysisService aiAnalysisService;
    @Autowired private RuleEngineService ruleEngineService;
    @Autowired private RiskEngineService riskEngineService;
    @Autowired private EvidenceService evidenceService;
    @Autowired private FileStorageService fileStorageService;
    @Autowired private InspectionService inspectionService;
    @Autowired private JwtService jwtService;

    @Test
    @DisplayName("starts with every engine and seam wired")
    void contextLoads() {
        assertThat(aiAnalysisService).isNotNull();
        assertThat(ruleEngineService).isNotNull();
        assertThat(riskEngineService).isNotNull();
        assertThat(evidenceService).isNotNull();
        assertThat(inspectionService).isNotNull();
        assertThat(jwtService).isNotNull();
    }

    @Test
    @DisplayName("uses the mock AI and local storage under the test profile")
    void usesTestProviders() {
        assertThat(aiAnalysisService.providerName()).isEqualTo("MOCK");
        assertThat(fileStorageService.providerName()).isEqualTo("LOCAL");
    }
}
