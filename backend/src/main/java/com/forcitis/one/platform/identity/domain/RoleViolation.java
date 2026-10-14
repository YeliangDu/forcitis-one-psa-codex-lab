package com.forcitis.one.platform.identity.domain;

public enum RoleViolation {
    INVALID_CODE,
    BLANK_NAME,
    RESERVED_CODE,
    DUPLICATE_CODE,
    DUPLICATE_NAME,
    SYSTEM_FROZEN,
    UNKNOWN_PERMISSION
}
