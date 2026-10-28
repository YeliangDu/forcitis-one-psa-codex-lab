package com.forcitis.one.platform.identity.domain;

public enum RoleViolation {
    INVALID_CODE,
    BLANK_NAME,
    RESERVED_CODE,
    DUPLICATE_CODE,
    DUPLICATE_NAME,
    SYSTEM_FROZEN,
    UNKNOWN_PERMISSION,
    ADMINISTRATOR_MODE_ONLY,
    /** CUSTOM_ORG_UNITS without any unit, the same unit listed twice, or units under another scope kind (F-1 card, constraint 7). */
    CUSTOM_ORG_UNITS_INVALID,
    /** A role with a two-person permission takes only ALL, CUSTOM_ORG_UNITS or SELF, and lists its units without descendants (ADR-0032 section 3, ADR-0044 section 1). */
    SCOPE_NOT_ALLOWED
}
