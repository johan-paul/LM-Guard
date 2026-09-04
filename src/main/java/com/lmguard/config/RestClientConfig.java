package com.lmguard.config;

import com.lmguard.config.properties.AiProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * HTTP clients for the two external systems this backend talks to.
 *
 * <p>Both carry explicit timeouts. An AI service that hangs must not hold an inspector's
 * request open indefinitely - it should fail fast so the pipeline can fall back to the mock
 * or report the failure cleanly.
 */
@Configuration
public class RestClientConfig {

    /** Client for the external Python AI/OCR service. Timeout comes from AI_SERVICE_TIMEOUT_MS. */
    @Bean("aiRestTemplate")
    public RestTemplate aiRestTemplate(AiProperties aiProperties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(aiProperties.timeoutMs());
        factory.setReadTimeout(aiProperties.timeoutMs());
        return new RestTemplate(factory);
    }

    /** Client for Supabase Storage. Uploads get a longer read timeout than the AI call. */
    @Bean("storageRestTemplate")
    public RestTemplate storageRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(60_000);
        return new RestTemplate(factory);
    }
}
