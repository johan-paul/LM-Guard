package com.lmguard.service;

import com.lmguard.config.properties.RulesProperties;
import com.lmguard.dto.rule.RulePublishRequest;
import com.lmguard.dto.rule.RuleResponse;
import com.lmguard.dto.rule.RuleSetResponse;
import com.lmguard.dto.rule.RuleUpsertRequest;
import com.lmguard.entity.Rule;
import com.lmguard.entity.enums.Severity;
import com.lmguard.exception.BadRequestException;
import com.lmguard.exception.ErrorCode;
import com.lmguard.exception.ResourceNotFoundException;
import com.lmguard.mapper.RuleMapper;
import com.lmguard.repository.InspectionRepository;
import com.lmguard.repository.RuleRepository;
import com.lmguard.rules.RuleCatalog;
import com.lmguard.rules.RuleSet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Rule management, restricted to administrators.
 *
 * <p>Amending a rule creates or updates a row within a <em>version</em>; historical
 * inspections keep referencing the version they were judged under, so changing the rules
 * never rewrites the past.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RuleAdminService {

    private static final String SAMPLE_DISCLAIMER =
            "These rules are DEMO / SAMPLE scaffolding for the LM-GUARD MVP. They are not the "
                    + "Legal Metrology Act or Rules, are not legally verified, and must be replaced with a "
                    + "ruleset reviewed by a qualified Legal Metrology authority before any real-world use.";

    private final RuleRepository ruleRepository;
    private final RuleCatalog ruleCatalog;
    private final RuleMapper ruleMapper;
    private final RulesProperties rulesProperties;
    private final InspectionRepository inspectionRepository;

    @Transactional(readOnly = true)
    public RuleSetResponse activeRuleSet(String version) {
        String resolved = (version == null || version.isBlank()) ? rulesProperties.activeVersion() : version;
        RuleSet ruleSet = ruleCatalog.load(resolved);

        List<Rule> stored = ruleRepository.findByVersionOrderByRuleCodeAsc(resolved);
        List<RuleResponse> rules = stored.isEmpty()
                ? ruleSet.rules().stream()
                        .map(definition -> new RuleResponse(
                                null,
                                definition.ruleCode(),
                                definition.ruleName(),
                                definition.description(),
                                definition.field(),
                                definition.type(),
                                null,
                                definition.severityOrDefault(),
                                ruleSet.rulesetVersion(),
                                true,
                                null,
                                null))
                        .toList()
                : stored.stream().map(ruleMapper::toResponse).toList();

        return new RuleSetResponse(
                ruleSet.rulesetVersion() == null ? resolved : ruleSet.rulesetVersion(),
                ruleSet.status() == null ? "UNKNOWN" : ruleSet.status(),
                ruleSet.disclaimer() == null && ruleSet.isSample() ? SAMPLE_DISCLAIMER : ruleSet.disclaimer(),
                rules.size(),
                inspectionRepository.existsByRulesetVersion(resolved),
                rules);
    }

    @Transactional
    public RuleResponse upsert(RuleUpsertRequest request) {
        String version = request.version().trim();
        // Once an inspection has been judged under a version, its rules must be treated as a
        // historical record, not a document still being edited - allowing further changes here
        // would silently rewrite what that past verdict was actually judged against. The fix is
        // always the same: publish(...) a new version and edit that instead.
        if (inspectionRepository.existsByRulesetVersion(version)) {
            throw new BadRequestException(ErrorCode.RULESET_VERSION_LOCKED,
                    ErrorCode.RULESET_VERSION_LOCKED.getDefaultMessage());
        }

        Rule rule = ruleRepository.findByRuleCodeAndVersion(request.ruleCode(), request.version())
                .orElseGet(Rule::new);

        rule.setRuleCode(request.ruleCode().trim());
        rule.setRuleName(request.ruleName().trim());
        rule.setDescription(request.description());
        rule.setFieldName(request.fieldName().trim().toUpperCase(java.util.Locale.ROOT));
        rule.setRuleType(request.ruleType());
        rule.setRuleDefinition(request.ruleDefinition());
        rule.setSeverity(request.severity() == null ? Severity.MAJOR : request.severity());
        rule.setVersion(request.version().trim());
        rule.setActive(request.active() == null || request.active());

        Rule saved = ruleRepository.save(rule);
        ruleCatalog.evict(saved.getVersion());
        log.info("Rule {} of ruleset {} saved by an administrator", saved.getRuleCode(), saved.getVersion());
        return ruleMapper.toResponse(saved);
    }

    @Transactional
    public RuleResponse setActive(UUID ruleId, boolean active) {
        Rule rule = ruleRepository.findById(ruleId)
                .orElseThrow(() -> ResourceNotFoundException.of(ErrorCode.RULE_NOT_FOUND, ruleId));
        if (inspectionRepository.existsByRulesetVersion(rule.getVersion())) {
            throw new BadRequestException(ErrorCode.RULESET_VERSION_LOCKED,
                    ErrorCode.RULESET_VERSION_LOCKED.getDefaultMessage());
        }
        rule.setActive(active);
        Rule saved = ruleRepository.save(rule);
        ruleCatalog.evict(saved.getVersion());
        log.info("Rule {} of ruleset {} set active={}", saved.getRuleCode(), saved.getVersion(), active);
        return ruleMapper.toResponse(saved);
    }

    /**
     * Clones every rule row of {@code sourceVersion} into a fresh {@code newVersion} - the
     * actual "publish a new version" action. Without this, changing a single rule after a
     * version has been locked by {@link #upsert} would mean retyping every other rule in that
     * ruleset by hand just to carry them forward unchanged.
     */
    @Transactional
    public RuleSetResponse publish(RulePublishRequest request) {
        String sourceVersion = request.sourceVersion().trim();
        String newVersion = request.newVersion().trim();

        if (ruleRepository.existsByVersion(newVersion)) {
            throw new BadRequestException(ErrorCode.RULESET_VERSION_EXISTS,
                    ErrorCode.RULESET_VERSION_EXISTS.getDefaultMessage());
        }

        List<Rule> sourceRules = ruleRepository.findByVersionOrderByRuleCodeAsc(sourceVersion);
        if (sourceRules.isEmpty()) {
            throw new ResourceNotFoundException(ErrorCode.RULESET_NOT_FOUND,
                    "No rules found for source version '" + sourceVersion + "'");
        }

        List<Rule> cloned = sourceRules.stream()
                .map(source -> Rule.builder()
                        .ruleCode(source.getRuleCode())
                        .ruleName(source.getRuleName())
                        .description(source.getDescription())
                        .fieldName(source.getFieldName())
                        .ruleType(source.getRuleType())
                        .ruleDefinition(source.getRuleDefinition())
                        .severity(source.getSeverity())
                        .version(newVersion)
                        .active(source.isActive())
                        .build())
                .toList();
        ruleRepository.saveAll(cloned);
        ruleCatalog.evict(newVersion);

        log.info("Published ruleset {} as a new draft version {} ({} rules cloned) by an administrator",
                sourceVersion, newVersion, cloned.size());
        return activeRuleSet(newVersion);
    }
}
