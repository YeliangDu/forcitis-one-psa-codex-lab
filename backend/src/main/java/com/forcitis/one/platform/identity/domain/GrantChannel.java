package com.forcitis.one.platform.identity.domain;

/** How a grant was opened (account_role_grants.grant_channel_kind); MIGRATION rows are written by the migration only. */
public enum GrantChannel {
    MANUAL,
    DEFAULT_ROLE,
    OPERATIONS
}
