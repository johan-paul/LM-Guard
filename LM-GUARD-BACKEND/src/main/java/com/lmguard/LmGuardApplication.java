package com.lmguard;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * LM-GUARD - AI-assisted, evidence-first Legal Metrology inspection platform.
 *
 * <p>Architectural principle enforced throughout this codebase:
 * <strong>the AI extracts facts, the deterministic rule engine decides compliance,
 * and the human inspector makes the enforcement decision.</strong>
 * No language model is ever consulted to interpret the law.
 */
@SpringBootApplication
@ConfigurationPropertiesScan("com.lmguard.config.properties")
public class LmGuardApplication {

    public static void main(String[] args) {
        SpringApplication.run(LmGuardApplication.class, args);
    }
}
