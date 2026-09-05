-- =====================================================================
-- LM-GUARD - Supabase database verification
--
-- Paste into: Supabase Dashboard -> SQL Editor -> New query
--
-- EVERY STATEMENT HERE IS READ-ONLY. Nothing creates, alters, drops or
-- deletes anything. Run it after starting the backend against Supabase
-- for the first time, to confirm Flyway built what it should have.
--
-- Run the sections in order. Section 1 is the one that tells you whether
-- anything worked at all.
-- =====================================================================


-- ---------------------------------------------------------------------
-- 1. Which tables exist?
--
-- Expect 15 rows: 14 LM-GUARD tables plus flyway_schema_history.
-- If you see 0 rows, the backend never connected or Flyway never ran -
-- check the application startup log before reading any further.
-- ---------------------------------------------------------------------
SELECT table_name
FROM information_schema.tables
WHERE table_schema = 'public'
ORDER BY table_name;


-- ---------------------------------------------------------------------
-- 2. Are all the expected LM-GUARD tables present?
--
-- Returns one row per expected table with a PRESENT / *** MISSING ***
-- verdict, so you do not have to eyeball the list above.
-- ---------------------------------------------------------------------
WITH expected(table_name) AS (
    VALUES ('users'), ('products'), ('product_versions'), ('inspections'),
           ('extracted_fields'), ('rules'), ('violations'), ('evidence'),
           ('online_listings'), ('risk_scores'),
           ('zones'), ('refresh_tokens'), ('admin_settings'), ('inspector_activities')
)
SELECT e.table_name,
       CASE WHEN t.table_name IS NULL THEN '*** MISSING ***' ELSE 'present' END AS status
FROM expected e
LEFT JOIN information_schema.tables t
       ON t.table_schema = 'public' AND t.table_name = e.table_name
ORDER BY status DESC, e.table_name;


-- ---------------------------------------------------------------------
-- 3. Flyway migration history
--
-- Expect 16 rows, V1 through V16, every one with success = true.
-- A row with success = false means that migration failed part-way. Fix
-- the cause, then delete that single failed row before restarting -
-- Flyway will not retry a migration it has recorded as failed.
-- ---------------------------------------------------------------------
SELECT installed_rank,
       version,
       description,
       type,
       success,
       execution_time AS ms,
       installed_on
FROM flyway_schema_history
ORDER BY installed_rank;

-- Quick verdict on the same thing.
--
-- version is a TEXT column, so max(version) would sort lexically and report
-- '9' as the newest of sixteen migrations. Cast to int for the ordering.
SELECT count(*) FILTER (WHERE success)     AS succeeded,
       count(*) FILTER (WHERE NOT success) AS failed,
       max(NULLIF(regexp_replace(version, '\\D', '', 'g'), '')::int) AS current_version
FROM flyway_schema_history
WHERE version IS NOT NULL;


-- ---------------------------------------------------------------------
-- 4. Foreign keys
--
-- The referential spine of the system. Expect inspections -> products and
-- users; violations -> inspections and rules; evidence -> violations and
-- inspections; risk_scores -> products and inspections; users -> zones.
-- ---------------------------------------------------------------------
SELECT tc.table_name       AS from_table,
       kcu.column_name     AS from_column,
       ccu.table_name      AS to_table,
       ccu.column_name     AS to_column,
       rc.delete_rule
FROM information_schema.table_constraints tc
JOIN information_schema.key_column_usage kcu
     ON kcu.constraint_name = tc.constraint_name AND kcu.table_schema = tc.table_schema
JOIN information_schema.constraint_column_usage ccu
     ON ccu.constraint_name = tc.constraint_name AND ccu.table_schema = tc.table_schema
JOIN information_schema.referential_constraints rc
     ON rc.constraint_name = tc.constraint_name AND rc.constraint_schema = tc.table_schema
WHERE tc.constraint_type = 'FOREIGN KEY'
  AND tc.table_schema = 'public'
ORDER BY tc.table_name, kcu.column_name;


-- ---------------------------------------------------------------------
-- 5. Columns that carry the audit trail
--
-- These are the ones that make a past verdict reproducible. If
-- inspections.ruleset_version is missing, an inspection can no longer say
-- which rules judged it, and the audit story is gone.
-- ---------------------------------------------------------------------
SELECT table_name, column_name, data_type, is_nullable, column_default
FROM information_schema.columns
WHERE table_schema = 'public'
  AND (
        (table_name = 'inspections' AND column_name IN
            ('status', 'review_status', 'ruleset_version', 'overall_confidence',
             'risk_score', 'risk_level', 'ai_provider', 'failure_reason',
             'image_url', 'image_path', 'completed_at'))
     OR (table_name = 'violations' AND column_name IN
            ('status', 'review_status', 'rule_code', 'decision_confidence', 'severity'))
     OR (table_name = 'evidence' AND column_name IN
            ('x', 'y', 'width', 'height', 'ocr_confidence', 'image_url', 'image_path'))
     OR (table_name = 'users' AND column_name IN
            ('role', 'enabled', 'inspector_code', 'zone_id', 'last_login_at', 'last_active_at'))
     OR (table_name = 'risk_scores' AND column_name IN
            ('total_score', 'risk_level', 'explanation'))
  )
ORDER BY table_name, column_name;


