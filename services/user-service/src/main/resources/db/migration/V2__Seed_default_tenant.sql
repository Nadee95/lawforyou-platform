-- V2__Seed_default_tenant.sql
-- Inserts a well-known development tenant so local developers can call the API
-- immediately without manually creating a tenant first.
--
-- Fixed UUID: 00000000-0000-0000-0000-000000000001
-- Use this as the X-Tenant-ID header value in local dev & Postman/HTTP files.
--
-- NOTE: This migration is intentionally idempotent (ON CONFLICT DO NOTHING)
-- so it is safe to run against a database that already has this tenant.

INSERT INTO tenants (id, slug, name, active)
VALUES ('00000000-0000-0000-0000-000000000001', 'dev', 'Development Tenant', TRUE)
ON CONFLICT (id) DO NOTHING;

