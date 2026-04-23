-- V5__Add_case_assign_and_dev_users.sql
-- 1. Adds the missing CASE_ASSIGN permission required by case-service's
--    @PreAuthorize("hasAuthority('CASE_ASSIGN')") endpoint.
-- 2. Seeds two well-known dev users so real JWTs include the right permissions
--    without manual DB surgery:
--      admin@lawforyou.dev  / Admin@12345   → ADMIN  role (all permissions)
--      lawyer@lawforyou.dev / Lawyer@12345  → LAWYER role (case + document + user-read)
-- All inserts are idempotent (ON CONFLICT DO NOTHING).

-- ============================================================
-- 1. CASE_ASSIGN permission
-- ============================================================
INSERT INTO permissions (name, display_name, resource, action)
VALUES ('CASE_ASSIGN', 'Assign Cases', 'CASE', 'ASSIGN')
ON CONFLICT (name) DO NOTHING;

-- ============================================================
-- 2. Grant CASE_ASSIGN to ADMIN and LAWYER system roles
-- ============================================================
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM   roles r, permissions p
WHERE  r.name IN ('ADMIN', 'LAWYER') AND r.tenant_id IS NULL
  AND  p.name = 'CASE_ASSIGN'
ON CONFLICT DO NOTHING;

-- ============================================================
-- 3. Dev admin user
--    tenant : 00000000-0000-0000-0000-000000000001  (dev)
--    user id: 00000000-0000-0000-0000-000000000010
--    creds  : admin@lawforyou.dev / Admin@12345   (BCrypt-10)
-- ============================================================
INSERT INTO users (id, tenant_id, username, email, password_hash, first_name, last_name, active)
VALUES (
    '00000000-0000-0000-0000-000000000010',
    '00000000-0000-0000-0000-000000000001',
    'admin',
    'admin@lawforyou.dev',
    '$2b$10$Gts0sLpG5mI.kH.cPtSV8eItEDZGhP/dykdnSSIz5HoqEiafGf/Zm',
    'Platform', 'Admin', TRUE
)
ON CONFLICT DO NOTHING;

-- Assign ADMIN role (looks up by email so it works even if UUID differs)
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM   users u, roles r
WHERE  u.email     = 'admin@lawforyou.dev'
  AND  u.tenant_id = '00000000-0000-0000-0000-000000000001'
  AND  r.name      = 'ADMIN' AND r.tenant_id IS NULL
ON CONFLICT DO NOTHING;

-- ============================================================
-- 4. Dev lawyer user
--    tenant : 00000000-0000-0000-0000-000000000001  (dev)
--    user id: 00000000-0000-0000-0000-000000000011
--    creds  : lawyer@lawforyou.dev / Lawyer@12345  (BCrypt-10)
-- ============================================================
INSERT INTO users (id, tenant_id, username, email, password_hash, first_name, last_name, active)
VALUES (
    '00000000-0000-0000-0000-000000000011',
    '00000000-0000-0000-0000-000000000001',
    'lawyer',
    'lawyer@lawforyou.dev',
    '$2b$10$JsioDh6UTEhRKXZcGlXIUee8jWLTuZqVW6sgWAZBPoT7iB85bXBfu',
    'Dev', 'Lawyer', TRUE
)
ON CONFLICT DO NOTHING;

-- Assign LAWYER role
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM   users u, roles r
WHERE  u.email     = 'lawyer@lawforyou.dev'
  AND  u.tenant_id = '00000000-0000-0000-0000-000000000001'
  AND  r.name      = 'LAWYER' AND r.tenant_id IS NULL
ON CONFLICT DO NOTHING;

