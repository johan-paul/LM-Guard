package com.lmguard.rules;

import com.lmguard.ai.BoundingBox;
import com.lmguard.config.properties.RulesProperties;
import com.lmguard.entity.enums.ComplianceStatus;
import com.lmguard.entity.enums.RuleType;
import com.lmguard.entity.enums.Severity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * The rule engine is where compliance is decided, so these tests are the ones that matter
 * most. They pin down the behaviour that separates a usable inspection tool from a
 * classifier: low confidence must never become a violation.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Deterministic rule engine")
class DeterministicRuleEngineServiceTest {

    private static final String VERSION = "TEST-1.0";
    private static final UUID INSPECTION_ID = UUID.randomUUID();

    @Mock
    private RuleCatalog ruleCatalog;

    private DeterministicRuleEngineService engine;

    @BeforeEach
    void setUp() {
        RulesProperties properties = new RulesProperties(VERSION, "classpath:rules/sample-rules.json", false, 0.70);
        engine = new DeterministicRuleEngineService(ruleCatalog, properties);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private void givenRules(RuleDefinition... rules) {
        when(ruleCatalog.load(anyString()))
                .thenReturn(new RuleSet(VERSION, "SAMPLE", "demo rules", List.of(), List.of(rules)));
    }

    private RuleDefinition requiredRule(String field) {
        return new RuleDefinition(null, "R-" + field, field + " required", "SAMPLE", field,
                RuleType.REQUIRED_FIELD, true, 0.70, null, null, null, null, Severity.MAJOR,
                "Required declaration not detected", "Print the declaration");
    }

    private RuleDefinition patternRule(String field, String pattern) {
        return new RuleDefinition(null, "P-" + field, field + " format", "SAMPLE", field,
                RuleType.PATTERN_MATCH, false, 0.70, pattern, null, null, null, Severity.MINOR,
                "Value is not in a valid format", null);
    }

    private InspectionFacts facts(Map<String, FactValue> values) {
        return new InspectionFacts(INSPECTION_ID, values);
    }

    private FactValue present(String value, double confidence) {
        return new FactValue(value, confidence, BoundingBox.of(10, 20, 30, 40));
    }

    private FactValue absent(double confidence) {
        return new FactValue(null, confidence, BoundingBox.absent());
    }

    // ------------------------------------------------------------------

    @Nested
    @DisplayName("required field")
    class RequiredField {

        @Test
        @DisplayName("passes when the declaration is present and read confidently")
        void presentAndConfident() {
            givenRules(requiredRule("MRP"));

            ComplianceResult result = engine.evaluate(facts(Map.of("MRP", present("99", 0.97))), VERSION);

            assertThat(result.status()).isEqualTo(ComplianceStatus.COMPLIANT);
            assertThat(result.breaches()).isEmpty();
            assertThat(result.rulesetVersion()).isEqualTo(VERSION);
        }

        @Test
        @DisplayName("is NON_COMPLIANT when the declaration is confidently absent")
        void confidentlyAbsent() {
            givenRules(requiredRule("CONSUMER_CARE"));

            ComplianceResult result =
                    engine.evaluate(facts(Map.of("CONSUMER_CARE", absent(0.91))), VERSION);

            assertThat(result.status()).isEqualTo(ComplianceStatus.NON_COMPLIANT);
            assertThat(result.breaches()).hasSize(1);
            RuleFinding finding = result.breaches().getFirst();
            assertThat(finding.ruleCode()).isEqualTo("R-CONSUMER_CARE");
            assertThat(finding.decisionConfidence()).isEqualTo(0.91);
            assertThat(finding.observedValue()).isNull();
        }

        @Test
        @DisplayName("is INCONCLUSIVE, not NON_COMPLIANT, when absence was read with low confidence")
        void absentButUnsure() {
            givenRules(requiredRule("CONSUMER_CARE"));

            ComplianceResult result =
                    engine.evaluate(facts(Map.of("CONSUMER_CARE", absent(0.35))), VERSION);

            // The point of the whole design: a bad photograph is not evidence of a missing label.
            assertThat(result.status()).isEqualTo(ComplianceStatus.INCONCLUSIVE);
            assertThat(result.breaches()).hasSize(1);
            assertThat(result.breaches().getFirst().status()).isEqualTo(ComplianceStatus.INCONCLUSIVE);
        }

        @Test
        @DisplayName("is INCONCLUSIVE when a present value was read below the threshold")
        void presentButUnsure() {
            givenRules(requiredRule("MRP"));

            ComplianceResult result = engine.evaluate(facts(Map.of("MRP", present("9?", 0.41))), VERSION);

            assertThat(result.status()).isEqualTo(ComplianceStatus.INCONCLUSIVE);
            assertThat(result.breaches().getFirst().finding()).contains("low confidence");
        }

        @Test
        @DisplayName("is INCONCLUSIVE when the AI did not assess the field at all")
        void fieldNotAssessed() {
            givenRules(requiredRule("MANUFACTURE_DATE"));

            ComplianceResult result = engine.evaluate(facts(Map.of("MRP", present("99", 0.99))), VERSION);

            assertThat(result.status()).isEqualTo(ComplianceStatus.INCONCLUSIVE);
            assertThat(result.breaches().getFirst().finding()).contains("not assessed");
        }

        @Test
        @DisplayName("treats a blank string as an absent declaration")
        void blankIsAbsent() {
            givenRules(requiredRule("ORIGIN"));

            ComplianceResult result = engine.evaluate(facts(Map.of("ORIGIN", present("   ", 0.95))), VERSION);

            assertThat(result.status()).isEqualTo(ComplianceStatus.NON_COMPLIANT);
        }
    }

    @Nested
    @DisplayName("pattern match")
    class PatternMatch {

        @Test
        @DisplayName("passes a well-formed value")
        void validFormat() {
            givenRules(patternRule("NET_QUANTITY", "^\\d+(\\.\\d+)?\\s?(g|kg|ml|l)$"));

            ComplianceResult result =
                    engine.evaluate(facts(Map.of("NET_QUANTITY", present("500 g", 0.95))), VERSION);

            assertThat(result.status()).isEqualTo(ComplianceStatus.COMPLIANT);
        }

        @Test
        @DisplayName("is NON_COMPLIANT for a malformed value read confidently")
        void invalidFormat() {
            givenRules(patternRule("NET_QUANTITY", "^\\d+(\\.\\d+)?\\s?(g|kg|ml|l)$"));

            ComplianceResult result =
                    engine.evaluate(facts(Map.of("NET_QUANTITY", present("about half a kilo", 0.95))), VERSION);

            assertThat(result.status()).isEqualTo(ComplianceStatus.NON_COMPLIANT);
        }

        @Test
        @DisplayName("does not fire on an absent optional value")
        void absentOptional() {
            givenRules(patternRule("BATCH_NUMBER", "^[A-Z0-9]+$"));

            ComplianceResult result = engine.evaluate(facts(Map.of("BATCH_NUMBER", absent(0.99))), VERSION);

            assertThat(result.status()).isEqualTo(ComplianceStatus.COMPLIANT);
        }

        @Test
        @DisplayName("reports INCONCLUSIVE rather than passing when the pattern itself is invalid")
        void brokenPattern() {
            givenRules(patternRule("MRP", "([unclosed"));

            ComplianceResult result = engine.evaluate(facts(Map.of("MRP", present("99", 0.99))), VERSION);

            // A rule that cannot be applied must never be silently reported as compliant.
            assertThat(result.status()).isEqualTo(ComplianceStatus.INCONCLUSIVE);
        }
    }

    @Nested
    @DisplayName("other check types")
    class OtherChecks {

        @Test
        @DisplayName("MIN_LENGTH rejects a stub value")
        void minLength() {
            RuleDefinition rule = new RuleDefinition(null, "L-MFR", "manufacturer length", "SAMPLE",
                    "MANUFACTURER", RuleType.MIN_LENGTH, false, 0.70, null, null, null, 3,
                    Severity.MINOR, "Too short", null);
            givenRules(rule);

            assertThat(engine.evaluate(facts(Map.of("MANUFACTURER", present("AB", 0.95))), VERSION).status())
                    .isEqualTo(ComplianceStatus.NON_COMPLIANT);
            assertThat(engine.evaluate(facts(Map.of("MANUFACTURER", present("ABC Foods", 0.95))), VERSION).status())
                    .isEqualTo(ComplianceStatus.COMPLIANT);
        }

        @Test
        @DisplayName("NUMERIC_RANGE reads the leading number out of a value")
        void numericRange() {
            RuleDefinition rule = new RuleDefinition(null, "N-MRP", "price range", "SAMPLE", "MRP",
                    RuleType.NUMERIC_RANGE, false, 0.70, null, 1.0, 10000.0, null,
                    Severity.MAJOR, "Price out of range", null);
            givenRules(rule);

            assertThat(engine.evaluate(facts(Map.of("MRP", present("Rs. 99.00", 0.95))), VERSION).status())
                    .isEqualTo(ComplianceStatus.COMPLIANT);
            assertThat(engine.evaluate(facts(Map.of("MRP", present("99999", 0.95))), VERSION).status())
                    .isEqualTo(ComplianceStatus.NON_COMPLIANT);
        }
    }

    @Nested
    @DisplayName("aggregation")
    class Aggregation {

        @Test
        @DisplayName("NON_COMPLIANT outranks INCONCLUSIVE across rules")
        void worstWins() {
            givenRules(requiredRule("MRP"), requiredRule("CONSUMER_CARE"), requiredRule("ORIGIN"));

            ComplianceResult result = engine.evaluate(facts(Map.of(
                    "MRP", present("99", 0.98),
                    "CONSUMER_CARE", absent(0.95),
                    "ORIGIN", absent(0.20))), VERSION);

            assertThat(result.status()).isEqualTo(ComplianceStatus.NON_COMPLIANT);
            assertThat(result.breaches()).hasSize(2);
            assertThat(result.countOf(ComplianceStatus.NON_COMPLIANT)).isEqualTo(1);
            assertThat(result.countOf(ComplianceStatus.INCONCLUSIVE)).isEqualTo(1);
            assertThat(result.statusByField())
                    .containsEntry("MRP", ComplianceStatus.COMPLIANT)
                    .containsEntry("CONSUMER_CARE", ComplianceStatus.NON_COMPLIANT)
                    .containsEntry("ORIGIN", ComplianceStatus.INCONCLUSIVE);
        }

        @Test
        @DisplayName("reports the mean observation confidence")
        void overallConfidence() {
            givenRules(requiredRule("MRP"));

            ComplianceResult result = engine.evaluate(facts(Map.of(
                    "MRP", present("99", 0.90),
                    "ORIGIN", present("India", 0.80))), VERSION);

            assertThat(result.overallConfidence()).isEqualTo(0.85, org.assertj.core.data.Offset.offset(0.0001));
        }

        @Test
        @DisplayName("is reproducible: the same facts always give the same verdict")
        void deterministic() {
            givenRules(requiredRule("MRP"), patternRule("MRP", "^\\d+$"));
            InspectionFacts input = facts(Map.of("MRP", present("99", 0.95)));

            ComplianceResult first = engine.evaluate(input, VERSION);
            ComplianceResult second = engine.evaluate(input, VERSION);

            assertThat(first.status()).isEqualTo(second.status());
            assertThat(first.findings()).hasSameSizeAs(second.findings());
        }
    }
}
