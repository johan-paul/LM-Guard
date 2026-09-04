-- =====================================================================
-- V7: violations
-- A rule that did not pass for an inspection. INCONCLUSIVE is a first
-- class outcome: "we could not read it" is not "it is missing".
-- =====================================================================
CREATE TABLE violations (
    id                  UUID         PRIMARY KEY,
    inspection_id       UUID         NOT NULL,
    rule_id             UUID,
    rule_code           VARCHAR(50)  NOT NULL,
    field_name          VARCHAR(100) NOT NULL,
    finding             TEXT         NOT NULL,
    remediation         TEXT,
    status              VARCHAR(30)  NOT NULL,
    severity            VARCHAR(20)  NOT NULL DEFAULT 'MAJOR',
    decision_confidence NUMERIC(5,4),
    observed_value      TEXT,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_violations_inspection
        FOREIGN KEY (inspection_id) REFERENCES inspections (id) ON DELETE CASCADE,
    CONSTRAINT fk_violations_rule
        FOREIGN KEY (rule_id) REFERENCES rules (id) ON DELETE SET NULL,
    CONSTRAINT ck_violations_status
        CHECK (status IN ('NON_COMPLIANT', 'INCONCLUSIVE')),
    CONSTRAINT ck_violations_severity
        CHECK (severity IN ('MINOR', 'MAJOR', 'CRITICAL')),
    CONSTRAINT ck_violations_confidence
        CHECK (decision_confidence IS NULL OR (decision_confidence >= 0 AND decision_confidence <= 1))
);

CREATE INDEX idx_violations_inspection ON violations (inspection_id);
CREATE INDEX idx_violations_rule       ON violations (rule_id);
CREATE INDEX idx_violations_rule_code  ON violations (rule_code);
CREATE INDEX idx_violations_status     ON violations (status);

COMMENT ON COLUMN violations.rule_code IS 'Denormalised so a finding stays readable even if the rule row is later retired.';
