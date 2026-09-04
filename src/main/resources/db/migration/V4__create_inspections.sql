-- =====================================================================
-- V4: inspections
-- One inspection = one package examined by one inspector at one time.
-- ruleset_version records exactly which rules produced the verdict, so a
-- past decision stays reproducible after the rules change.
-- =====================================================================
CREATE TABLE inspections (
    id                 UUID         PRIMARY KEY,
    product_id         UUID         NOT NULL,
    inspector_id       UUID         NOT NULL,
    status             VARCHAR(30)  NOT NULL DEFAULT 'PENDING',
    overall_confidence NUMERIC(5,4),
    risk_score         INTEGER,
    risk_level         VARCHAR(20),
    ruleset_version    VARCHAR(50),
    image_url          VARCHAR(1000),
    image_path         VARCHAR(1000),
    ai_provider        VARCHAR(50),
    notes              TEXT,
    failure_reason     TEXT,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    completed_at       TIMESTAMPTZ,
    CONSTRAINT fk_inspections_product
        FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE,
    CONSTRAINT fk_inspections_inspector
        FOREIGN KEY (inspector_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT ck_inspections_status
        CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLIANT', 'NON_COMPLIANT', 'INCONCLUSIVE', 'FAILED')),
    CONSTRAINT ck_inspections_risk_level
        CHECK (risk_level IS NULL OR risk_level IN ('LOW', 'MEDIUM', 'HIGH')),
    CONSTRAINT ck_inspections_risk_score
        CHECK (risk_score IS NULL OR (risk_score >= 0 AND risk_score <= 100)),
    CONSTRAINT ck_inspections_confidence
        CHECK (overall_confidence IS NULL OR (overall_confidence >= 0 AND overall_confidence <= 1))
);

CREATE INDEX idx_inspections_product    ON inspections (product_id);
CREATE INDEX idx_inspections_inspector  ON inspections (inspector_id);
CREATE INDEX idx_inspections_status     ON inspections (status);
CREATE INDEX idx_inspections_created_at ON inspections (created_at DESC);

COMMENT ON COLUMN inspections.ruleset_version IS 'The exact ruleset version used for this verdict. Never null once completed.';
COMMENT ON COLUMN inspections.status          IS 'FAILED means the pipeline errored; it is not a compliance verdict.';
