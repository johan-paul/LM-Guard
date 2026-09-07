package com.lmguard.entity;

import com.lmguard.entity.enums.VersionSource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

/** An immutable point-in-time snapshot of a product's declared facts. */
@Entity
@Table(name = "product_versions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductVersion extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    @Column(name = "mrp", length = 100)
    private String mrp;

    @Column(name = "net_quantity", length = 100)
    private String netQuantity;

    @Column(name = "manufacturer", length = 255)
    private String manufacturer;

    @Column(name = "origin", length = 150)
    private String origin;

    @Column(name = "consumer_care", length = 500)
    private String consumerCare;

    @Column(name = "captured_at", nullable = false)
    private Instant capturedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 50)
    @Builder.Default
    private VersionSource source = VersionSource.INSPECTION;

    @Override
    protected void onCreate() {
        super.onCreate();
        if (capturedAt == null) {
            capturedAt = getCreatedAt();
        }
    }
}
