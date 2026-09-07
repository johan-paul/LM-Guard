-- =====================================================================
-- V6: rules
-- Machine-readable, versioned legal rules. Rows are append-only in
-- spirit: to change a rule, add a new version and deactivate the old one
-- so historical inspections remain explainable.
-- =====================================================================
CREATE TABLE rules (
    id              UUID         PRIMARY KEY,
    rule_code       VARCHAR(50)  NOT NULL,
    rule_name       VARCHAR(255) NOT NULL,
    description     TEXT,
    field_name      VARCHAR(100) NOT NULL,
    rule_type       VARCHAR(50)  NOT NULL,
    rule_definition TEXT         NOT NULL,
    severity        VARCHAR(20)  NOT NULL DEFAULT 'MAJOR',
    version         VARCHAR(50)  NOT NULL,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_rules_code_version UNIQUE (rule_code, version),
    CONSTRAINT ck_rules_type
        CHECK (rule_type IN ('REQUIRED_FIELD', 'PATTERN_MATCH', 'NUMERIC_RANGE', 'MIN_LENGTH')),
    CONSTRAINT ck_rules_severity
        CHECK (severity IN ('MINOR', 'MAJOR', 'CRITICAL'))
);

CREATE INDEX idx_rules_version_active ON rules (version, active);
CREATE INDEX idx_rules_field          ON rules (field_name);

COMMENT ON TABLE  rules                 IS 'Versioned deterministic rules. Demo rules are marked in their description and are NOT official regulations.';
COMMENT ON COLUMN rules.rule_definition IS 'JSON payload of the rule parameters (pattern, minConfidence, min, max, minLength, finding, remediation).';
