-- =====================================================================
-- V10: risk_scores
-- A transparent, fully itemised score. Every component is stored so the
-- number can always be explained to an inspector. No ML, no black box.
-- =====================================================================
CREATE TABLE risk_scores (
    id                  UUID         PRIMARY KEY,
    product_id          UUID         NOT NULL,
    inspection_id       UUID         NOT NULL,
    previous_violations INTEGER      NOT NULL DEFAULT 0,
    product_changes     INTEGER      NOT NULL DEFAULT 0,
    online_mismatch     INTEGER      NOT NULL DEFAULT 0,
    category_risk       INTEGER      NOT NULL DEFAULT 0,
    repeat_issue        INTEGER      NOT NULL DEFAULT 0,
    total_score         INTEGER      NOT NULL DEFAULT 0,
    risk_level          VARCHAR(20)  NOT NULL,
    explanation         TEXT,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_risk_scores_product
        FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE,
    CONSTRAINT fk_risk_scores_inspection
        FOREIGN KEY (inspection_id) REFERENCES inspections (id) ON DELETE CASCADE,
    CONSTRAINT uk_risk_scores_inspection UNIQUE (inspection_id),
    CONSTRAINT ck_risk_scores_level
        CHECK (risk_level IN ('LOW', 'MEDIUM', 'HIGH')),
    CONSTRAINT ck_risk_scores_total
        CHECK (total_score >= 0 AND total_score <= 100)
);

CREATE INDEX idx_risk_scores_product ON risk_scores (product_id);
CREATE INDEX idx_risk_scores_level   ON risk_scores (risk_level);

COMMENT ON TABLE  risk_scores                    IS 'Itemised weighted risk assessment per inspection. Weights are configuration, not code.';
COMMENT ON COLUMN risk_scores.previous_violations IS 'Points contributed by this product''s prior non-compliant inspections (not a raw count).';
COMMENT ON COLUMN risk_scores.explanation         IS 'Human-readable breakdown shown to the inspector.';
