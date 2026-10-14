package com.forcitis.one.platform.identity.domain;

public class RoleRuleException extends RuntimeException {

    private final RoleViolation violation;

    public RoleRuleException(RoleViolation violation) {
        super(violation.name());
        this.violation = violation;
    }

    public RoleViolation violation() {
        return violation;
    }
}
