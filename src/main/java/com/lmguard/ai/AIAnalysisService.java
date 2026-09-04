package com.lmguard.ai;

import java.util.UUID;

/**
 * The seam between LM-GUARD and whatever performs image understanding.
 *
 * <p>Implementations extract <strong>facts</strong> from a package image. They must never
 * return, imply or encode a compliance decision - that belongs exclusively to
 * {@code RuleEngineService}. Keeping this boundary sharp is what allows the platform to
 * swap in a better model, or a different vendor, without any risk of changing what the law
 * is taken to mean.
 *
 * <p>Two implementations ship today:
 * <ul>
 *   <li>{@code MockAIAnalysisService} - deterministic sample facts, so the whole pipeline and
 *       the frontend work before the Python service exists.</li>
 *   <li>{@code ExternalAIAnalysisService} - HTTP client for the Python OCR/VLM service.</li>
 * </ul>
 */
public interface AIAnalysisService {

    /**
     * @param imageUrl     resolvable URL of the stored package image
     * @param inspectionId inspection the facts belong to; echoed back for correlation
     * @return the observations; never null
     * @throws com.lmguard.exception.AiServiceException when analysis could not be performed
     */
    AIAnalysisResult analyzeImage(String imageUrl, UUID inspectionId);

    /** Identifies which implementation this is, recorded on the inspection for audit. */
    String providerName();
}
