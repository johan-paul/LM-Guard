package com.lmguard.ai;

import com.lmguard.config.properties.AiProperties;
import com.lmguard.exception.AiServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins down the exact bug this project shipped with: {@code AI_MOCK_MODE} and
 * {@code AI_FALLBACK_TO_MOCK} must actually control which provider runs, in both directions.
 * A profile that hardcodes either flag so it cannot be overridden is exactly what let every
 * inspection silently use {@link MockAIAnalysisService} while the real service sat unused.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AiAnalysisRouter")
class AiAnalysisRouterTest {

    private static final UUID INSPECTION_ID = UUID.randomUUID();
    private static final String IMAGE_URL = "http://localhost:8080/files/x.jpg";

    @Mock
    private MockAIAnalysisService mockService;

    @Mock
    private ExternalAIAnalysisService externalService;

    private AIAnalysisResult mockResult() {
        return new AIAnalysisResult(INSPECTION_ID, List.of(), AIAnalysisResult.PROVIDER_MOCK, "mock-1.0.0", 1, List.of());
    }

    private AIAnalysisResult externalResult() {
        return new AIAnalysisResult(INSPECTION_ID, List.of(), AIAnalysisResult.PROVIDER_EXTERNAL, "real-1.0.0", 5, List.of());
    }

    private AiProperties properties(boolean mockMode, boolean fallbackToMock) {
        return new AiProperties(mockMode, fallbackToMock, "http://localhost:8000", "/analyze", "", 5_000);
    }

    @Test
    @DisplayName("AI_MOCK_MODE=true routes every call to the mock, never touching the real service")
    void mockModeOnUsesMockOnly() {
        when(mockService.analyzeImage(IMAGE_URL, INSPECTION_ID)).thenReturn(mockResult());
        AiAnalysisRouter router = new AiAnalysisRouter(mockService, externalService, properties(true, true));

        AIAnalysisResult result = router.analyzeImage(IMAGE_URL, INSPECTION_ID);

        assertThat(result.provider()).isEqualTo(AIAnalysisResult.PROVIDER_MOCK);
        assertThat(router.providerName()).isEqualTo(AIAnalysisResult.PROVIDER_MOCK);
        verify(externalService, never()).analyzeImage(anyString(), any());
    }

    @Test
    @DisplayName("AI_MOCK_MODE=false routes to the real service when it succeeds")
    void mockModeOffUsesExternalOnSuccess() {
        when(externalService.analyzeImage(IMAGE_URL, INSPECTION_ID)).thenReturn(externalResult());
        AiAnalysisRouter router = new AiAnalysisRouter(mockService, externalService, properties(false, false));

        AIAnalysisResult result = router.analyzeImage(IMAGE_URL, INSPECTION_ID);

        assertThat(result.provider()).isEqualTo(AIAnalysisResult.PROVIDER_EXTERNAL);
        assertThat(router.providerName()).isEqualTo(AIAnalysisResult.PROVIDER_EXTERNAL);
        verify(mockService, never()).analyzeImage(anyString(), any());
    }

    @Test
    @DisplayName("AI_FALLBACK_TO_MOCK=false lets a real-service failure propagate as a real error")
    void fallbackDisabledPropagatesFailure() {
        when(externalService.analyzeImage(IMAGE_URL, INSPECTION_ID))
                .thenThrow(new AiServiceException("AI service call failed"));
        AiAnalysisRouter router = new AiAnalysisRouter(mockService, externalService, properties(false, false));

        assertThatThrownBy(() -> router.analyzeImage(IMAGE_URL, INSPECTION_ID))
                .isInstanceOf(AiServiceException.class);
        verify(mockService, never()).analyzeImage(anyString(), any());
    }

    @Test
    @DisplayName("AI_FALLBACK_TO_MOCK=true degrades a real-service failure to the mock, still labelled MOCK")
    void fallbackEnabledDegradesToMock() {
        when(externalService.analyzeImage(IMAGE_URL, INSPECTION_ID))
                .thenThrow(new AiServiceException("AI service call failed"));
        when(mockService.analyzeImage(IMAGE_URL, INSPECTION_ID)).thenReturn(mockResult());
        AiAnalysisRouter router = new AiAnalysisRouter(mockService, externalService, properties(false, true));

        AIAnalysisResult result = router.analyzeImage(IMAGE_URL, INSPECTION_ID);

        assertThat(result.provider()).isEqualTo(AIAnalysisResult.PROVIDER_MOCK);
    }
}
