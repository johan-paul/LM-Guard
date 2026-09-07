-- =====================================================================
-- V9: online_listings
-- Captured e-commerce declarations for the future physical-vs-digital
-- comparison feature. Populated manually for now; no scraping is built.
-- =====================================================================
CREATE TABLE online_listings (
    id           UUID          PRIMARY KEY,
    product_id   UUID          NOT NULL,
    source       VARCHAR(100)  NOT NULL,
    listing_url  VARCHAR(1000),
    mrp          VARCHAR(100),
    quantity     VARCHAR(100),
    manufacturer VARCHAR(255),
    origin       VARCHAR(150),
    captured_at  TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_online_listings_product
        FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE
);

CREATE INDEX idx_online_listings_product     ON online_listings (product_id);
CREATE INDEX idx_online_listings_captured_at ON online_listings (captured_at DESC);

COMMENT ON TABLE  online_listings        IS 'Declared values from an online marketplace listing, for physical-vs-digital mismatch checks.';
COMMENT ON COLUMN online_listings.source IS 'Marketplace identifier, e.g. AMAZON, FLIPKART, BIGBASKET. Free text for the MVP.';
