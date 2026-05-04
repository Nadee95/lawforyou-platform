-- V6__Add_keycloak_id.sql
-- Adds a nullable keycloak_id column to the users table.
--
-- Populated on user creation (Phase 4+) when Keycloak Admin API integration is enabled.
-- Nullable because:
--   1. Existing users seeded before Phase 4 don't yet have a Keycloak identity.
--   2. Tests run with app.keycloak.enabled=false and never call the Keycloak Admin API.
--
-- In Phase 8 (legacy removal), a follow-up migration backfills missing values
-- via Admin API sync and adds a NOT NULL constraint.

ALTER TABLE users ADD COLUMN IF NOT EXISTS keycloak_id UUID;
CREATE UNIQUE INDEX IF NOT EXISTS users_keycloak_id_idx ON users(keycloak_id)
    WHERE keycloak_id IS NOT NULL;