-- ---------------------------------------------------------------------
-- 6. Check constraints on the status columns
--
-- These are what stop an invalid verdict reaching the database. In
-- particular inspections.status must allow INCONCLUSIVE - if it does not,
-- an unreadable label has nowhere to go but NON_COMPLIANT, which is the
-- one outcome this system is built to prevent.
-- ---------------------------------------------------------------------
SELECT conrelid::regclass AS table_name,
       conname            AS constraint_name,
       pg_get_constraintdef(oid) AS definition
FROM pg_constraint
WHERE contype = 'c'
  AND connamespace = 'public'::regnamespace
  AND conrelid::regclass::text IN ('inspections', 'violations', 'users', 'zones', 'rules')
ORDER BY table_name, constraint_name;


-- ---------------------------------------------------------------------
-- 7. Row counts
--
-- On a database the backend has only just migrated, expect zones = 3
-- (seeded by V11) and everything else 0. After running an inspection you
-- should see inspections, extracted_fields, risk_scores and usually
-- violations and evidence all non-zero.
-- ---------------------------------------------------------------------
SELECT 'zones'                AS table_name, count(*) FROM zones
UNION ALL SELECT 'users',                count(*) FROM users
UNION ALL SELECT 'rules',                count(*) FROM rules
UNION ALL SELECT 'products',             count(*) FROM products
UNION ALL SELECT 'product_versions',     count(*) FROM product_versions
UNION ALL SELECT 'inspections',          count(*) FROM inspections
UNION ALL SELECT 'extracted_fields',     count(*) FROM extracted_fields
UNION ALL SELECT 'violations',           count(*) FROM violations
UNION ALL SELECT 'evidence',             count(*) FROM evidence
UNION ALL SELECT 'risk_scores',          count(*) FROM risk_scores
UNION ALL SELECT 'online_listings',      count(*) FROM online_listings
UNION ALL SELECT 'refresh_tokens',       count(*) FROM refresh_tokens
UNION ALL SELECT 'admin_settings',       count(*) FROM admin_settings
UNION ALL SELECT 'inspector_activities', count(*) FROM inspector_activities
ORDER BY table_name;


-- ---------------------------------------------------------------------
-- 8. Did the pipeline actually persist a complete inspection?
--
-- The end-to-end check. One row per inspection with the counts of what
-- was written alongside it. A completed inspection should have extracted
-- fields and a risk score; a NON_COMPLIANT one should also have
-- violations, and each violation should have evidence.
--
-- Note INCONCLUSIVE is its own status. It means the declaration could not
-- be read confidently. It is NOT a violation, and it is correct for an
-- INCONCLUSIVE inspection to carry violations rows whose own status is
-- INCONCLUSIVE - that is the system recording "we looked and could not
-- tell", which is the distinction the whole design protects.
-- ---------------------------------------------------------------------
SELECT i.id,
       p.product_name,
       i.status,
       i.review_status,
       i.risk_score,
       i.risk_level,
       i.ruleset_version,
       i.ai_provider,
       (SELECT count(*) FROM extracted_fields ef WHERE ef.inspection_id = i.id) AS fields,
       (SELECT count(*) FROM violations v       WHERE v.inspection_id  = i.id) AS violations,
       (SELECT count(*) FROM evidence e         WHERE e.inspection_id  = i.id) AS evidence,
       i.created_at,
       i.completed_at
FROM inspections i
JOIN products p ON p.id = i.product_id
ORDER BY i.created_at DESC
LIMIT 25;


-- ---------------------------------------------------------------------
-- 9. Verdict distribution
--
-- Sanity check on the rule engine. With the mock analyser you should see
-- all three verdicts appear across a handful of inspections. If every row
-- is NON_COMPLIANT, something is wrong with the confidence handling.
-- ---------------------------------------------------------------------
SELECT status, count(*)
FROM inspections
GROUP BY status
ORDER BY count(*) DESC;


-- ---------------------------------------------------------------------
-- 10. Storage URLs
--
-- Confirms images went to Supabase Storage rather than local disk. With
-- STORAGE_PROVIDER=supabase these should start with your Supabase project
-- URL; with STORAGE_PROVIDER=local they will point at localhost:8080.
-- ---------------------------------------------------------------------
SELECT DISTINCT
       CASE
         WHEN image_url LIKE 'http://localhost%'      THEN 'local disk'
         WHEN image_url LIKE '%supabase.co%'          THEN 'supabase storage'
         WHEN image_url IS NULL                       THEN 'no image uploaded'
         ELSE 'other: ' || left(image_url, 40)
       END AS storage_location,
       count(*) OVER (PARTITION BY
         CASE
           WHEN image_url LIKE 'http://localhost%' THEN 'local disk'
           WHEN image_url LIKE '%supabase.co%'     THEN 'supabase storage'
           WHEN image_url IS NULL                  THEN 'no image uploaded'
           ELSE 'other'
         END) AS inspections
FROM inspections;


-- ---------------------------------------------------------------------
-- 11. Nothing sensitive is sitting in the database in the clear
--
-- password_hash must look like a BCrypt hash ($2a$ / $2b$ / $2y$ ...), and
-- refresh tokens must be stored as 64-character SHA-256 hex, never as the
-- token itself. Both queries should return zero rows.
-- ---------------------------------------------------------------------
SELECT id, email, 'password is NOT bcrypt-hashed' AS problem
FROM users
WHERE password_hash NOT LIKE '$2%';

SELECT id, 'refresh token is not a sha-256 hash' AS problem
FROM refresh_tokens
WHERE length(token_hash) <> 64
   OR token_hash !~ '^[0-9a-f]+$';
