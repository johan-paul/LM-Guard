package com.lmguard.ai;

import com.lmguard.config.properties.AiProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Chooses which AI implementation handles an analysis, and decides what happens when the
 * real one fails.
 *
 * <p>Two switches drive it:
 * <ul>
 *   <li>{@code AI_MOCK_MODE=true} - every analysis goes to the mock. This is how the backend
 *       and the frontend stay unblocked while the Python service is still being built.</li>
 *   <li>{@code AI_FALLBACK_TO_MOCK=true} - when the external service errors, fall back to the
 *       mock instead of failing the inspection.</li>
 * </ul>
 *
 * <p>The fallback is a demo safeguard, and it is honest about itself: the inspection records
 * {@code aiProvider = MOCK}, so a result produced from mock data can never be mistaken for
 * one produced from a real photograph. It defaults to off under the {@code prod} profile,
 * where inventing facts would be far worse than returning an error.
 */
@Service
@Primary
@Slf4j
public class AiAnalysisRouter implements AIAnalysisService {

    private final AIAnalysisService mockService;
    private final AIAnalysisService externalService;
    private final AiProperties aiProperties;

    public AiAnalysisRouter(MockAIAnalysisService mockService,
                            ExternalAIAnalysisService externalService,
                            AiProperties aiProperties) {
        this.mockService = mockService;
        this.externalService = externalService;
        this.aiProperties = aiProperties;
    }

    @Override
    public AIAnalysisResult analyzeImage(String imageUrl, UUID inspectionId) {
        if (aiProperties.mockMode()) {
            log.debug("AI_MOCK_MODE is on; using the mock analyser for inspection {}", inspectionId);
            return mockService.analyzeImage(imageUrl, inspectionId);
        }

        try {
            return externalService.analyzeImage(imageUrl, inspectionId);
        } catch (RuntimeException ex) {
            if (!aiProperties.fallbackToMock()) {
                throw ex;
            }
            log.warn("External AI service failed for inspection {} ({}); falling back to the mock analyser. "
                            + "The inspection will be recorded with aiProvider=MOCK.",
                    inspectionId, ex.getMessage());
            return mockService.analyzeImage(imageUrl, inspectionId);
        }
    }

    @Override
    public String providerName() {
        return aiProperties.mockMode()
                ? AIAnalysisResult.PROVIDER_MOCK
                : AIAnalysisResult.PROVIDER_EXTERNAL;
    }
}
