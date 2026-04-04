package com.lawforyou.user.entity;

/**
 * Named constants for the four platform-wide system roles seeded in V1 migration.
 * Use these instead of raw strings throughout application logic.
 *
 * <p>System roles have {@code system_role = TRUE} in the DB and
 * {@code tenant_id = NULL} — they are global and cannot be deleted.</p>
 */
public enum SystemRole {

    ADMIN,
    LAWYER,
    CLIENT,
    STAFF;

    /** Returns the role name as stored in the database. */
    public String roleName() {
        return this.name();
    }
}

