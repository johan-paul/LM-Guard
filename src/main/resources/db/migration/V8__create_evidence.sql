-- =====================================================================
-- V8: evidence
-- The audit trail. Every violation points at the exact pixels that
-- produced it, so an inspector can see WHY, not just WHAT.
-- =====================================================================
CREATE TABLE evidence (
    id              UUID          PRIMARY KEY,
    violation_id    UUID          NOT NULL,
    inspection_id   UUID          NOT NULL,
    image_url       VARCHAR(1000),
    image_path      VARCHAR(1000),
    x               INTEGER,
    y               INTEGER,
    width           INTEGER,
    height          INTEGER,
    ocr_confidence  NUMERIC(5,4),
    description     TEXT,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_evidence_violation
        FOREIGN KEY (violation_id) REFERENCES violations (id) ON DELETE CASCADE,
    CONSTRAINT fk_evidence_inspection
        FOREIGN KEY (inspection_id) REFERENCES inspections (id) ON DELETE CASCADE,
    CONSTRAINT ck_evidence_confidence
        CHECK (ocr_confidence IS NULL OR (ocr_confidence >= 0 AND ocr_confidence <= 1))
);

CREATE INDEX idx_evidence_violation  ON evidence (violation_id);
CREATE INDEX idx_evidence_inspection ON evidence (inspection_id);

COMMENT ON TABLE  evidence   IS 'Bounding-box backed proof for each violation; the frontend highlights this region on the package image.';
COMMENT ON COLUMN evidence.x IS 'Bounding box origin in pixels on the stored package image. NULL when the finding is an absence with no region to point at.';
