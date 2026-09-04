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

import java.math.BigDecimal;

/**
 * The audit trail behind a violation: the image and the exact pixel region that produced it.
 *
 * <p>Evidence points back at both the violation and the inspection so an inspector can always
 * answer "why did the system say this?" The frontend draws the bounding box over the package
 * image to highlight the problem area.
 *
 * <p>Coordinates are null when the finding is an absence: there is no region to point at when
 * a declaration simply is not on the package.
 */
@Entity
@Table(name = "evidence")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Evidence extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "violation_id", nullable = false)
    private Violation violation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inspection_id", nullable = false)
    private Inspection inspection;

    @Column(name = "image_url", length = 1000)
    private String imageUrl;

    @Column(name = "image_path", length = 1000)
    private String imagePath;

    @Column(name = "x")
    private Integer x;

    @Column(name = "y")
    private Integer y;

    @Column(name = "width")
    private Integer width;

    @Column(name = "height")
    private Integer height;

    @Column(name = "ocr_confidence", precision = 5, scale = 4)
    private BigDecimal ocrConfidence;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    public boolean hasRegion() {
        return x != null && y != null && width != null && height != null;
    }
}
