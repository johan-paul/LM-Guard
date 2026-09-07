package com.lmguard.entity;

import com.lmguard.entity.enums.FindingStatus;
import com.lmguard.entity.enums.Severity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * An inspector-authored finding from the six-step workflow.
 *
 * <p>Distinct from {@link Violation}, which is AI/rule-engine-derived: a finding is something
 * the inspector themselves observed and recorded, whether or not the AI/rule engine flagged it.
 * Reuses the existing {@link Severity} vocabulary rather than a second severity scale.
 */
@Entity
@Table(name = "findings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Finding extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inspection_id", nullable = false)
    private Inspection inspection;

    @Column(name = "sequence", nullable = false)
    private int sequence;

    @Column(name = "rule_ref", length = 100)
    private String ruleRef;

    @Column(name = "rule_name", length = 255)
    private String ruleName;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 20)
    @Builder.Default
    private Severity severity = Severity.MAJOR;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private FindingStatus status = FindingStatus.OPEN;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

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
