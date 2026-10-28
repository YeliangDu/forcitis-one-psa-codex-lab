package com.forcitis.one.platform.identity.domain;

import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.platform.identity.api.IdentityError;

public class RoleRuleException extends DomainException {

    private final transient RoleViolation violation;

    public RoleRuleException(RoleViolation violation) {
        super(codeOf(violation), codeOf(violation).kind(), violation.name());
        this.violation = violation;
    }

    public RoleViolation violation() {
        return violation;
    }

    private static IdentityError codeOf(RoleViolation violation) {
        return switch (violation) {
            case INVALID_CODE -> IdentityError.PLATFORM_IDENTITY_ROLE_INVALID_CODE;
            case BLANK_NAME -> IdentityError.PLATFORM_IDENTITY_ROLE_BLANK_NAME;
            case RESERVED_CODE -> IdentityError.PLATFORM_IDENTITY_ROLE_RESERVED_CODE;
            case DUPLICATE_CODE -> IdentityError.PLATFORM_IDENTITY_ROLE_DUPLICATE_CODE;
            case DUPLICATE_NAME -> IdentityError.PLATFORM_IDENTITY_ROLE_DUPLICATE_NAME;
            case SYSTEM_FROZEN -> IdentityError.PLATFORM_IDENTITY_ROLE_SYSTEM_FROZEN;
            case UNKNOWN_PERMISSION -> IdentityError.PLATFORM_IDENTITY_PERMISSION_UNKNOWN;
            case ADMINISTRATOR_MODE_ONLY -> IdentityError.PLATFORM_IDENTITY_PERMISSION_ADMINISTRATOR_MODE_ONLY;
            case CUSTOM_ORG_UNITS_INVALID -> IdentityError.PLATFORM_IDENTITY_ROLE_CUSTOM_ORG_UNITS_INVALID;
            case SCOPE_NOT_ALLOWED -> IdentityError.PLATFORM_IDENTITY_ROLE_SCOPE_NOT_ALLOWED;
        };
    }
}
