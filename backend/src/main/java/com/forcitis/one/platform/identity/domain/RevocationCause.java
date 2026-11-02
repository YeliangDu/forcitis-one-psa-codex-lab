package com.forcitis.one.platform.identity.domain;

/** Why a grant was closed (account_role_grants.revocation_cause_kind); MIGRATION_ROLLBACK is written by the migration only. */
public enum RevocationCause {
    MANUAL,
    SEPARATION,
    OPERATIONS
}
