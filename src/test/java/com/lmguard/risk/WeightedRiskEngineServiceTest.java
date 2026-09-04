package com.lmguard.risk;

import com.lmguard.config.properties.RiskProperties;
import com.lmguard.entity.enums.RiskLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Weighted risk engine")
class WeightedRiskEngineServiceTest {

    private WeightedRiskEngineService engine;

    @BeforeEach
    void setUp() {
        RiskProperties properties = new RiskProperties(
                new RiskProperties.Weights(30, 20, 25, 10, 15),
                new RiskProperties.Thresholds(30, 60),
                100,
                "FOOD,PACKAGED_FOOD,EDIBLE_OIL,INFANT_FOOD,DAIRY,MEDICINE,COSMETICS");
        engine = new WeightedRiskEngineService(properties);
    }

    @Test
    @DisplayName("a clean product with no history scores LOW")
    void noFactorsIsLow() {
        RiskAssessment assessment = engine.assess(RiskInput.empty());

        assertThat(assessment.totalScore()).isZero();
        assertThat(assessment.riskLevel()).isEqualTo(RiskLevel.LOW);
        assertThat(assessment.explanation()).contains("No risk factors");
    }

    @Test
    @DisplayName("a single prior violation lands in the LOW band")
    void singlePriorViolationIsLow() {
        RiskAssessment assessment = engine.assess(new RiskInput(
                1, 0, false, "STATIONERY", Set.of(), Set.of()));

        // 1 of 3 prior violations -> 10 of 30 points.
        assertThat(assessment.previousViolations()).isEqualTo(10);
        assertThat(assessment.totalScore()).isEqualTo(10);
        assertThat(assessment.riskLevel()).isEqualTo(RiskLevel.LOW);
    }

    @Test
    @DisplayName("history plus a label change reaches MEDIUM")
    void historyAndChangesIsMedium() {
        RiskAssessment assessment = engine.assess(new RiskInput(
                3, 1, false, "STATIONERY", Set.of(), Set.of()));

        assertThat(assessment.previousViolations()).isEqualTo(30);
        assertThat(assessment.productChanges()).isEqualTo(10);
        assertThat(assessment.totalScore()).isEqualTo(40);
        assertThat(assessment.riskLevel()).isEqualTo(RiskLevel.MEDIUM);
    }

    @Test
    @DisplayName("a repeat offender in a high-risk category reaches HIGH")
    void repeatOffenderIsHigh() {
        RiskAssessment assessment = engine.assess(new RiskInput(
                3, 2, false, "PACKAGED_FOOD",
                Set.of("DEMO-RULE-001"), Set.of("DEMO-RULE-001", "DEMO-MRP-001")));

        // 30 previous + 20 changes + 10 category + 15 repeat = 75
        assertThat(assessment.previousViolations()).isEqualTo(30);
        assertThat(assessment.productChanges()).isEqualTo(20);
        assertThat(assessment.categoryRisk()).isEqualTo(10);
        assertThat(assessment.repeatIssue()).isEqualTo(15);
        assertThat(assessment.totalScore()).isEqualTo(75);
        assertThat(assessment.riskLevel()).isEqualTo(RiskLevel.HIGH);
    }

    @Test
    @DisplayName("the total is capped at 100 and says so")
    void capsAtMaximum() {
        RiskAssessment assessment = engine.assess(new RiskInput(
                10, 10, true, "MEDICINE",
                Set.of("DEMO-RULE-001"), Set.of("DEMO-RULE-001")));

        assertThat(assessment.totalScore()).isEqualTo(100);
        assertThat(assessment.riskLevel()).isEqualTo(RiskLevel.HIGH);
        assertThat(assessment.explanation()).contains("capped");
    }

    @Test
    @DisplayName("an online mismatch contributes its full weight")
    void onlineMismatch() {
        RiskAssessment assessment = engine.assess(new RiskInput(
                0, 0, true, null, Set.of(), Set.of()));

        assertThat(assessment.onlineMismatch()).isEqualTo(25);
        assertThat(assessment.totalScore()).isEqualTo(25);
        assertThat(assessment.riskLevel()).isEqualTo(RiskLevel.LOW);
    }

    @Test
    @DisplayName("category matching ignores case and surrounding whitespace")
    void categoryMatchingIsLenient() {
        assertThat(engine.assess(new RiskInput(0, 0, false, "  packaged_food ", Set.of(), Set.of()))
                .categoryRisk()).isEqualTo(10);
        assertThat(engine.assess(new RiskInput(0, 0, false, "STATIONERY", Set.of(), Set.of()))
                .categoryRisk()).isZero();
        assertThat(engine.assess(new RiskInput(0, 0, false, null, Set.of(), Set.of()))
                .categoryRisk()).isZero();
    }

    @Test
    @DisplayName("a different rule failing is not a repeat issue")
    void repeatRequiresTheSameRule() {
        RiskAssessment assessment = engine.assess(new RiskInput(
                0, 0, false, null, Set.of("DEMO-QTY-001"), Set.of("DEMO-MRP-001")));

        assertThat(assessment.repeatIssue()).isZero();
    }

    @Test
    @DisplayName("every score explains itself")
    void explanationIsItemised() {
        RiskAssessment assessment = engine.assess(new RiskInput(
                2, 1, true, "DAIRY", Set.of("R1"), Set.of("R1")));

        assertThat(assessment.explanation())
                .contains("prior non-compliant inspection")
                .contains("change(s) to declared values")
                .contains("online listing")
                .contains("higher-risk list")
                .contains("breached again");
        assertThat(assessment.totalScore()).isEqualTo(
                assessment.previousViolations() + assessment.productChanges()
                        + assessment.onlineMismatch() + assessment.categoryRisk() + assessment.repeatIssue());
    }

    @Test
    @DisplayName("band boundaries land where the configuration says")
    void bandBoundaries() {
        // lowMax = 30, mediumMax = 60
        assertThat(engine.assess(new RiskInput(3, 0, false, null, Set.of(), Set.of())).riskLevel())
                .isEqualTo(RiskLevel.LOW);      // exactly 30
        assertThat(engine.assess(new RiskInput(3, 1, false, null, Set.of(), Set.of())).riskLevel())
                .isEqualTo(RiskLevel.MEDIUM);   // 40
        assertThat(engine.assess(new RiskInput(3, 2, false, null, Set.of("R"), Set.of("R"))).riskLevel())
                .isEqualTo(RiskLevel.HIGH);     // 65
    }
}
