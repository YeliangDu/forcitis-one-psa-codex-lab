package com.forcitis.one.platform.identity.domain;

/** Why a role change was refused (RULE-345). */
public enum RoleViolation {
    INVALID_CODE,
    BLANK_NAME,
    RESERVED_CODE,
    DUPLICATE_CODE,
    DUPLICATE_NAME,
    SYSTEM_FROZEN,
    UNKNOWN_PERMISSION
}
