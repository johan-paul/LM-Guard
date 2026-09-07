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
 * One fact the AI/OCR layer reports it can see on the package, plus where it saw it.
 *
 * <p>These rows are <em>observations</em>, never verdicts. A null {@code fieldValue} means
 * the AI reports the declaration as absent, and {@code confidence} is then its confidence
 * in that absence.
 */
@Entity
@Table(name = "extracted_fields")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExtractedField extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inspection_id", nullable = false)
    private Inspection inspection;

    @Column(name = "field_name", nullable = false, length = 100)
    private String fieldName;

    @Column(name = "field_value", columnDefinition = "TEXT")
    private String fieldValue;

    @Column(name = "confidence", precision = 5, scale = 4)
    private BigDecimal confidence;

    @Column(name = "bounding_box_x")
    private Integer boundingBoxX;

    @Column(name = "bounding_box_y")
    private Integer boundingBoxY;

    @Column(name = "bounding_box_width")
    private Integer boundingBoxWidth;

    @Column(name = "bounding_box_height")
    private Integer boundingBoxHeight;

    /** The unprocessed OCR text this field was derived from, when the AI supplies it. */
    @Column(name = "raw_text", columnDefinition = "TEXT")
    private String rawText;

    public boolean hasBoundingBox() {
        return boundingBoxX != null && boundingBoxY != null
                && boundingBoxWidth != null && boundingBoxHeight != null;
    }
}
