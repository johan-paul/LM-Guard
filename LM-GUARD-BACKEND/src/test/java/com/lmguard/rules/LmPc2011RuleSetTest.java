package com.lmguard.rules;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lmguard.ai.BoundingBox;
import com.lmguard.config.properties.RulesProperties;
import com.lmguard.entity.enums.ComplianceStatus;
import com.lmguard.repository.RuleRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.DefaultResourceLoader;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Loads the real {@code lm-pc-2011-rules.json} bundled resource exactly the way
 * {@link RuleCatalog} does in production, and evaluates it through the unmodified
 * {@link DeterministicRuleEngineService}.
 *
 * <p>This is the regression test for the legal rule content itself: a mistake in the JSON
 * (bad regex, wrong field name, wrong rule type) fails here before it ever reaches an
 * inspection. It does not touch the database and needs no Spring context.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LM-PC-2011-v1 bundled ruleset")
class LmPc2011RuleSetTest {

    @Mock
    private RuleRepository ruleRepository;

    private RuleCatalog catalog() {
        RulesProperties properties = new RulesProperties(
                "LM-PC-2011-v1", "classpath:rules/lm-pc-2011-rules.json", false, 0.70);
        return new RuleCatalog(ruleRepository, properties, new DefaultResourceLoader(), new ObjectMapper());
    }

    @Test
    @DisplayName("parses without error and contains the six Rule 6 declarations, cited by rule number")
    void parsesAndCitesRealRuleNumbers() {
        RuleSet ruleSet = catalog().loadSampleFileOnly();

        assertThat(ruleSet.rulesetVersion()).isEqualTo("LM-PC-2011-v1");
        assertThat(ruleSet.rules()).isNotEmpty();
        assertThat(ruleSet.rules())
                .extracting(RuleDefinition::ruleCode)
                .contains(
                        "LM-PC-6-1-a-MANUFACTURER",
                        "LM-PC-6-1-b-COMMODITY_NAME",
                        "LM-PC-6-1-c-NET_QUANTITY",
                        "LM-PC-6-1-d-MANUFACTURE_DATE",
                        "LM-PC-6-1-e-MRP",
                        "LM-PC-6-2-CONSUMER_CARE");

        // Every rule must cite the real rule number in its description, per the brief's
        // traceability requirement -- not a made-up code with no legal citation attached.
        assertThat(ruleSet.rules()).allSatisfy(rule ->
                assertThat(rule.description()).as("rule %s cites its source", rule.ruleCode())
                        .containsPattern("Rule \\d+"));
    }

    @Test
    @DisplayName("does NOT carry forward an unconditional country-of-origin requirement")
    void doesNotFabricateOriginRequirement() {
        RuleSet ruleSet = catalog().loadSampleFileOnly();

        assertThat(ruleSet.rules())
                .as("see legal-rules/RULE_REVIEW.md #2 -- the 2011 Rules do not require this "
                        + "for a domestic package, unlike the DEMO-ORG-001 sample rule")
                .noneMatch(rule -> "ORIGIN".equals(rule.field()));
    }

    @Test
    @DisplayName("a package missing MRP, quantity and consumer care is NON_COMPLIANT with real citations")
    void missingCoreDeclarationsIsNonCompliant() {
        RuleCatalog catalog = catalog();
        when(ruleRepository.findByVersionAndActiveTrueOrderByRuleCodeAsc(anyString())).thenReturn(List.of());

        DeterministicRuleEngineService engine = new DeterministicRuleEngineService(
                catalog, new RulesProperties("LM-PC-2011-v1", "classpath:rules/lm-pc-2011-rules.json", false, 0.70));

        InspectionFacts facts = new InspectionFacts(UUID.randomUUID(), Map.of(
                "MANUFACTURER", present("ABC Foods Pvt Ltd, Pune", 0.95),
                "COMMODITY_NAME", present("Classic Salted Chips", 0.95),
                "MRP", absent(0.93),
                "NET_QUANTITY", absent(0.90),
                "MANUFACTURE_DATE", present("03/2026", 0.90),
                "CONSUMER_CARE", absent(0.92)));

        ComplianceResult result = engine.evaluate(facts, "LM-PC-2011-v1");

        assertThat(result.status()).isEqualTo(ComplianceStatus.NON_COMPLIANT);
        assertThat(result.breaches())
                .extracting(RuleFinding::ruleCode)
                .contains("LM-PC-6-1-e-MRP", "LM-PC-6-1-c-NET_QUANTITY", "LM-PC-6-2-CONSUMER_CARE");
    }

    @Test
    @DisplayName("a fully and confidently declared package is COMPLIANT")
    void fullyDeclaredPackageIsCompliant() {
        RuleCatalog catalog = catalog();
        when(ruleRepository.findByVersionAndActiveTrueOrderByRuleCodeAsc(anyString())).thenReturn(List.of());

        DeterministicRuleEngineService engine = new DeterministicRuleEngineService(
                catalog, new RulesProperties("LM-PC-2011-v1", "classpath:rules/lm-pc-2011-rules.json", false, 0.70));

        InspectionFacts facts = new InspectionFacts(UUID.randomUUID(), Map.of(
                "MANUFACTURER", present("ABC Foods Pvt Ltd, Pune, Maharashtra 411001", 0.96),
                "COMMODITY_NAME", present("Classic Salted Chips", 0.96),
                "MRP", present("Rs. 99.00", 0.97),
                "NET_QUANTITY", present("500 g", 0.95),
                "MANUFACTURE_DATE", present("03/2026", 0.92),
                "CONSUMER_CARE", present("care@abcfoods.example / 1800-123-456", 0.90)));

        ComplianceResult result = engine.evaluate(facts, "LM-PC-2011-v1");

        assertThat(result.status()).isEqualTo(ComplianceStatus.COMPLIANT);
        assertThat(result.breaches()).isEmpty();
    }

    private FactValue present(String value, double confidence) {
        return new FactValue(value, confidence, BoundingBox.of(10, 20, 30, 40));
    }

    private FactValue absent(double confidence) {
        return new FactValue(null, confidence, BoundingBox.absent());
    }
}
