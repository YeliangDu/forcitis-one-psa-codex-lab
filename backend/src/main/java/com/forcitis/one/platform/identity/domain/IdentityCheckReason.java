package com.forcitis.one.platform.identity.domain;

/** Why a login from another site had to be confirmed by the person; the audit trail carries exactly this name and nothing more. */
public enum IdentityCheckReason {
    NO_IDENTIFIER,
    UNKNOWN_IDENTIFIER,
    EXPIRED_IDENTIFIER,
    REPLAYED_IDENTIFIER
}
