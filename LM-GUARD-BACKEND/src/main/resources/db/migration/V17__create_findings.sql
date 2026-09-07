-- =====================================================================
-- V17: findings
-- Inspector-authored findings from the six-step workflow (distinct from
-- `violations`, which are AI/rule-engine-derived). Reuses the existing
-- `severity` vocabulary (MINOR/MAJOR/CRITICAL) rather than introducing a
-- second severity scale.
-- =====================================================================
CREATE TABLE findings (
    id            UUID         PRIMARY KEY,
    inspection_id UUID         NOT NULL,
    sequence      INTEGER      NOT NULL,
    rule_ref      VARCHAR(100),
    rule_name     VARCHAR(255),
    severity      VARCHAR(20)  NOT NULL DEFAULT 'MAJOR',
    description   TEXT         NOT NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'OPEN',
    note          TEXT,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_findings_inspection
        FOREIGN KEY (inspection_id) REFERENCES inspections (id) ON DELETE CASCADE,
    CONSTRAINT ck_findings_severity CHECK (severity IN ('MINOR', 'MAJOR', 'CRITICAL')),
    CONSTRAINT ck_findings_status CHECK (status IN ('OPEN', 'RESOLVED'))
);

CREATE INDEX idx_findings_inspection ON findings (inspection_id, sequence);

COMMENT ON TABLE findings IS 'Inspector-authored findings, distinct from AI/rule-engine-derived violations.';
