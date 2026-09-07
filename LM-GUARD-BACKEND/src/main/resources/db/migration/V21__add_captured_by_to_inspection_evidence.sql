-- =====================================================================
-- V21: audit trail for officer-captured evidence.
-- inspection_evidence recorded WHEN a photo was captured but never WHO
-- captured it - the upload endpoint already knows the caller (used for the
-- assigned-inspector-or-admin check) but never persisted it. Nullable
-- because existing rows predate this column and their uploader is not
-- recoverable.
-- =====================================================================
ALTER TABLE inspection_evidence
    ADD COLUMN captured_by UUID REFERENCES users (id) ON DELETE SET NULL;

CREATE INDEX idx_inspection_evidence_captured_by ON inspection_evidence (captured_by);

COMMENT ON COLUMN inspection_evidence.captured_by IS
    'The inspector (or admin) who uploaded this photo - null for rows captured before this column existed.';
