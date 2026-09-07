-- =====================================================================
-- V2: products
-- The identity of a packaged commodity. Mutable declaration values live
-- in product_versions, not here, so history is preserved.
-- =====================================================================
CREATE TABLE products (
    id           UUID         PRIMARY KEY,
    product_name VARCHAR(255) NOT NULL,
    brand        VARCHAR(255),
    category     VARCHAR(100),
    barcode      VARCHAR(100),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_products_barcode  ON products (barcode);
CREATE INDEX idx_products_category ON products (category);
CREATE INDEX idx_products_brand    ON products (brand);

COMMENT ON TABLE products IS 'Packaged commodities under inspection. Declaration values are versioned separately.';
