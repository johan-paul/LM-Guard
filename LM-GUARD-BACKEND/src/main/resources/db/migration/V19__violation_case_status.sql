-- =====================================================================
-- V19: violation case-management workflow
--
-- `status` (NON_COMPLIANT / INCONCLUSIVE) is the rule engine's verdict on
-- whether the observation was correct - it never changes after analysis.
-- `case_status` is a SEPARATE axis: what an inspector has since done about
-- that finding (still open, escalated, confirmed, dismissed on review).
-- Keeping them apart means every existing query against `status` keeps its
-- current meaning untouched.
-- =====================================================================
ALTER TABLE violations
    ADD COLUMN case_status   VARCHAR(30) NOT NULL DEFAULT 'OPEN',
    ADD COLUMN decided_by    UUID,
    ADD COLUMN decided_at    TIMESTAMPTZ,
    ADD COLUMN decision_note TEXT;

ALTER TABLE violations
    ADD CONSTRAINT ck_violations_case_status
        CHECK (case_status IN ('OPEN', 'UNDER_REVIEW', 'CONFIRMED', 'DISMISSED', 'ESCALATED')),
    ADD CONSTRAINT fk_violations_decided_by
        FOREIGN KEY (decided_by) REFERENCES users (id) ON DELETE SET NULL;

CREATE INDEX idx_violations_case_status ON violations (case_status);

COMMENT ON COLUMN violations.case_status IS 'Inspector case-management state, independent of the rule engine''s status verdict.';
