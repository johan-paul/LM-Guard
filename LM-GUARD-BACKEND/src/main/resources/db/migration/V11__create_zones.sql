-- =====================================================================
-- V11: zones
-- Legal Metrology jurisdictions. Inspectors are posted to one; inspections
-- belong to one.
-- =====================================================================
CREATE TABLE zones (
    id          UUID         PRIMARY KEY,
    name        VARCHAR(150) NOT NULL,
    code        VARCHAR(20),
    office      VARCHAR(255),
    district    VARCHAR(100),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_zones_name UNIQUE (name),
    CONSTRAINT uk_zones_code UNIQUE (code)
);

COMMENT ON TABLE zones IS 'Legal Metrology zones/jurisdictions.';
