-- =====================================================================
-- V15: inspections - case details + officer-driven lifecycle
--
-- Admins open a case (establishment/address/type/priority/due date) before
-- a product is known - the inspector identifies/attaches one during their
-- own product-identification step. So product_id becomes nullable.
--
-- The rule engine's verdict from /analyze is recorded as ai_suggested_status
-- (advisory only) instead of overwriting the authoritative status column;
-- only the inspector's own /submit call sets the final status. IN_PROGRESS
-- is added to represent "assigned/opened, not yet submitted".
-- =====================================================================
ALTER TABLE inspections
    ALTER COLUMN product_id DROP NOT NULL;

ALTER TABLE inspections
    ADD COLUMN establishment      VARCHAR(255),
    ADD COLUMN address            TEXT,
    ADD COLUMN inspection_type    VARCHAR(30),
    ADD COLUMN priority           VARCHAR(20),
    ADD COLUMN due_date           TIMESTAMPTZ,
    ADD COLUMN ai_suggested_status VARCHAR(30),
    ADD COLUMN analyzed_at        TIMESTAMPTZ;

ALTER TABLE inspections
    DROP CONSTRAINT ck_inspections_status,
    ADD CONSTRAINT ck_inspections_status
        CHECK (status IN ('PENDING', 'IN_PROGRESS', 'PROCESSING', 'COMPLIANT', 'NON_COMPLIANT',
                           'INCONCLUSIVE', 'FAILED')),
    ADD CONSTRAINT ck_inspections_ai_suggested_status
        CHECK (ai_suggested_status IS NULL OR ai_suggested_status IN
               ('COMPLIANT', 'NON_COMPLIANT', 'INCONCLUSIVE')),
    ADD CONSTRAINT ck_inspections_inspection_type
        CHECK (inspection_type IS NULL OR inspection_type IN
               ('ROUTINE', 'COMPLAINT', 'FOLLOW_UP', 'DRIVE')),
    ADD CONSTRAINT ck_inspections_priority
        CHECK (priority IS NULL OR priority IN ('LOW', 'MEDIUM', 'HIGH'));

COMMENT ON COLUMN inspections.product_id IS 'Nullable: an admin-opened case has no product until the inspector identifies one.';
COMMENT ON COLUMN inspections.ai_suggested_status IS 'Advisory verdict from the rule engine (/analyze). Never the authoritative outcome - see status.';
COMMENT ON COLUMN inspections.status IS 'IN_PROGRESS covers assigned-but-not-submitted. FAILED means the pipeline errored; it is not a compliance verdict. Only /submit sets a final verdict (COMPLIANT/NON_COMPLIANT/INCONCLUSIVE).';
