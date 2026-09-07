package com.lmguard.ai;

import com.lmguard.entity.enums.ProductField;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Deterministic stand-in for the Python AI service.
 *
 * <p>This exists so that the backend, the frontend and the demo are never blocked on the AI
 * pipeline being finished, and so a live demonstration survives an external service outage.
 *
 * <p>It is deliberately <em>deterministic per inspection</em>: the same inspection id always
 * produces the same facts. That makes the mock usable in automated tests as well as demos,
 * and means a screenshot taken during preparation still matches what the judges will see.
 *
 * <p>The returned facts are shaped to exercise every path through the rule engine: fields
 * read confidently, a field that is genuinely absent, and a field read too poorly to judge.
 */
@Service
@Slf4j
public class MockAIAnalysisService implements AIAnalysisService {

    private static final String MODEL_VERSION = "mock-1.0.0";

    @Override
    public AIAnalysisResult analyzeImage(String imageUrl, UUID inspectionId) {
        long startedAt = System.currentTimeMillis();
        log.info("Mock AI analysing inspection {} (image: {})", inspectionId, imageUrl);

        // Stable pseudo-random choice derived from the inspection id, so results are
        // reproducible but not identical for every inspection in a demo dataset.
        int variant = variantFor(inspectionId);

        List<ExtractedFact> facts = new ArrayList<>();
        facts.add(ExtractedFact.detected(ProductField.MRP, "99", 0.97, BoundingBox.of(64, 210, 150, 54)));
        facts.add(ExtractedFact.detected(ProductField.NET_QUANTITY, "500 g", 0.95, BoundingBox.of(64, 280, 180, 52)));
        facts.add(ExtractedFact.detected(ProductField.MANUFACTURER, "ABC Foods", 0.93,
                BoundingBox.of(60, 355, 320, 48)));
        facts.add(ExtractedFact.detected(ProductField.ORIGIN, "India", 0.92, BoundingBox.of(60, 410, 140, 44)));
        facts.add(ExtractedFact.detected(ProductField.COMMODITY_NAME, "Classic Salted Chips", 0.96,
                BoundingBox.of(58, 120, 380, 62)));

        switch (variant) {
            case 0 -> {
                // Confidently absent: the classic non-compliance the demo is built around.
                facts.add(ExtractedFact.notDetected(ProductField.CONSUMER_CARE, 0.91));
                facts.add(ExtractedFact.detected(ProductField.MANUFACTURE_DATE, "03/2026", 0.89,
                        BoundingBox.of(240, 410, 160, 44)));
            }
            case 1 -> {
                // Fully compliant package.
                facts.add(ExtractedFact.detected(ProductField.CONSUMER_CARE, "care@abcfoods.example / 1800-123-456",
                        0.90, BoundingBox.of(58, 470, 400, 56)));
                facts.add(ExtractedFact.detected(ProductField.MANUFACTURE_DATE, "03/2026", 0.88,
                        BoundingBox.of(240, 410, 160, 44)));
            }
            default -> {
                // Read too poorly to judge: must surface as INCONCLUSIVE, never as a violation.
                facts.add(ExtractedFact.detected(ProductField.CONSUMER_CARE, "car…@abcfo…", 0.41,
                        BoundingBox.of(58, 470, 400, 56)));
                facts.add(ExtractedFact.notDetected(ProductField.MANUFACTURE_DATE, 0.52));
            }
        }

        List<String> warnings = variant == 2
                ? List.of("Low-contrast region detected on the lower panel; some text may be unreliable.")
                : List.of();

        AIAnalysisResult result = new AIAnalysisResult(
                inspectionId,
                facts,
                AIAnalysisResult.PROVIDER_MOCK,
                MODEL_VERSION,
                System.currentTimeMillis() - startedAt,
                warnings);

        log.debug("Mock AI produced {} facts for inspection {} (variant {})", facts.size(), inspectionId, variant);
        return result;
    }

    @Override
    public String providerName() {
        return AIAnalysisResult.PROVIDER_MOCK;
    }

    /** 0, 1 or 2, stable for a given inspection id. */
    private int variantFor(UUID inspectionId) {
        if (inspectionId == null) {
            return 0;
        }
        return Math.floorMod(inspectionId.hashCode(), 3);
    }
}
