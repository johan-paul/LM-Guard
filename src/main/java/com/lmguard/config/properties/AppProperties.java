package com.lmguard.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param baseUrl Public base URL of this backend, used to build absolute URLs for
 *                locally stored files.
 */
@ConfigurationProperties(prefix = "lmguard")
public record AppProperties(String baseUrl) {

    public AppProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "http://localhost:8080";
        }
        baseUrl = baseUrl.replaceAll("/+$", "");
    }
}
