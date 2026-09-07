package com.lmguard.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * @param provider       {@code supabase} or {@code local}. {@code local} writes to disk so the
 *                       full pipeline runs without a Supabase account.
 * @param localDirectory Root directory used by the local provider.
 * @param buckets        Logical bucket name -> configured bucket name.
 */
@ConfigurationProperties(prefix = "lmguard.storage")
public record StorageProperties(
        String provider,
        String localDirectory,
        Map<String, String> buckets
) {

    public static final String PACKAGE_IMAGES = "package-images";
    public static final String EVIDENCE = "evidence";
    public static final String REPORTS = "reports";
    public static final String ONLINE_LISTINGS = "online-listings";

    public StorageProperties {
        if (provider == null || provider.isBlank()) {
            provider = "local";
        }
        if (localDirectory == null || localDirectory.isBlank()) {
            localDirectory = "./storage-data";
        }
        buckets = buckets == null ? Map.of() : Map.copyOf(buckets);
    }

    public String bucket(String logicalName) {
        return buckets.getOrDefault(logicalName, logicalName);
    }

    public boolean isSupabase() {
        return "supabase".equalsIgnoreCase(provider);
    }
}
