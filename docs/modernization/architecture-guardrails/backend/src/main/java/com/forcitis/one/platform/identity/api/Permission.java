package com.forcitis.one.platform.identity.api;

/**
 * Typed permission catalogue. Use in @PreAuthorize via Permission.Codes constants (annotation values must be constants).
 * Code format (G43): {subdomain}.{bc}:{resource}:{action} - the owning module, a kebab-case glossary resource and an
 * action from standard_actions in glossary/terms.yaml, e.g. cost.timesheet:timesheet:submit.
 */
public enum Permission {
    TIMESHEET_SUBMIT(Codes.TIMESHEET_SUBMIT),
    TIMESHEET_APPROVE(Codes.TIMESHEET_APPROVE),
    DELIVERY_BUDGET_UPDATE(Codes.DELIVERY_BUDGET_UPDATE),
    PLATFORM_NUMBERING_RULE_UPDATE(Codes.PLATFORM_NUMBERING_RULE_UPDATE);

    private final String code;

    Permission(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    /** Compile-time constants for annotations: hasAuthority('cost.timesheet:timesheet:submit'). */
    public interface Codes {
        String TIMESHEET_SUBMIT = "cost.timesheet:timesheet:submit";
        String TIMESHEET_APPROVE = "cost.timesheet:timesheet:approve";
        String DELIVERY_BUDGET_UPDATE = "delivery.execution:budget:update";
        String PLATFORM_NUMBERING_RULE_UPDATE = "platform.numbering:numbering-rule:update";
    }
}
