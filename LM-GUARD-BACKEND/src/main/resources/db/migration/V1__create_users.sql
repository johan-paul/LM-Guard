-- =====================================================================
-- V1: users
-- Inspectors and administrators of the LM-GUARD platform.
-- =====================================================================
CREATE TABLE users (
    id            UUID         PRIMARY KEY,
    name          VARCHAR(150) NOT NULL,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(30)  NOT NULL,
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT ck_users_role  CHECK (role IN ('INSPECTOR', 'ADMIN'))
);

CREATE INDEX idx_users_role ON users (role);

COMMENT ON TABLE  users      IS 'Legal Metrology inspectors and platform administrators.';
COMMENT ON COLUMN users.role IS 'INSPECTOR = field inspection; ADMIN = INSPECTOR plus rule management and global dashboard.';
