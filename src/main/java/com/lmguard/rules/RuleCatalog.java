package com.lmguard.rules;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lmguard.config.properties.RulesProperties;
import com.lmguard.entity.Rule;
import com.lmguard.exception.ErrorCode;
import com.lmguard.exception.RuleEngineException;
import com.lmguard.repository.RuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Supplies the rules the engine evaluates.
 *
 * <p>The database is the source of truth, so an administrator can amend rules through the API
 * without a redeploy. The bundled {@code sample-rules.json} is the fallback for a fresh
 * database, which keeps a first run - and a demo on an empty Supabase project - working out
 * of the box.
 *
 * <p>Rulesets are cached per version. They are immutable by convention (amending a rule means
 * publishing a new version), so a cache cannot serve a stale decision procedure; the rules
 * API evicts explicitly after a write anyway.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RuleCatalog {

    private static final String SAMPLE_DISCLAIMER_FALLBACK =
            "DEMO / SAMPLE RULES. Not official Legal Metrology regulations and not legally verified.";

    private final RuleRepository ruleRepository;
    private final RulesProperties rulesProperties;
    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;

    private final java.util.Map<String, RuleSet> cache = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * @return the active rules for a version, from the database if present, otherwise from the
     *         bundled sample file
     * @throws RuleEngineException when neither source yields any rule
     */
    @Transactional(readOnly = true)
    public RuleSet load(String version) {
        String resolved = (version == null || version.isBlank()) ? rulesProperties.activeVersion() : version;
        RuleSet cached = cache.get(resolved);
        if (cached != null) {
            return cached;
        }

        RuleSet ruleSet = loadFromDatabase(resolved);
        if (ruleSet == null) {
            ruleSet = loadSampleFile(resolved);
        }
        if (ruleSet == null || ruleSet.rules().isEmpty()) {
            throw new RuleEngineException(ErrorCode.RULESET_NOT_FOUND,
                    "No active rules found for ruleset version '" + resolved + "'. "
                            + "Seed the demo ruleset (RULES_SEED_DEMO=true) or create rules via POST /api/rules.");
        }
        cache.put(resolved, ruleSet);
        return ruleSet;
    }

    /** Reads the bundled demo ruleset without touching the database. Used by the seeder. */
    public RuleSet loadSampleFileOnly() {
        RuleSet ruleSet = loadSampleFile(null);
        if (ruleSet == null) {
            throw new RuleEngineException("Bundled sample ruleset could not be read from "
                    + rulesProperties.sampleFile());
        }
        return ruleSet;
    }

    public void evict(String version) {
        if (version == null) {
            cache.clear();
        } else {
            cache.remove(version);
        }
    }

    private RuleSet loadFromDatabase(String version) {
        List<Rule> rules = ruleRepository.findByVersionAndActiveTrueOrderByRuleCodeAsc(version);
        if (rules.isEmpty()) {
            log.debug("No rules in the database for version '{}'; falling back to the bundled sample file", version);
            return null;
        }

        List<RuleDefinition> definitions = new ArrayList<>(rules.size());
        boolean anySample = false;
        for (Rule rule : rules) {
            RuleDefinition definition = toDefinition(rule);
            if (definition != null) {
                definitions.add(definition);
                anySample = anySample || isSampleRule(rule);
            }
        }
        if (definitions.isEmpty()) {
            return null;
        }

        log.debug("Loaded {} active rules for version '{}' from the database", definitions.size(), version);
        return new RuleSet(
                version,
                anySample ? RuleSet.STATUS_SAMPLE : "CUSTOM",
                anySample ? SAMPLE_DISCLAIMER_FALLBACK : null,
                List.of(),
                definitions);
    }

    /**
     * Merges the persisted columns with the JSON parameter blob. Columns win where both
     * define the same thing, because the columns are what the database constraints and the
     * admin API operate on.
     */
    private RuleDefinition toDefinition(Rule rule) {
        RuleDefinition parsed;
        try {
            parsed = objectMapper.readValue(rule.getRuleDefinition(), RuleDefinition.class);
        } catch (Exception ex) {
            // One malformed rule must not take down every inspection; skip it loudly instead.
            log.error("Skipping rule {} ({}): rule_definition is not valid JSON: {}",
                    rule.getRuleCode(), rule.getId(), ex.getMessage());
            return null;
        }

        return new RuleDefinition(
                rule.getId(),
                rule.getRuleCode(),
                rule.getRuleName(),
                rule.getDescription(),
                rule.getFieldName(),
                rule.getRuleType(),
                parsed.required(),
                parsed.minConfidence(),
                parsed.pattern(),
                parsed.min(),
                parsed.max(),
                parsed.minLength(),
                rule.getSeverity(),
                parsed.finding(),
                parsed.remediation());
    }

    private boolean isSampleRule(Rule rule) {
        String description = rule.getDescription();
        return description != null && description.toUpperCase(java.util.Locale.ROOT).contains("SAMPLE");
    }

    private RuleSet loadSampleFile(String requestedVersion) {
        Resource resource = resourceLoader.getResource(rulesProperties.sampleFile());
        if (!resource.exists()) {
            log.error("Sample ruleset not found at {}", rulesProperties.sampleFile());
            return null;
        }
        try (InputStream in = resource.getInputStream()) {
            RuleSet ruleSet = objectMapper.readValue(in, RuleSet.class);
            if (requestedVersion != null
                    && ruleSet.rulesetVersion() != null
                    && !requestedVersion.equals(ruleSet.rulesetVersion())) {
                log.warn("Requested ruleset version '{}' but the bundled sample file declares '{}'. "
                                + "Using the bundled rules; create the requested version via POST /api/rules "
                                + "if that is not what you want.",
                        requestedVersion, ruleSet.rulesetVersion());
            }
            log.info("Loaded {} rules from the bundled sample ruleset '{}'",
                    ruleSet.rules().size(), ruleSet.rulesetVersion());
            return ruleSet;
        } catch (IOException ex) {
            log.error("Could not read the sample ruleset from {}", rulesProperties.sampleFile(), ex);
            return null;
        }
    }
}
