package com.lmguard.entity;

import com.lmguard.entity.enums.Severity;
import com.lmguard.entity.enums.ViolationStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * A rule that did not pass for an inspection.
 *
 * <p>{@code ruleCode} is denormalised so a finding stays readable even after the referenced
 * rule row is retired, and {@code ruleId} is nullable for the same reason.
 */
@Entity
@Table(name = "violations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Violation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inspection_id", nullable = false)
    private Inspection inspection;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rule_id")
    private Rule rule;

    @Column(name = "rule_code", nullable = false, length = 50)
    private String ruleCode;

    @Column(name = "field_name", nullable = false, length = 100)
    private String fieldName;

    /** Plain-language statement of what was found, shown to the inspector. */
    @Column(name = "finding", nullable = false, columnDefinition = "TEXT")
    private String finding;

    @Column(name = "remediation", columnDefinition = "TEXT")
    private String remediation;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ViolationStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 20)
    @Builder.Default
    private Severity severity = Severity.MAJOR;

    /** Confidence that this finding is correct, 0..1. Carried through from the observation. */
    @Column(name = "decision_confidence", precision = 5, scale = 4)
    private BigDecimal decisionConfidence;

    /** The value actually observed, if any. Null when the declaration was absent. */
    @Column(name = "observed_value", columnDefinition = "TEXT")
    private String observedValue;

    @OneToMany(mappedBy = "violation", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Evidence> evidence = new ArrayList<>();

    public void addEvidence(Evidence item) {
        item.setViolation(this);
        item.setInspection(this.inspection);
        this.evidence.add(item);
    }
}
