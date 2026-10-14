package com.forcitis.one.platform.identity.web;

import com.forcitis.one.platform.identity.api.IdentityError;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

final class IdentityResponses {

    private IdentityResponses() {
    }

    static ResponseEntity<ErrorResponse> error(IdentityError error, String legacyLoginUrl) {
        return ResponseEntity.status(statusOf(error)).body(new ErrorResponse(error.code(), legacyLoginUrl));
    }

    static HttpStatus statusOf(IdentityError error) {
        return switch (error) {
            case PLATFORM_IDENTITY_CREDENTIALS_INVALID, PLATFORM_IDENTITY_STATE_INVALID, PLATFORM_IDENTITY_WECOM_CODE_INVALID,
                    PLATFORM_IDENTITY_WECOM_NOT_BOUND, PLATFORM_IDENTITY_TICKET_INVALID, PLATFORM_IDENTITY_TICKET_REPLAYED,
                    PLATFORM_IDENTITY_LOGIN_NAME_UNKNOWN, PLATFORM_IDENTITY_NOT_SIGNED_IN -> HttpStatus.UNAUTHORIZED;
            case PLATFORM_IDENTITY_NETWORK_DENIED, PLATFORM_IDENTITY_ACCOUNT_DISABLED, PLATFORM_IDENTITY_EMPLOYEE_SEPARATED,
                    PLATFORM_IDENTITY_ROLE_NOT_ALLOWED -> HttpStatus.FORBIDDEN;
            case PLATFORM_IDENTITY_SSO_UNAVAILABLE, PLATFORM_IDENTITY_DEFAULT_ROLE_MISSING -> HttpStatus.SERVICE_UNAVAILABLE;
            case PLATFORM_IDENTITY_ROLE_NOT_FOUND, PLATFORM_IDENTITY_ACCOUNT_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case PLATFORM_IDENTITY_ROLE_DUPLICATE_CODE, PLATFORM_IDENTITY_ROLE_DUPLICATE_NAME,
                    PLATFORM_IDENTITY_ROLE_SYSTEM_FROZEN, PLATFORM_IDENTITY_ROLE_TRANSITION_ILLEGAL,
                    PLATFORM_IDENTITY_ACCOUNT_TRANSITION_ILLEGAL, PLATFORM_IDENTITY_CONCURRENT_UPDATE -> HttpStatus.CONFLICT;
            case PLATFORM_IDENTITY_ROLE_INVALID_CODE, PLATFORM_IDENTITY_ROLE_BLANK_NAME, PLATFORM_IDENTITY_ROLE_RESERVED_CODE,
                    PLATFORM_IDENTITY_PERMISSION_UNKNOWN -> HttpStatus.UNPROCESSABLE_CONTENT;
        };
    }
}
