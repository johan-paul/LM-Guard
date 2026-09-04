-- =====================================================================
-- V5: extracted_fields
-- Facts the AI/OCR layer says it can SEE on the package, with the pixel
-- region it saw them in. These are observations, never verdicts.
-- =====================================================================
CREATE TABLE extracted_fields (
    id                  UUID         PRIMARY KEY,
    inspection_id       UUID         NOT NULL,
    field_name          VARCHAR(100) NOT NULL,
    field_value         TEXT,
    confidence          NUMERIC(5,4),
    bounding_box_x      INTEGER,
    bounding_box_y      INTEGER,
    bounding_box_width  INTEGER,
    bounding_box_height INTEGER,
    raw_text            TEXT,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_extracted_fields_inspection
        FOREIGN KEY (inspection_id) REFERENCES inspections (id) ON DELETE CASCADE,
    CONSTRAINT uk_extracted_fields_inspection_field UNIQUE (inspection_id, field_name),
    CONSTRAINT ck_extracted_fields_confidence
        CHECK (confidence IS NULL OR (confidence >= 0 AND confidence <= 1))
);

CREATE INDEX idx_extracted_fields_inspection ON extracted_fields (inspection_id);
CREATE INDEX idx_extracted_fields_name       ON extracted_fields (field_name);

COMMENT ON TABLE  extracted_fields            IS 'AI/OCR observations. The rule engine, not the AI, decides compliance.';
COMMENT ON COLUMN extracted_fields.field_value IS 'NULL means the AI reports the declaration as absent; `confidence` is then confidence in the absence.';
