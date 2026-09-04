package com.lmguard.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param mockMode      When true every analysis goes to {@code MockAIAnalysisService}.
 * @param fallbackToMock When true a failing external service degrades to the mock rather
 *                       than failing the inspection. Intended for demos; off in production.
 * @param serviceUrl    Base URL of the Python AI service.
 * @param analyzePath   Path appended to {@code serviceUrl} for the analyse call.
 * @param apiKey        Optional shared secret sent as {@code X-API-Key}.
 * @param timeoutMs     Connect and read timeout for the AI call.
 */
@ConfigurationProperties(prefix = "lmguard.ai")
public record AiProperties(
        boolean mockMode,
        boolean fallbackToMock,
        String serviceUrl,
        String analyzePath,
        String apiKey,
        int timeoutMs
) {

    public AiProperties {
        if (analyzePath == null || analyzePath.isBlank()) {
            analyzePath = "/analyze";
        }
        if (timeoutMs <= 0) {
            timeoutMs = 30_000;
        }
    }

    public String analyzeUrl() {
        String base = serviceUrl == null ? "" : serviceUrl.replaceAll("/+$", "");
        String path = analyzePath.startsWith("/") ? analyzePath : "/" + analyzePath;
        return base + path;
    }
}
