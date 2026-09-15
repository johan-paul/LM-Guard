package com.lmguard.service;

import com.lmguard.ai.AIAnalysisResult;
import com.lmguard.ai.AIAnalysisService;
import com.lmguard.ai.ExtractedFact;
import com.lmguard.config.properties.RulesProperties;
import com.lmguard.dto.inspection.InspectionResponse;
import com.lmguard.dto.product.ProductCreateRequest;
import com.lmguard.entity.Evidence;
import com.lmguard.entity.ExtractedField;
import com.lmguard.entity.Inspection;
import com.lmguard.entity.OnlineListing;
import com.lmguard.entity.Product;
import com.lmguard.entity.RiskScore;
import com.lmguard.entity.Violation;
import com.lmguard.entity.enums.ComplianceStatus;
import com.lmguard.entity.enums.InspectionStatus;
import com.lmguard.entity.enums.ProductField;
import com.lmguard.entity.enums.VersionSource;
import com.lmguard.entity.enums.ViolationStatus;
import com.lmguard.evidence.EvidenceRequest;
import com.lmguard.evidence.EvidenceService;
import com.lmguard.exception.BadRequestException;
import com.lmguard.exception.ErrorCode;
import com.lmguard.exception.ResourceNotFoundException;
import com.lmguard.mapper.InspectionMapper;
import com.lmguard.repository.ExtractedFieldRepository;
import com.lmguard.repository.InspectionRepository;
import com.lmguard.repository.OnlineListingRepository;
import com.lmguard.repository.RiskScoreRepository;
import com.lmguard.repository.ViolationRepository;
import com.lmguard.risk.RiskAssessment;
import com.lmguard.risk.RiskEngineService;
import com.lmguard.risk.RiskInput;
import com.lmguard.rules.ComplianceResult;
import com.lmguard.rules.InspectionFacts;
import com.lmguard.rules.RuleEngineService;
import com.lmguard.rules.RuleFinding;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The inspection analysis pipeline - the spine of LM-GUARD.
 *
 * <pre>
 *   package image (already stored)
 *          |
 *          v
 *   AI / OCR / CV        ->  FACTS: what is visible, where, and how sure
 *          |
 *          v
 *   save extracted fields    (observations persisted before anything is judged)
 *          |
 *          v
 *   deterministic rule engine ->  COMPLIANT / NON_COMPLIANT / INCONCLUSIVE
 *          |
 *          v
 *   create violations         (one per failing rule)
 *          |
 *          v
 *   create evidence           (image region + confidence for every finding)
 *          |
 *          v
 *   calculate risk            (transparent weighted score)
 *          |
 *          v
 *   save result
 * </pre>
 *
 * <p>The ordering is not incidental. Facts are persisted before evaluation so a verdict can
 * always be re-derived from what was actually observed. Evidence is written per finding so no
 * violation can exist without its proof. The ruleset version is stamped on the inspection so
 * the decision stays reproducible after the rules change.
 *
 * <p>Everything here runs in one transaction: an inspection is either fully written or not at
 * all. Recording a failure is deliberately <em>not</em> done here - see
 * {@link InspectionStatusWriter} for why.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InspectionAnalysisService {

    private final InspectionRepository inspectionRepository;
    private final ExtractedFieldRepository extractedFieldRepository;
    private final ViolationRepository violationRepository;
    private final RiskScoreRepository riskScoreRepository;
    private final OnlineListingRepository onlineListingRepository;

    private final ProductService productService;
    private final AIAnalysisService aiAnalysisService;
    private final RuleEngineService ruleEngineService;
    private final EvidenceService evidenceService;
    private final RiskEngineService riskEngineService;

    private final InspectionMapper inspectionMapper;
    private final RulesProperties rulesProperties;

    /**
     * Runs the whole pipeline for one inspection.
     *
     * @throws BadRequestException when no image has been uploaded yet
     * @throws com.lmguard.exception.AiServiceException when analysis could not be performed
     */
    @Transactional
    public InspectionResponse run(UUID inspectionId) {
        Inspection inspection = inspectionRepository.findDetailedById(inspectionId)
                .orElseThrow(() -> ResourceNotFoundException.of(ErrorCode.INSPECTION_NOT_FOUND, inspectionId));

        if (inspection.getImageUrl() == null || inspection.getImageUrl().isBlank()) {
            throw new BadRequestException(ErrorCode.IMAGE_REQUIRED,
                    "Upload a package image with POST /api/inspections/%s/image before analysing"
                            .formatted(inspectionId));
        }

        // --- 1. AI: facts only, never a verdict ---
        AIAnalysisResult analysis = aiAnalysisService.analyzeImage(inspection.getImageUrl(), inspectionId);
        inspection.setAiProvider(analysis.provider());

        // --- 1b. identify the product from what the AI actually read on the package. Nobody
        // types this in by hand any more: an admin-assigned case may already carry a product
        // (set via PATCH .../product before the officer ever opens it), in which case that
        // stands - this only fills the gap when analysis is the first thing to touch the
        // inspection. A commodity name the AI couldn't read still gets a product row (never
        // block the pipeline on it) - it's just named accordingly and can be corrected later.
        if (inspection.getProduct() == null) {
            inspection.setProduct(resolveProductFromAnalysis(inspection, analysis));
        } else if (inspection.getProduct().getBrand() == null && inspection.getEstablishment() != null) {
            inspection.getProduct().setBrand(inspection.getEstablishment());
        }

        // --- 2. persist the observations before judging them --- (this replaces AI-derived
        // fields only; any inspector-submitted measurement from a previous run, e.g.
        // NUMERAL_HEIGHT_MM, survives untouched - see persistExtractedFields)
        persistExtractedFields(inspection, analysis);

        // --- product declaration history --- (independent of the evaluate/persist steps
        // below; needs `analysis` specifically, so it stays here rather than in the shared
        // evaluateAndPersist() that submitMeasurement() also calls)
        recordProductVersion(inspection, analysis);

        // --- 3-7: deterministic rule engine decides compliance, violations, risk, final state.
        // Facts are rebuilt from everything now persisted (not just this run's AI result) so a
        // measurement submitted on an earlier run stays part of the evaluation, not just the
        // database row - see submitMeasurement, which sources facts the same way.
        List<ExtractedField> fields = extractedFieldRepository.findByInspectionIdOrderByFieldNameAsc(inspectionId);
        InspectionFacts facts = InspectionFacts.from(inspectionId, fields);
        InspectionResponse response = evaluateAndPersist(inspection, facts, fields, analysis.warnings());

        log.info("Inspection {} analysed via AI (provider {})", inspectionId, analysis.provider());
        return response;
    }

    /**
     * Re-evaluates an inspection against a single inspector-submitted measurement (currently
     * only {@link ProductField#NUMERAL_HEIGHT_MM}, captured via AR depth measurement rather
     * than the AI vision pipeline - see that constant's javadoc for why it can't come from
     * {@code /analyze}). Upserts the field, then re-runs the SAME evaluate-persist-risk
     * pipeline {@link #run} uses, sourced from everything currently persisted for this
     * inspection rather than a fresh {@link AIAnalysisResult} - the photo is not re-analysed.
     *
     * @throws BadRequestException if {@code fieldName} is not one this endpoint accepts
     * @throws com.lmguard.exception.ResourceNotFoundException if the inspection does not exist
     */
    @Transactional
    public InspectionResponse submitMeasurement(UUID inspectionId, String fieldName, String value, Double confidence) {
        Inspection inspection = inspectionRepository.findDetailedById(inspectionId)
                .orElseThrow(() -> ResourceNotFoundException.of(ErrorCode.INSPECTION_NOT_FOUND, inspectionId));

        String normalizedField = fieldName == null ? "" : fieldName.trim().toUpperCase(Locale.ROOT);
        if (!ProductField.INSPECTOR_MEASURED_FIELDS.contains(normalizedField)) {
            throw new BadRequestException(ErrorCode.VALIDATION_FAILED,
                    "fieldName must be one of " + ProductField.INSPECTOR_MEASURED_FIELDS + ", got: " + fieldName);
        }

        ExtractedField field = extractedFieldRepository.findByInspectionIdAndFieldName(inspectionId, normalizedField)
                .orElseGet(() -> ExtractedField.builder().inspection(inspection).fieldName(normalizedField).build());
        field.setFieldValue(value);
        field.setConfidence(toConfidence(confidence == null ? 1.0 : confidence));
        // A physical measurement has no image region to point at, and no raw OCR text behind it -
        // clear both in case this field previously held (stale) values from a different source.
        field.setBoundingBoxX(null);
        field.setBoundingBoxY(null);
        field.setBoundingBoxWidth(null);
        field.setBoundingBoxHeight(null);
        field.setRawText(null);
        extractedFieldRepository.save(field);

        List<ExtractedField> fields = extractedFieldRepository.findByInspectionIdOrderByFieldNameAsc(inspectionId);
        InspectionFacts facts = InspectionFacts.from(inspectionId, fields);

        InspectionResponse response = evaluateAndPersist(inspection, facts, fields, List.of());
        log.info("Inspection {} re-evaluated after inspector-submitted measurement {}={}",
                inspectionId, normalizedField, value);
        return response;
    }

    // ------------------------------------------------------------------
    // Pipeline steps
    // ------------------------------------------------------------------

    /**
     * Deterministic rule engine decides compliance, then violations/evidence, risk, and the
     * inspection's final advisory state are all persisted from that one verdict. Shared by
     * {@link #run} (fed fresh AI facts) and {@link #submitMeasurement} (fed facts rebuilt from
     * whatever is currently persisted) so the two entry points can never drift out of sync.
     */
    private InspectionResponse evaluateAndPersist(Inspection inspection, InspectionFacts facts,
                                                   List<ExtractedField> fields, List<String> warnings) {
        ComplianceResult compliance = ruleEngineService.evaluate(facts, rulesProperties.activeVersion());
        inspection.setRulesetVersion(compliance.rulesetVersion());

        List<Violation> violations = persistViolations(inspection, compliance);

        RiskAssessment risk = assessRisk(inspection, compliance);
        RiskScore riskScore = persistRiskScore(inspection, risk);

        // The rule engine's verdict is recorded as an advisory suggestion only. It never
        // becomes the inspection's authoritative status - that is set exclusively by the
        // inspector's own POST .../submit, independent of what AI/rules suggested here.
        inspection.setAiSuggestedStatus(compliance.status().toInspectionStatus());
        inspection.setStatus(InspectionStatus.IN_PROGRESS);
        inspection.setOverallConfidence(toConfidence(compliance.overallConfidence()));
        inspection.setRiskScore(risk.totalScore());
        inspection.setRiskLevel(risk.riskLevel());
        inspection.setFailureReason(null);
        inspection.setAnalyzedAt(Instant.now());
        inspectionRepository.save(inspection);

        log.info("Inspection {} evaluated: AI suggests {} (risk {}/{}, ruleset {}, {} violation(s))",
                inspection.getId(), inspection.getAiSuggestedStatus(), risk.totalScore(), risk.riskLevel(),
                compliance.rulesetVersion(), violations.size());

        return inspectionMapper.toResponse(inspection, fields, violations, riskScore, warnings);
    }

    /** Registers a new product from the AI's own reading of the package - COMMODITY_NAME and
     * MANUFACTURER, the two declarations Legal Metrology requires on every principal display
     * panel. Either can come back not-detected (low OCR confidence, glare, a cropped panel);
     * this never blocks on that, since a human can always correct the product afterward via
     * PATCH .../product - it just means an inspector reviewing the case sees "Unidentified
     * product" instead of a guessed name. */
    private Product resolveProductFromAnalysis(Inspection inspection, AIAnalysisResult analysis) {
        String name = analysis.fact(ProductField.COMMODITY_NAME)
                .filter(ExtractedFact::isPresent)
                .map(ExtractedFact::value)
                .orElse("Unidentified product");
        String brand = analysis.fact(ProductField.MANUFACTURER)
                .filter(ExtractedFact::isPresent)
                .map(ExtractedFact::value)
                .orElse(inspection != null ? inspection.getEstablishment() : null);
        UUID createdId = productService.create(new ProductCreateRequest(name, brand, null, null)).id();
        return productService.requireById(createdId);
    }

    /** Replaces any previous AI-derived observations, so re-analysing an inspection is
     * idempotent - but never touches fields an inspector submitted directly (see
     * ProductField.INSPECTOR_MEASURED_FIELDS): a fresh photo re-analysis has nothing to say
     * about a physical measurement taken separately, and must not silently erase it. */
    private List<ExtractedField> persistExtractedFields(Inspection inspection, AIAnalysisResult analysis) {
        extractedFieldRepository.deleteByInspectionIdAndFieldNameNotIn(
                inspection.getId(), ProductField.INSPECTOR_MEASURED_FIELDS);
        extractedFieldRepository.flush();

        List<ExtractedField> fields = analysis.facts().stream()
                .map(fact -> ExtractedField.builder()
                        .inspection(inspection)
                        .fieldName(fact.fieldName())
                        .fieldValue(fact.value())
                        .confidence(toConfidence(fact.confidence()))
                        .boundingBoxX(fact.boundingBox().x())
                        .boundingBoxY(fact.boundingBox().y())
                        .boundingBoxWidth(fact.boundingBox().width())
                        .boundingBoxHeight(fact.boundingBox().height())
                        .rawText(fact.rawText())
                        .build())
                .toList();

        return extractedFieldRepository.saveAll(fields);
    }

    /** One violation per failing rule, each with an evidence record - including absences. */
    private List<Violation> persistViolations(Inspection inspection, ComplianceResult compliance) {
        List<Violation> existing = violationRepository.findByInspectionIdOrderByCreatedAtAsc(inspection.getId());
        if (!existing.isEmpty()) {
            violationRepository.deleteAll(existing);
            violationRepository.flush();
        }

        List<Violation> saved = new ArrayList<>();
        for (RuleFinding finding : compliance.breaches()) {
            Violation violation = violationRepository.save(Violation.builder()
                    .inspection(inspection)
                    .ruleCode(finding.ruleCode())
                    .fieldName(finding.fieldName())
                    .finding(finding.finding())
                    .remediation(finding.remediation())
                    .status(finding.status() == ComplianceStatus.NON_COMPLIANT
                            ? ViolationStatus.NON_COMPLIANT
                            : ViolationStatus.INCONCLUSIVE)
                    .severity(finding.severity())
                    .decisionConfidence(toConfidence(finding.decisionConfidence()))
                    .observedValue(finding.observedValue())
                    .build());

            Evidence evidence = evidenceService.record(new EvidenceRequest(
                    violation,
                    inspection,
                    inspection.getImageUrl(),
                    inspection.getImagePath(),
                    finding.box(),
                    finding.decisionConfidence(),
                    describeEvidence(finding)));

            violation.getEvidence().add(evidence);
            saved.add(violation);
        }
        return saved;
    }

    private String describeEvidence(RuleFinding finding) {
        if (finding.box() != null && finding.box().isComplete()) {
            return "Region of the package image examined for '%s' under rule %s. Observed value: %s"
                    .formatted(finding.fieldName(), finding.ruleCode(),
                            finding.observedValue() == null ? "none" : finding.observedValue());
        }
        // No box does not always mean nothing was read: the AI/OCR layer can recognise a value
        // (e.g. from the vision model's own reading) without being able to match it back to a
        // specific OCR text region on the image - two different situations that read very
        // differently to an inspector and must not share one message.
        if (finding.observedValue() != null && !finding.observedValue().isBlank()) {
            return ("Declaration '%s' was read as '%s' under rule %s, but its exact position on "
                    + "the package image could not be pinpointed. Manual verification of its "
                    + "location is required.")
                    .formatted(finding.fieldName(), finding.observedValue(), finding.ruleCode());
        }
        return "No region of the package image was found to contain '%s'. Rule %s was evaluated "
                .formatted(finding.fieldName(), finding.ruleCode())
                + "against the absence of this declaration.";
    }

    private RiskAssessment assessRisk(Inspection inspection, ComplianceResult compliance) {
        UUID productId = inspection.getProduct().getId();

        long previousNonCompliant = inspectionRepository.countPreviousNonCompliant(productId, inspection.getId());
        long productChanges = productService.changeCount(productId);

        Set<String> currentRuleCodes = compliance.breaches().stream()
                .filter(finding -> finding.status() == ComplianceStatus.NON_COMPLIANT)
                .map(RuleFinding::ruleCode)
                .collect(Collectors.toCollection(HashSet::new));

        Set<String> previousRuleCodes = new HashSet<>(
                violationRepository.findPreviousRuleCodesForProduct(productId, inspection.getId()));

        return riskEngineService.assess(new RiskInput(
                previousNonCompliant,
                productChanges,
                detectOnlineMismatch(productId, inspection.getId()),
                inspection.getProduct().getCategory(),
                currentRuleCodes,
                previousRuleCodes));
    }

    /**
     * Compares the most recent captured online listing against what was read off the package.
     *
     * <p>With no listing on file there is nothing to compare, and the factor stays off. A
     * missing listing must never read as a mismatch - that would penalise products simply for
     * not having been scraped yet.
     */
    private boolean detectOnlineMismatch(UUID productId, UUID inspectionId) {
        Optional<OnlineListing> listing =
                onlineListingRepository.findFirstByProductIdOrderByCapturedAtDesc(productId);
        if (listing.isEmpty()) {
            return false;
        }
        Map<String, String> observed = observedValues(inspectionId);
        OnlineListing online = listing.get();

        return differs(observed.get(ProductField.MRP), online.getMrp())
                || differs(observed.get(ProductField.NET_QUANTITY), online.getQuantity())
                || differs(observed.get(ProductField.MANUFACTURER), online.getManufacturer());
    }

    private boolean differs(String packageValue, String onlineValue) {
        if (packageValue == null || onlineValue == null) {
            return false;
        }
        return !normalise(packageValue).equals(normalise(onlineValue));
    }

    /** Strips currency markers, spacing and punctuation so "Rs. 99.00" and "₹99" compare equal. */
    private String normalise(String value) {
        return value.trim().toLowerCase(Locale.ROOT).replaceAll("(rs|inr|₹|\\s|\\.|,)", "");
    }

    private Map<String, String> observedValues(UUID inspectionId) {
        Map<String, String> values = new HashMap<>();
        for (ExtractedField field : extractedFieldRepository.findByInspectionIdOrderByFieldNameAsc(inspectionId)) {
            if (field.getFieldValue() != null && !field.getFieldValue().isBlank()) {
                values.put(field.getFieldName(), field.getFieldValue());
            }
        }
        return values;
    }

    private RiskScore persistRiskScore(Inspection inspection, RiskAssessment assessment) {
        RiskScore score = riskScoreRepository.findByInspectionId(inspection.getId())
                .orElseGet(() -> RiskScore.builder()
                        .product(inspection.getProduct())
                        .inspection(inspection)
                        .build());

        score.setProduct(inspection.getProduct());
        score.setInspection(inspection);
        score.setPreviousViolations(assessment.previousViolations());
        score.setProductChanges(assessment.productChanges());
        score.setOnlineMismatch(assessment.onlineMismatch());
        score.setCategoryRisk(assessment.categoryRisk());
        score.setRepeatIssue(assessment.repeatIssue());
        score.setTotalScore(assessment.totalScore());
        score.setRiskLevel(assessment.riskLevel());
        score.setExplanation(assessment.explanation());

        return riskScoreRepository.save(score);
    }

    private void recordProductVersion(Inspection inspection, AIAnalysisResult analysis) {
        Map<String, String> declared = new HashMap<>();
        for (String fieldName : ProductField.VERSIONED_FIELDS) {
            analysis.fact(fieldName)
                    .filter(ExtractedFact::isPresent)
                    .ifPresent(fact -> declared.put(fieldName, fact.value()));
        }
        productService.recordVersionIfChanged(inspection.getProduct(), declared, VersionSource.INSPECTION);
    }

    /** Stored with 4 decimal places to match the NUMERIC(5,4) columns. */
    private BigDecimal toConfidence(double value) {
        double clamped = Math.max(0, Math.min(1, value));
        return BigDecimal.valueOf(clamped).setScale(4, RoundingMode.HALF_UP);
    }

    /** Exposed for tests and for callers that need the terminal states in one place. */
    public static boolean isTerminal(InspectionStatus status) {
        return status != null && status.isTerminal();
    }
}
