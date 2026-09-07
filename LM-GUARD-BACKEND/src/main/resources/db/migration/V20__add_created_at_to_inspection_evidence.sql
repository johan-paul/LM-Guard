-- =====================================================================
-- V20: inspection_evidence was missing created_at (inherited from
-- BaseEntity, required NOT NULL on every entity). Backfill from the
-- existing captured_at before enforcing NOT NULL, since captured_at
-- already defaults to createdAt in the entity's onCreate() hook.
-- =====================================================================
ALTER TABLE inspection_evidence ADD COLUMN created_at TIMESTAMPTZ;

UPDATE inspection_evidence SET created_at = captured_at WHERE created_at IS NULL;

ALTER TABLE inspection_evidence ALTER COLUMN created_at SET DEFAULT NOW();
ALTER TABLE inspection_evidence ALTER COLUMN created_at SET NOT NULL;
