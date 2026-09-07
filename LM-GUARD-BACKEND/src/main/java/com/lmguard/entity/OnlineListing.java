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
 * Declared values taken from an online marketplace listing.
 *
 * <p>Groundwork for the physical-vs-digital comparison feature. Listings are entered
 * manually or imported for the MVP; no scraping is implemented and none is planned here.
 */
@Entity
@Table(name = "online_listings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OnlineListing extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /** Marketplace identifier, e.g. AMAZON, FLIPKART. Free text for the MVP. */
    @Column(name = "source", nullable = false, length = 100)
    private String source;

    @Column(name = "listing_url", length = 1000)
    private String listingUrl;

    @Column(name = "mrp", length = 100)
    private String mrp;

    @Column(name = "quantity", length = 100)
    private String quantity;

    @Column(name = "manufacturer", length = 255)
    private String manufacturer;

    @Column(name = "origin", length = 150)
    private String origin;

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
