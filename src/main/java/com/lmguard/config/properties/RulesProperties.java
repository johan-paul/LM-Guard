package com.lmguard.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param activeVersion        Ruleset version recorded on every inspection.
 * @param sampleFile           Classpath location of the demo ruleset.
 * @param seedDemoRules        Insert the demo ruleset into the {@code rules} table on first start.
 * @param defaultMinConfidence Below this, a finding is INCONCLUSIVE rather than NON_COMPLIANT.
 *                             This threshold is what keeps a blurred photo from becoming a
 *                             prosecution.
 */
@ConfigurationProperties(prefix = "lmguard.rules")
public record RulesProperties(
        String activeVersion,
        String sampleFile,
        boolean seedDemoRules,
        double defaultMinConfidence
) {

    public RulesProperties {
        if (activeVersion == null || activeVersion.isBlank()) {
            activeVersion = "DEMO-2026.1";
        }
        if (sampleFile == null || sampleFile.isBlank()) {
            sampleFile = "classpath:rules/sample-rules.json";
        }
        if (defaultMinConfidence <= 0 || defaultMinConfidence > 1) {
            defaultMinConfidence = 0.70;
        }
    }
}
