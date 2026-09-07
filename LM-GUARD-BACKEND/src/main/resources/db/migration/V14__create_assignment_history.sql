-- =====================================================================
-- V14: assignment_history
-- Audit trail: one row per inspection assignment or reassignment.
-- `created_at` (inherited BaseEntity timestamp) is the "assigned at" time -
-- a row is written once, at the moment of assignment, and never edited.
-- =====================================================================
CREATE TABLE assignment_history (
    id               UUID        PRIMARY KEY,
    inspection_id    UUID        NOT NULL,
    from_inspector_id UUID,
    to_inspector_id  UUID        NOT NULL,
    from_zone_id     UUID,
    to_zone_id       UUID,
    assigned_by_id   UUID        NOT NULL,
    reason           TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_assignment_history_inspection
        FOREIGN KEY (inspection_id) REFERENCES inspections (id) ON DELETE CASCADE,
    CONSTRAINT fk_assignment_history_from_inspector
        FOREIGN KEY (from_inspector_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_assignment_history_to_inspector
        FOREIGN KEY (to_inspector_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_assignment_history_from_zone
        FOREIGN KEY (from_zone_id) REFERENCES zones (id) ON DELETE SET NULL,
    CONSTRAINT fk_assignment_history_to_zone
        FOREIGN KEY (to_zone_id) REFERENCES zones (id) ON DELETE SET NULL,
    CONSTRAINT fk_assignment_history_assigned_by
        FOREIGN KEY (assigned_by_id) REFERENCES users (id) ON DELETE RESTRICT
);

CREATE INDEX idx_assignment_history_inspection ON assignment_history (inspection_id, created_at DESC);

COMMENT ON TABLE assignment_history IS 'Audit trail of inspection assignment/reassignment.';
