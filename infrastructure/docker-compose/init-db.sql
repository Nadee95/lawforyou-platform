-- Creates each service database inside the shared PostgreSQL instance.
-- Runs automatically when the postgres container starts for the first time.

CREATE DATABASE user_db;
CREATE DATABASE case_db;
CREATE DATABASE keycloak_db;   -- Keycloak identity provider (Phase 2+)

