package com.lmguard.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lmguard.config.properties.RulesProperties;
import com.lmguard.entity.Rule;
import com.lmguard.repository.RuleRepository;
import com.lmguard.rules.RuleCatalog;
import com.lmguard.rules.RuleDefinition;
import com.lmguard.rules.RuleSet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads the bundled demo ruleset into the {@code rules} table on first start.
 *
 * <p>Without this, a fresh Supabase project would have no rules and the very first inspection
 * would fail - a poor first five minutes for a new team member, and a bad surprise on demo
 * day. Seeding is idempotent: if the version already exists, nothing is written.
 *
 * <p>Controlled by {@code RULES_SEED_DEMO}, which defaults to off under the {@code prod}
 * profile. Demo rules have no business appearing in a production database by accident.
 */
@Component
@ConditionalOnProperty(name = "lmguard.rules.seed-demo-rules", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class DemoRuleSeeder implements ApplicationRunner {

    private final RuleRepository ruleRepository;
    private final RuleCatalog ruleCatalog;
    private final RulesProperties rulesProperties;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        RuleSet ruleSet;
        try {
            ruleSet = ruleCatalog.loadSampleFileOnly();
        } catch (RuntimeException ex) {
            log.error("Could not read the bundled sample ruleset; skipping seeding. {}", ex.getMessage());
            return;
        }

        String version = ruleSet.rulesetVersion() == null
                ? rulesProperties.activeVersion()
                : ruleSet.rulesetVersion();

        if (ruleRepository.existsByVersion(version)) {
            log.info("Ruleset '{}' already present in the database ({} active rules); not seeding",
                    version, ruleRepository.countByVersionAndActiveTrue(version));
            return;
        }

        List<Rule> rules = ruleSet.rules().stream()
                .map(definition -> toEntity(definition, version, ruleSet))
                .filter(java.util.Objects::nonNull)
                .toList();

        if (rules.isEmpty()) {
            log.warn("Bundled sample ruleset '{}' contained no usable rules", version);
            return;
        }

        ruleRepository.saveAll(rules);
        ruleCatalog.evict(version);
        log.info("Seeded {} DEMO rules for ruleset version '{}'. These are sample rules and are NOT "
                + "official Legal Metrology regulations.", rules.size(), version);
    }

    private Rule toEntity(RuleDefinition definition, String version, RuleSet ruleSet) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        putIfPresent(parameters, "required", definition.required());
        putIfPresent(parameters, "minConfidence", definition.minConfidence());
        putIfPresent(parameters, "pattern", definition.pattern());
        putIfPresent(parameters, "min", definition.min());
        putIfPresent(parameters, "max", definition.max());
        putIfPresent(parameters, "minLength", definition.minLength());
        putIfPresent(parameters, "finding", definition.finding());
        putIfPresent(parameters, "remediation", definition.remediation());

        String json;
        try {
            json = objectMapper.writeValueAsString(parameters);
        } catch (Exception ex) {
            log.error("Skipping rule {}: parameters could not be serialised: {}",
                    definition.ruleCode(), ex.getMessage());
            return null;
        }

        // The provenance of a rule travels with the rule, not just with the file it came from.
        String description = definition.description();
        if (ruleSet.isSample() && (description == null || !description.toUpperCase(java.util.Locale.ROOT)
                .contains("SAMPLE"))) {
            description = "SAMPLE RULE. " + (description == null ? "" : description);
        }

        return Rule.builder()
                .ruleCode(definition.ruleCode())
                .ruleName(definition.ruleName())
                .description(description)
                .fieldName(definition.field())
                .ruleType(definition.type())
                .ruleDefinition(json)
                .severity(definition.severityOrDefault())
                .version(version)
                .active(true)
                .build();
    }

    private void putIfPresent(Map<String, Object> map, String key, Object value) {
        if (value != null) {
            map.put(key, value);
        }
    }
}
