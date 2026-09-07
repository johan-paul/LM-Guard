-- =====================================================================
-- V18: inspection_evidence
-- Officer-captured evidence photos from the six-step workflow, distinct
-- from `evidence` (AI-pipeline bounding-box regions tied to a violation).
-- This table has no violation dependency: an officer can capture evidence
-- before any finding exists, and later link it to one.
-- =====================================================================
CREATE TABLE inspection_evidence (
    id            UUID         PRIMARY KEY,
    inspection_id UUID         NOT NULL,
    finding_id    UUID,
    image_url     VARCHAR(1000) NOT NULL,
    image_path    VARCHAR(1000) NOT NULL,
    label         VARCHAR(255),
    description   TEXT,
    captured_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_inspection_evidence_inspection
        FOREIGN KEY (inspection_id) REFERENCES inspections (id) ON DELETE CASCADE,
    CONSTRAINT fk_inspection_evidence_finding
        FOREIGN KEY (finding_id) REFERENCES findings (id) ON DELETE SET NULL
);

CREATE INDEX idx_inspection_evidence_inspection ON inspection_evidence (inspection_id);
CREATE INDEX idx_inspection_evidence_finding ON inspection_evidence (finding_id);

COMMENT ON TABLE inspection_evidence IS 'Officer-captured evidence photos, distinct from the AI-pipeline evidence table.';
