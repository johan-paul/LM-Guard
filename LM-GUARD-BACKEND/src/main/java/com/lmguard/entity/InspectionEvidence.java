package com.lmguard.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * An officer-captured evidence photo from the six-step workflow.
 *
 * <p>Distinct from {@link Evidence}, which is an AI-pipeline bounding-box region tied to a
 * {@link Violation}: this is a whole photograph the inspector took, optionally linked to a
 * {@link Finding} afterwards - it can exist before any finding does.
 */
@Entity
@Table(name = "inspection_evidence")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InspectionEvidence extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inspection_id", nullable = false)
    private Inspection inspection;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "finding_id")
    private Finding finding;

    @Column(name = "image_url", nullable = false, length = 1000)
    private String imageUrl;

    @Column(name = "image_path", nullable = false, length = 1000)
    private String imagePath;

    @Column(name = "label", length = 255)
    private String label;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "captured_at", nullable = false)
    private Instant capturedAt;

    @Override
    protected void onCreate() {
        super.onCreate();
        if (capturedAt == null) {
            capturedAt = getCreatedAt();
        }
    }
}
