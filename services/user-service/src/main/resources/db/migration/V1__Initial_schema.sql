-- V1__Initial_schema.sql
-- User Service — full RBAC schema
-- Flyway migration: runs once on first startup

-- ============================================================
-- TENANTS
-- ============================================================
CREATE TABLE tenants (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    slug        VARCHAR(63)  NOT NULL UNIQUE,
    name        VARCHAR(255) NOT NULL,
    domain      VARCHAR(255),
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by  VARCHAR(100),
    updated_by  VARCHAR(100)
);

-- ============================================================
-- PERMISSIONS
-- resource = domain object (USER, CASE, DOCUMENT, ROLE, TENANT)
-- action   = operation     (READ, CREATE, UPDATE, DELETE, MANAGE)
-- ============================================================
CREATE TABLE permissions (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name         VARCHAR(100) NOT NULL UNIQUE,
    display_name VARCHAR(255),
    description  TEXT,
    resource     VARCHAR(50)  NOT NULL,
    action       VARCHAR(50)  NOT NULL,
    active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by   VARCHAR(100),
    updated_by   VARCHAR(100)
);

-- ============================================================
-- ROLES
-- tenant_id = NULL  → system/global role (system_role = TRUE)
-- tenant_id = <id>  → tenant-defined custom role
-- system_role = TRUE → cannot be deleted by tenant admins
-- ============================================================
CREATE TABLE roles (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    UUID         REFERENCES tenants(id) ON DELETE CASCADE,
    name         VARCHAR(100) NOT NULL,
    display_name VARCHAR(255),
    description  TEXT,
    active       BOOLEAN      NOT NULL DEFAULT TRUE,
    system_role  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by   VARCHAR(100),
    updated_by   VARCHAR(100)
);

-- Partial unique indexes handle NULL tenant_id correctly in PostgreSQL
CREATE UNIQUE INDEX uq_system_role_name ON roles (name)            WHERE tenant_id IS NULL;
CREATE UNIQUE INDEX uq_tenant_role_name ON roles (tenant_id, name) WHERE tenant_id IS NOT NULL;

-- ============================================================
-- ROLE_PERMISSIONS  (many-to-many)
-- ============================================================
CREATE TABLE role_permissions (
    role_id       UUID NOT NULL REFERENCES roles(id)       ON DELETE CASCADE,
    permission_id UUID NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

-- ============================================================
-- USERS
-- tenant_id propagated via Hibernate @TenantId
-- ============================================================
CREATE TABLE users (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     UUID         NOT NULL REFERENCES tenants(id),
    username      VARCHAR(100) NOT NULL,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    first_name    VARCHAR(100),
    last_name     VARCHAR(100),
    phone         VARCHAR(30),
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by    VARCHAR(100),
    updated_by    VARCHAR(100),
    CONSTRAINT uq_users_email_per_tenant    UNIQUE (tenant_id, email),
    CONSTRAINT uq_users_username_per_tenant UNIQUE (tenant_id, username)
);

-- ============================================================
-- USER_ROLES  (many-to-many)
-- role_id is UUID — can be system OR tenant-specific role
-- ============================================================
CREATE TABLE user_roles (
    user_id UUID NOT NULL REFERENCES users(id)  ON DELETE CASCADE,
    role_id UUID NOT NULL REFERENCES roles(id)  ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

-- ============================================================
-- INDEXES
-- ============================================================
CREATE INDEX idx_users_tenant_id      ON users(tenant_id);
CREATE INDEX idx_users_email          ON users(email);
CREATE INDEX idx_users_username       ON users(username);
CREATE INDEX idx_tenants_slug         ON tenants(slug);
CREATE INDEX idx_tenants_domain       ON tenants(domain);
CREATE INDEX idx_roles_tenant_id      ON roles(tenant_id);
CREATE INDEX idx_permissions_resource ON permissions(resource);

-- ============================================================
-- SEED: PERMISSIONS
-- ============================================================
INSERT INTO permissions (name, display_name, resource, action) VALUES
    ('USER_READ',       'View Users',       'USER',     'READ'),
    ('USER_CREATE',     'Create Users',     'USER',     'CREATE'),
    ('USER_UPDATE',     'Update Users',     'USER',     'UPDATE'),
    ('USER_DELETE',     'Delete Users',     'USER',     'DELETE'),
    ('CASE_READ',       'View Cases',       'CASE',     'READ'),
    ('CASE_CREATE',     'Create Cases',     'CASE',     'CREATE'),
    ('CASE_UPDATE',     'Update Cases',     'CASE',     'UPDATE'),
    ('CASE_DELETE',     'Delete Cases',     'CASE',     'DELETE'),
    ('DOCUMENT_READ',   'View Documents',   'DOCUMENT', 'READ'),
    ('DOCUMENT_UPLOAD', 'Upload Documents', 'DOCUMENT', 'CREATE'),
    ('DOCUMENT_DELETE', 'Delete Documents', 'DOCUMENT', 'DELETE'),
    ('ROLE_MANAGE',     'Manage Roles',     'ROLE',     'MANAGE'),
    ('TENANT_MANAGE',   'Manage Tenant',    'TENANT',   'MANAGE');

-- ============================================================
-- SEED: SYSTEM ROLES  (tenant_id = NULL, system_role = TRUE)
-- ============================================================
INSERT INTO roles (name, display_name, description, system_role) VALUES
    ('ADMIN',  'Administrator', 'Full platform access',                TRUE),
    ('LAWYER', 'Lawyer',        'Legal professional with case access', TRUE),
    ('CLIENT', 'Client',        'Client with read-only access',        TRUE),
    ('STAFF',  'Support Staff', 'Administrative support staff',        TRUE);

-- ============================================================
-- SEED: ROLE → PERMISSION ASSIGNMENTS
-- ============================================================

-- ADMIN gets all permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'ADMIN' AND r.tenant_id IS NULL;

-- LAWYER: cases (full), documents (full), users (read)
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'LAWYER' AND r.tenant_id IS NULL
  AND p.name IN ('CASE_READ','CASE_CREATE','CASE_UPDATE','CASE_DELETE',
                 'DOCUMENT_READ','DOCUMENT_UPLOAD','DOCUMENT_DELETE',
                 'USER_READ');

-- CLIENT: read own cases and documents only
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'CLIENT' AND r.tenant_id IS NULL
  AND p.name IN ('CASE_READ','DOCUMENT_READ');

-- STAFF: read everything, update cases, upload documents
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'STAFF' AND r.tenant_id IS NULL
  AND p.name IN ('USER_READ','CASE_READ','CASE_UPDATE',
                 'DOCUMENT_READ','DOCUMENT_UPLOAD');

