-- =====================================================================
-- V23: allow NUMERIC_BAND in rules.rule_type
-- Rule 7 (minimum numeral height, evaluated against a Table-I band keyed
-- by declared net quantity) needs a rule_type the original V6 check
-- constraint predates. Without this, DemoRuleSeeder can build the row in
-- Java but Postgres itself rejects the INSERT.
-- =====================================================================
ALTER TABLE rules DROP CONSTRAINT ck_rules_type;

ALTER TABLE rules
    ADD CONSTRAINT ck_rules_type
        CHECK (rule_type IN ('REQUIRED_FIELD', 'PATTERN_MATCH', 'NUMERIC_RANGE', 'MIN_LENGTH', 'NUMERIC_BAND'));
