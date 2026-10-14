package com.forcitis.one.platform.identity.domain;

/** Only ENABLED roles count for permissions and data scope (RULE-343/349). */
public enum RoleStatus {
    ENABLED,
    DISABLED
}
