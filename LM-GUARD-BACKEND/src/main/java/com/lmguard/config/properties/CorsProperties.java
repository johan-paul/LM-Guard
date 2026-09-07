package com.lmguard.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Arrays;
import java.util.List;

/**
 * CORS settings for the React frontend.
 *
 * <p>Origins are an explicit list. {@code "*"} is rejected at startup under the {@code prod}
 * profile because credentialed requests plus a wildcard origin is not a configuration this
 * application should ever ship with.
 */
@ConfigurationProperties(prefix = "lmguard.cors")
public record CorsProperties(
        String allowedOrigins,
        String allowedMethods,
        String allowedHeaders,
        boolean allowCredentials,
        long maxAge
) {

    public CorsProperties {
        if (allowedOrigins == null || allowedOrigins.isBlank()) {
            allowedOrigins = "http://localhost:5173";
        }
        if (allowedMethods == null || allowedMethods.isBlank()) {
            allowedMethods = "GET,POST,PUT,PATCH,DELETE,OPTIONS";
        }
        if (allowedHeaders == null || allowedHeaders.isBlank()) {
            allowedHeaders = "*";
        }
        if (maxAge <= 0) {
            maxAge = 3600;
        }
    }

    public List<String> originList() {
        return split(allowedOrigins);
    }

    public List<String> methodList() {
        return split(allowedMethods);
    }

    public List<String> headerList() {
        return split(allowedHeaders);
    }

    public boolean hasWildcardOrigin() {
        return originList().contains("*");
    }

    private static List<String> split(String value) {
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(part -> !part.isEmpty())
                .toList();
    }
}
