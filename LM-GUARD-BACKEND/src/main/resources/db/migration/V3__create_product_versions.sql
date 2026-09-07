-- =====================================================================
-- V3: product_versions
-- A point-in-time snapshot of a product's declared facts. Every capture
-- (inspection, online listing scrape, manual entry) can add a version,
-- which is what makes "has this label changed?" answerable.
-- =====================================================================
CREATE TABLE product_versions (
    id             UUID         PRIMARY KEY,
    product_id     UUID         NOT NULL,
    version_number INTEGER      NOT NULL,
    mrp            VARCHAR(100),
    net_quantity   VARCHAR(100),
    manufacturer   VARCHAR(255),
    origin         VARCHAR(150),
    consumer_care  VARCHAR(500),
    captured_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    source         VARCHAR(50)  NOT NULL DEFAULT 'INSPECTION',
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_product_versions_product
        FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE,
    CONSTRAINT uk_product_versions_product_number UNIQUE (product_id, version_number),
    CONSTRAINT ck_product_versions_source
        CHECK (source IN ('INSPECTION', 'ONLINE_LISTING', 'MANUAL', 'IMPORT'))
);

CREATE INDEX idx_product_versions_product     ON product_versions (product_id);
CREATE INDEX idx_product_versions_captured_at ON product_versions (captured_at);

COMMENT ON TABLE  product_versions        IS 'Immutable history of declared product facts; drives the product-change risk factor.';
COMMENT ON COLUMN product_versions.source IS 'Where this snapshot came from.';
