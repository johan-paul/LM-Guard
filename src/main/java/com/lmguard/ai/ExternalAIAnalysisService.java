package com.lmguard.ai;

import com.lmguard.ai.dto.AiAnalyzeRequest;
import com.lmguard.ai.dto.AiAnalyzeResponse;
import com.lmguard.config.properties.AiProperties;
import com.lmguard.entity.enums.ProductField;
import com.lmguard.exception.AiServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * HTTP client for the external Python AI/OCR service.
 *
 * <p>Wire format is documented on {@link AiAnalyzeRequest} and {@link AiAnalyzeResponse}. The
 * service is expected to expose {@code POST {AI_SERVICE_URL}{AI_SERVICE_ANALYZE_PATH}}.
 *
 * <p>Anything the service returns is treated as untrusted input: confidences are clamped to
 * 0..1, unnamed fields are dropped, and a malformed or empty response raises
 * {@link AiServiceException} rather than silently producing an inspection based on nothing.
 */
@Service
@Slf4j
public class ExternalAIAnalysisService implements AIAnalysisService {

    /** Declarations LM-GUARD asks the AI service to look for. */
    private static final List<String> REQUESTED_FIELDS = List.of(
            ProductField.MRP,
            ProductField.NET_QUANTITY,
            ProductField.MANUFACTURER,
            ProductField.ORIGIN,
            ProductField.CONSUMER_CARE,
            ProductField.MANUFACTURE_DATE,
            ProductField.EXPIRY_DATE,
            ProductField.BATCH_NUMBER,
            ProductField.COMMODITY_NAME);

    private final RestTemplate restTemplate;
    private final AiProperties aiProperties;

    public ExternalAIAnalysisService(@Qualifier("aiRestTemplate") RestTemplate restTemplate,
                                     AiProperties aiProperties) {
        this.restTemplate = restTemplate;
        this.aiProperties = aiProperties;
    }

    @Override
    public AIAnalysisResult analyzeImage(String imageUrl, UUID inspectionId) {
        String url = aiProperties.analyzeUrl();
        long startedAt = System.currentTimeMillis();
        log.info("Calling external AI service {} for inspection {}", url, inspectionId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        if (aiProperties.apiKey() != null && !aiProperties.apiKey().isBlank()) {
            headers.set("X-API-Key", aiProperties.apiKey());
        }

        AiAnalyzeRequest request = new AiAnalyzeRequest(imageUrl, inspectionId, REQUESTED_FIELDS);

        AiAnalyzeResponse body;
        try {
            ResponseEntity<AiAnalyzeResponse> response =
                    restTemplate.postForEntity(url, new HttpEntity<>(request, headers), AiAnalyzeResponse.class);
            body = response.getBody();
        } catch (RestClientException ex) {
            throw new AiServiceException(
                    "AI service call failed for inspection " + inspectionId + ": " + ex.getMessage(), ex);
        }

        if (body == null || body.fields() == null || body.fields().isEmpty()) {
            throw new AiServiceException(
                    "AI service returned no fields for inspection " + inspectionId);
        }

        List<ExtractedFact> facts = toFacts(body);
        if (facts.isEmpty()) {
            throw new AiServiceException(
                    "AI service returned only unusable fields for inspection " + inspectionId);
        }

        long elapsed = System.currentTimeMillis() - startedAt;
        log.info("External AI returned {} facts for inspection {} in {} ms", facts.size(), inspectionId, elapsed);

        return new AIAnalysisResult(
                inspectionId,
                facts,
                AIAnalysisResult.PROVIDER_EXTERNAL,
                body.modelVersion(),
                elapsed,
                body.warnings() == null ? List.of() : body.warnings());
    }

    @Override
    public String providerName() {
        return AIAnalysisResult.PROVIDER_EXTERNAL;
    }

    private List<ExtractedFact> toFacts(AiAnalyzeResponse body) {
        List<ExtractedFact> facts = new ArrayList<>();
        for (AiAnalyzeResponse.AiField field : body.fields()) {
            if (field == null || field.name() == null || field.name().isBlank()) {
                log.warn("Dropping AI field with no name: {}", field);
                continue;
            }
            double confidence = field.confidence() == null ? 0.0 : field.confidence();
            BoundingBox box = field.boundingBox() == null
                    ? BoundingBox.absent()
                    : new BoundingBox(field.boundingBox().x(), field.boundingBox().y(),
                            field.boundingBox().width(), field.boundingBox().height());

            String value = field.value() == null || field.value().isBlank() ? null : field.value().trim();

            // The ExtractedFact constructor clamps confidence into 0..1.
            facts.add(new ExtractedFact(
                    field.name().trim().toUpperCase(java.util.Locale.ROOT),
                    value,
                    confidence,
                    box,
                    field.rawText()));
        }
        return facts;
    }
}
