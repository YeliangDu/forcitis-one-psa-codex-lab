package com.forcitis.one.platform.identity.domain;

/** Where a role version came from (role_versions.change_channel_kind, ck_role_versions_change_channel_kind). */
public enum ChangeChannel {
    /** The start-up reconciliation of the system role declaration. */
    SEED,
    /** The role management of a person. */
    MANUAL,
    /** The migration of old roles (not built yet). */
    MIGRATION
}
