package com.lmguard.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * A packaged commodity.
 *
 * <p>Declared values (MRP, net quantity, ...) deliberately do <em>not</em> live here.
 * They are captured as {@link ProductVersion} snapshots so label changes over time
 * remain visible and auditable.
 */
@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product extends BaseEntity {

    @Column(name = "product_name", nullable = false, length = 255)
    private String productName;

    @Column(name = "brand", length = 255)
    private String brand;

    @Column(name = "category", length = 100)
    private String category;

    @Column(name = "barcode", length = 100)
    private String barcode;

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
