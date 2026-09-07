package com.lmguard.entity;

import com.lmguard.entity.enums.InspectionStatus;
import com.lmguard.entity.enums.InspectionType;
import com.lmguard.entity.enums.Priority;
import com.lmguard.entity.enums.RiskLevel;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * One package examined by one inspector at one point in time.
 *
 * <p>{@code rulesetVersion} is recorded on completion so a past verdict stays reproducible
 * and explainable after the rules are amended.
 */
@Entity
@Table(name = "inspections")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Inspection extends BaseEntity {

    /**
     * The product under inspection. Nullable: an admin opens a case (establishment, zone,
     * inspector) before a product has been identified - the inspector attaches or registers
     * one during their own product-identification step, via {@code PATCH .../product}.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inspector_id", nullable = false)
    private User inspector;

    /** Zone this inspection belongs to. Null for inspections opened before zones existed. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zone_id")
    private Zone zone;

    /** Admin who made the current assignment. Null when the inspector opened it themselves. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_by_id")
    private User assignedBy;

    /** When the current {@link #inspector} was assigned - set on admin assignment/reassignment only. */
    @Column(name = "assigned_at")
    private java.time.Instant assignedAt;

    // ------------------------------------------------------------------
    // Case details (set by the admin when opening the inspection)
    // ------------------------------------------------------------------

    @Column(name = "establishment", length = 255)
    private String establishment;

    @Column(name = "address", columnDefinition = "TEXT")
    private String address;

    @Enumerated(EnumType.STRING)
    @Column(name = "inspection_type", length = 30)
    private InspectionType inspectionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", length = 20)
    private Priority priority;

    @Column(name = "due_date")
    private Instant dueDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private InspectionStatus status = InspectionStatus.PENDING;

    /**
     * The rule engine's most recent suggested verdict - advisory only, never the inspection's
     * authoritative outcome. Written by {@code /analyze}; only the inspector's own
     * {@code /submit} call decides {@link #status}.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "ai_suggested_status", length = 30)
    private InspectionStatus aiSuggestedStatus;

    /** When {@code /analyze} last completed. Distinct from {@link #completedAt}, which marks
     * the inspector's final submission. */
    @Column(name = "analyzed_at")
    private Instant analyzedAt;

    /** Mean confidence of the AI observations this verdict rests on, 0..1. */
    @Column(name = "overall_confidence", precision = 5, scale = 4)
    private BigDecimal overallConfidence;

    @Column(name = "risk_score")
    private Integer riskScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", length = 20)
    private RiskLevel riskLevel;

    @Column(name = "ruleset_version", length = 50)
    private String rulesetVersion;

    /** Publicly resolvable URL of the stored package image. */
    @Column(name = "image_url", length = 1000)
    private String imageUrl;

    /** Storage-internal path (bucket-relative), used for deletion and re-signing. */
    @Column(name = "image_path", length = 1000)
    private String imagePath;

    /** Which AI implementation produced the facts: MOCK or EXTERNAL. Kept for audit. */
    @Column(name = "ai_provider", length = 50)
    private String aiProvider;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    /** Populated only when {@code status == FAILED}. */
    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @OneToMany(mappedBy = "inspection", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("fieldName ASC")
    @Builder.Default
    private List<ExtractedField> extractedFields = new ArrayList<>();

    @OneToMany(mappedBy = "inspection", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("createdAt ASC")
    @Builder.Default
    private List<Violation> violations = new ArrayList<>();

    public void addExtractedField(ExtractedField field) {
        field.setInspection(this);
        this.extractedFields.add(field);
    }

    public void addViolation(Violation violation) {
        violation.setInspection(this);
        this.violations.add(violation);
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    @Override
    protected void onCreate() {
        super.onCreate();
        if (updatedAt == null) {
            updatedAt = getCreatedAt();
        }
    }
}
