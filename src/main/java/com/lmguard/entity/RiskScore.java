package com.lmguard.entity;

import com.lmguard.entity.enums.RiskLevel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A fully itemised risk assessment for one inspection.
 *
 * <p>Each component's contribution is stored, not just the total, so the score can always be
 * explained to an inspector. This is a transparent weighted model by design; no machine
 * learning is involved and none should be added before the weights are validated in the field.
 */
@Entity
@Table(name = "risk_scores")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RiskScore extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inspection_id", nullable = false, unique = true)
    private Inspection inspection;

    /** Points contributed by prior non-compliant inspections of this product. */
    @Column(name = "previous_violations", nullable = false)
    @Builder.Default
    private int previousViolations = 0;

    /** Points contributed by changes to the product's declared facts over time. */
    @Column(name = "product_changes", nullable = false)
    @Builder.Default
    private int productChanges = 0;

    /** Points contributed by a mismatch against a captured online listing. */
    @Column(name = "online_mismatch", nullable = false)
    @Builder.Default
    private int onlineMismatch = 0;

    /** Points contributed by the product belonging to a higher-risk category. */
    @Column(name = "category_risk", nullable = false)
    @Builder.Default
    private int categoryRisk = 0;

    /** Points contributed by the same rule failing again on this product. */
    @Column(name = "repeat_issue", nullable = false)
    @Builder.Default
    private int repeatIssue = 0;

    @Column(name = "total_score", nullable = false)
    @Builder.Default
    private int totalScore = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false, length = 20)
    private RiskLevel riskLevel;

    @Column(name = "explanation", columnDefinition = "TEXT")
    private String explanation;
}
