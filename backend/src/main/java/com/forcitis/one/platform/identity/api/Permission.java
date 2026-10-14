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
    PLATFORM_NUMBERING_RULE_UPDATE(Codes.PLATFORM_NUMBERING_RULE_UPDATE),
    PLATFORM_NUMBERING_RULE_READ(Codes.PLATFORM_NUMBERING_RULE_READ),
    PLATFORM_APPROVAL_TASK_READ(Codes.PLATFORM_APPROVAL_TASK_READ),
    PLATFORM_APPROVAL_TASK_APPROVE(Codes.PLATFORM_APPROVAL_TASK_APPROVE),
    PLATFORM_IDENTITY_ROLE_READ(Codes.PLATFORM_IDENTITY_ROLE_READ),
    PLATFORM_IDENTITY_ROLE_UPDATE(Codes.PLATFORM_IDENTITY_ROLE_UPDATE),
    PLATFORM_AUDIT_ENTRY_READ(Codes.PLATFORM_AUDIT_ENTRY_READ),
    ORGANIZATION_WORKFORCE_ORG_UNIT_READ(Codes.ORGANIZATION_WORKFORCE_ORG_UNIT_READ),
    ORGANIZATION_WORKFORCE_SYNC_RUN_READ(Codes.ORGANIZATION_WORKFORCE_SYNC_RUN_READ),
    ORGANIZATION_WORKFORCE_SYNC_RUN_EXECUTE(Codes.ORGANIZATION_WORKFORCE_SYNC_RUN_EXECUTE);

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
        String PLATFORM_NUMBERING_RULE_READ = "platform.numbering:numbering-rule:read";
        /** Read the merged inbox and approval details. */
        String PLATFORM_APPROVAL_TASK_READ = "platform.approval:task:read";
        /** Claim, approve, reject or return a task the caller is a candidate for (RULE-276). */
        String PLATFORM_APPROVAL_TASK_APPROVE = "platform.approval:task:approve";
        String PLATFORM_IDENTITY_ROLE_READ = "platform.identity:role:read";
        String PLATFORM_IDENTITY_ROLE_UPDATE = "platform.identity:role:update";
        String PLATFORM_AUDIT_ENTRY_READ = "platform.audit:audit-entry:read";
        String ORGANIZATION_WORKFORCE_ORG_UNIT_READ = "organization.workforce:org-unit:read";
        String ORGANIZATION_WORKFORCE_SYNC_RUN_READ = "organization.workforce:sync-run:read";
        /** Release a sync run held by the shrink guard (RULE-358/366). */
        String ORGANIZATION_WORKFORCE_SYNC_RUN_EXECUTE = "organization.workforce:sync-run:execute";
    }
}
