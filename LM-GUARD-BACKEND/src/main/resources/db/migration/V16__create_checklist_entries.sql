-- =====================================================================
-- V16: checklist_entries
-- The inspector's own answers to the six-step workflow's compliance
-- checklist. Backend has no "checklist template" table - the fixed item
-- set (code/title/guidance) is owned by the Flutter app, exactly as it was
-- before this table existed; this table stores only the inspector's
-- per-inspection result and note for each item.
-- =====================================================================
CREATE TABLE checklist_entries (
    id           UUID         PRIMARY KEY,
    inspection_id UUID        NOT NULL,
    item_code    VARCHAR(50)  NOT NULL,
    title        VARCHAR(255) NOT NULL,
    guidance     TEXT,
    result       VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    note         TEXT,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_checklist_entries_inspection_item UNIQUE (inspection_id, item_code),
    CONSTRAINT fk_checklist_entries_inspection
        FOREIGN KEY (inspection_id) REFERENCES inspections (id) ON DELETE CASCADE,
    CONSTRAINT ck_checklist_entries_result
        CHECK (result IN ('PENDING', 'COMPLIANT', 'NON_COMPLIANT', 'NOT_APPLICABLE'))
);

CREATE INDEX idx_checklist_entries_inspection ON checklist_entries (inspection_id);

COMMENT ON TABLE checklist_entries IS 'Inspector-recorded checklist answers. Result is never written by AI.';
