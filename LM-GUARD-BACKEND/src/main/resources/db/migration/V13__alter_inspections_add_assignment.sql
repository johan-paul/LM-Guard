-- =====================================================================
-- V13: inspections - zone + assignment tracking
-- Lets an admin assign an inspection to an inspector within a zone, and
-- records who made that assignment and when.
-- =====================================================================
ALTER TABLE inspections
    ADD COLUMN zone_id        UUID,
    ADD COLUMN assigned_by_id UUID,
    ADD COLUMN assigned_at    TIMESTAMPTZ;

ALTER TABLE inspections
    ADD CONSTRAINT fk_inspections_zone
        FOREIGN KEY (zone_id) REFERENCES zones (id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_inspections_assigned_by
        FOREIGN KEY (assigned_by_id) REFERENCES users (id) ON DELETE SET NULL;

CREATE INDEX idx_inspections_zone ON inspections (zone_id);

COMMENT ON COLUMN inspections.assigned_by_id IS 'Admin who made the current assignment; null when the inspector opened it themselves.';
