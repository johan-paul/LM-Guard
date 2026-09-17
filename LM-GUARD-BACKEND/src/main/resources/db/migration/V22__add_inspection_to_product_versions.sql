-- =====================================================================
-- V22: product_versions.inspection_id
-- Which inspection produced this declared-value snapshot, when there is
-- one (an online-listing-sourced version has none). Without this, a
-- declared value that looks wrong on the admin's Product Detail page had
-- no way to be traced back to the specific inspection that wrote it.
-- =====================================================================
ALTER TABLE product_versions
    ADD COLUMN inspection_id UUID,
    ADD CONSTRAINT fk_product_versions_inspection
        FOREIGN KEY (inspection_id) REFERENCES inspections (id) ON DELETE SET NULL;

CREATE INDEX idx_product_versions_inspection ON product_versions (inspection_id);

COMMENT ON COLUMN product_versions.inspection_id IS
    'The inspection that produced this snapshot; null for a version sourced from an online-listing capture. ON DELETE SET NULL rather than CASCADE: deleting an inspection should not erase the product declaration history it once contributed.';
