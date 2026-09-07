package com.lmguard.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param url            Project URL, e.g. {@code https://xxxx.supabase.co}.
 * @param anonKey        Public key. Safe for the browser; not used for server uploads.
 * @param serviceRoleKey Secret key used for server-side storage writes. Never expose this
 *                       to the React client - it bypasses row level security.
 */
@ConfigurationProperties(prefix = "lmguard.supabase")
public record SupabaseProperties(
        String url,
        String anonKey,
        String serviceRoleKey
) {

    public boolean isConfigured() {
        return url != null && !url.isBlank()
                && serviceRoleKey != null && !serviceRoleKey.isBlank();
    }

    public String baseUrl() {
        return url == null ? "" : url.replaceAll("/+$", "");
    }
}
