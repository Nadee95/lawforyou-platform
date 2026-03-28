-- V1__Initial_schema.sql
-- User Service — initial database schema
-- Flyway migration: runs once on first startup

-- ============================================================
-- TENANTS
-- Every row in every table belongs to a tenant.
-- ============================================================
CREATE TABLE tenants (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    slug        VARCHAR(63) NOT NULL UNIQUE,   -- e.g. "acme-law", used in custom domains
    name        VARCHAR(255) NOT NULL,
    domain      VARCHAR(255),                  -- optional custom domain
    active      BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ============================================================
-- ROLES
-- ============================================================
CREATE TABLE roles (
    id   SMALLSERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE   -- ADMIN, LAWYER, CLIENT, STAFF
);

INSERT INTO roles (name) VALUES ('ADMIN'), ('LAWYER'), ('CLIENT'), ('STAFF');

-- ============================================================
-- USERS
-- tenant_id implements row-level multi-tenancy (Hibernate @TenantId)
-- ============================================================
CREATE TABLE users (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     UUID        NOT NULL REFERENCES tenants(id),
    username      VARCHAR(100) NOT NULL,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    first_name    VARCHAR(100),
    last_name     VARCHAR(100),
    phone         VARCHAR(30),
    active        BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by    VARCHAR(100),
    updated_by    VARCHAR(100),
    CONSTRAINT uq_users_email_per_tenant    UNIQUE (tenant_id, email),
    CONSTRAINT uq_users_username_per_tenant UNIQUE (tenant_id, username)
);

-- ============================================================
-- USER_ROLES  (many-to-many)
-- ============================================================
CREATE TABLE user_roles (
    user_id UUID     NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id SMALLINT NOT NULL REFERENCES roles(id),
    PRIMARY KEY (user_id, role_id)
);

-- ============================================================
-- INDEXES
-- ============================================================
CREATE INDEX idx_users_tenant_id    ON users(tenant_id);
CREATE INDEX idx_users_email        ON users(email);
CREATE INDEX idx_users_username     ON users(username);
CREATE INDEX idx_tenants_slug       ON tenants(slug);
CREATE INDEX idx_tenants_domain     ON tenants(domain);

