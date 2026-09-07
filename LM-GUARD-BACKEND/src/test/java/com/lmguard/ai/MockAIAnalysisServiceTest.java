package com.lmguard.ai;

import com.lmguard.entity.enums.ProductField;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Mock AI analysis service")
class MockAIAnalysisServiceTest {

    private final MockAIAnalysisService service = new MockAIAnalysisService();

    @Test
    @DisplayName("returns facts with confidences and bounding boxes")
    void returnsUsableFacts() {
        UUID inspectionId = UUID.randomUUID();

        AIAnalysisResult result = service.analyzeImage("http://example.test/pack.jpg", inspectionId);

        assertThat(result.inspectionId()).isEqualTo(inspectionId);
        assertThat(result.provider()).isEqualTo(AIAnalysisResult.PROVIDER_MOCK);
        assertThat(result.facts()).isNotEmpty();
        assertThat(result.fact(ProductField.MRP)).isPresent();
        assertThat(result.fact(ProductField.NET_QUANTITY)).hasValueSatisfying(fact -> {
            assertThat(fact.value()).isEqualTo("500 g");
            assertThat(fact.confidence()).isBetween(0.0, 1.0);
            assertThat(fact.boundingBox().isComplete()).isTrue();
        });
        assertThat(result.meanConfidence()).isBetween(0.0, 1.0);
    }

    @Test
    @DisplayName("is deterministic for a given inspection id")
    void deterministicPerInspection() {
        UUID inspectionId = UUID.randomUUID();

        AIAnalysisResult first = service.analyzeImage("http://example.test/a.jpg", inspectionId);
        AIAnalysisResult second = service.analyzeImage("http://example.test/a.jpg", inspectionId);

        assertThat(first.facts()).isEqualTo(second.facts());
    }

    @Test
    @DisplayName("covers every rule-engine path across inspections")
    void exercisesAllPaths() {
        boolean sawConfidentAbsence = false;
        boolean sawLowConfidence = false;
        boolean sawFullyPresent = false;

        // Three variants exist; a small sample of ids is enough to hit all of them.
        for (int i = 0; i < 60; i++) {
            AIAnalysisResult result = service.analyzeImage("http://example.test/x.jpg", UUID.randomUUID());
            ExtractedFact care = result.fact(ProductField.CONSUMER_CARE).orElseThrow();

            if (!care.isPresent() && care.confidence() >= 0.70) {
                sawConfidentAbsence = true;
            }
            if (care.isPresent() && care.confidence() < 0.70) {
                sawLowConfidence = true;
            }
            if (care.isPresent() && care.confidence() >= 0.70) {
                sawFullyPresent = true;
            }
        }

        assertThat(sawConfidentAbsence).as("a confidently absent declaration").isTrue();
        assertThat(sawLowConfidence).as("a low-confidence reading").isTrue();
        assertThat(sawFullyPresent).as("a compliant package").isTrue();
    }

    @Test
    @DisplayName("clamps out-of-range confidences")
    void clampsConfidence() {
        assertThat(new ExtractedFact("X", "v", 1.4, null, null).confidence()).isEqualTo(1.0);
        assertThat(new ExtractedFact("X", "v", -0.2, null, null).confidence()).isEqualTo(0.0);
        assertThat(new ExtractedFact("X", "v", 0.5, null, null).boundingBox()).isNotNull();
    }
}
