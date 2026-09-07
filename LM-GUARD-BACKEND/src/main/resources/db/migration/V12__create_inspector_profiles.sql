-- =====================================================================
-- V12: inspector_profiles
-- Directory fields for an INSPECTOR user that do not belong on `users`
-- (which stays auth-only). One row per inspecting officer.
-- =====================================================================
CREATE TABLE inspector_profiles (
    id             UUID         PRIMARY KEY,
    user_id        UUID         NOT NULL,
    officer_code   VARCHAR(20)  NOT NULL,
    zone_id        UUID,
    full_name      VARCHAR(255),
    rank           VARCHAR(50)  NOT NULL DEFAULT 'Inspecting Officer',
    phone          VARCHAR(30),
    joined_on      DATE         NOT NULL,
    last_active_at TIMESTAMPTZ,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_inspector_profiles_user UNIQUE (user_id),
    CONSTRAINT uk_inspector_profiles_officer_code UNIQUE (officer_code),
    CONSTRAINT fk_inspector_profiles_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_inspector_profiles_zone
        FOREIGN KEY (zone_id) REFERENCES zones (id) ON DELETE SET NULL
);

CREATE INDEX idx_inspector_profiles_zone ON inspector_profiles (zone_id);

COMMENT ON TABLE inspector_profiles IS 'Admin-console directory profile for an inspecting officer.';
