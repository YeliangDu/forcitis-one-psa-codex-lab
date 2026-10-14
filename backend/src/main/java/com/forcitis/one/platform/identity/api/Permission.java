package com.forcitis.one.platform.identity.api;

/** Code format (G43): {subdomain}.{bc}:{resource}:{action}, action from standard_actions in glossary/terms.yaml; use Permission.Codes in @PreAuthorize. */
public enum Permission {
    TIMESHEET_SUBMIT(Codes.TIMESHEET_SUBMIT),
    TIMESHEET_APPROVE(Codes.TIMESHEET_APPROVE),
    DELIVERY_BUDGET_UPDATE(Codes.DELIVERY_BUDGET_UPDATE),
    PLATFORM_NUMBERING_RULE_UPDATE(Codes.PLATFORM_NUMBERING_RULE_UPDATE),
    PLATFORM_NUMBERING_RULE_READ(Codes.PLATFORM_NUMBERING_RULE_READ),
    PLATFORM_NUMBERING_LEGACY_NUMBER_READ(Codes.PLATFORM_NUMBERING_LEGACY_NUMBER_READ),
    PLATFORM_APPROVAL_TASK_READ(Codes.PLATFORM_APPROVAL_TASK_READ),
    PLATFORM_APPROVAL_TASK_APPROVE(Codes.PLATFORM_APPROVAL_TASK_APPROVE),
    PLATFORM_IDENTITY_ROLE_READ(Codes.PLATFORM_IDENTITY_ROLE_READ),
    PLATFORM_IDENTITY_ROLE_UPDATE(Codes.PLATFORM_IDENTITY_ROLE_UPDATE),
    PLATFORM_AUDIT_ENTRY_READ(Codes.PLATFORM_AUDIT_ENTRY_READ),
    ORGANIZATION_WORKFORCE_ORG_UNIT_READ(Codes.ORGANIZATION_WORKFORCE_ORG_UNIT_READ),
    ORGANIZATION_WORKFORCE_SYNC_RUN_READ(Codes.ORGANIZATION_WORKFORCE_SYNC_RUN_READ),
    ORGANIZATION_WORKFORCE_SYNC_RUN_EXECUTE(Codes.ORGANIZATION_WORKFORCE_SYNC_RUN_EXECUTE),
    ORGANIZATION_WORKFORCE_LEGAL_ENTITY_UPDATE(Codes.ORGANIZATION_WORKFORCE_LEGAL_ENTITY_UPDATE),
    ORGANIZATION_ATTENDANCE_WORK_CALENDAR_READ(Codes.ORGANIZATION_ATTENDANCE_WORK_CALENDAR_READ),
    ORGANIZATION_ATTENDANCE_WORK_CALENDAR_UPDATE(Codes.ORGANIZATION_ATTENDANCE_WORK_CALENDAR_UPDATE);

    private final String code;

    Permission(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public interface Codes {
        String TIMESHEET_SUBMIT = "cost.timesheet:timesheet:submit";
        String TIMESHEET_APPROVE = "cost.timesheet:timesheet:approve";
        String DELIVERY_BUDGET_UPDATE = "delivery.execution:project-budget:update";
        String PLATFORM_NUMBERING_RULE_UPDATE = "platform.numbering:numbering-rule:update";
        String PLATFORM_NUMBERING_RULE_READ = "platform.numbering:numbering-rule:read";
        String PLATFORM_NUMBERING_LEGACY_NUMBER_READ = "platform.numbering:legacy-number:read";
        String PLATFORM_APPROVAL_TASK_READ = "platform.approval:task:read";
        String PLATFORM_APPROVAL_TASK_APPROVE = "platform.approval:task:approve";
        String PLATFORM_IDENTITY_ROLE_READ = "platform.identity:role:read";
        String PLATFORM_IDENTITY_ROLE_UPDATE = "platform.identity:role:update";
        String PLATFORM_AUDIT_ENTRY_READ = "platform.audit:audit-entry:read";
        String ORGANIZATION_WORKFORCE_ORG_UNIT_READ = "organization.workforce:org-unit:read";
        String ORGANIZATION_WORKFORCE_SYNC_RUN_READ = "organization.workforce:sync-run:read";
        String ORGANIZATION_WORKFORCE_SYNC_RUN_EXECUTE = "organization.workforce:sync-run:execute";
        String ORGANIZATION_WORKFORCE_LEGAL_ENTITY_UPDATE = "organization.workforce:legal-entity:update";
        String ORGANIZATION_ATTENDANCE_WORK_CALENDAR_READ = "organization.attendance:work-calendar:read";
        String ORGANIZATION_ATTENDANCE_WORK_CALENDAR_UPDATE = "organization.attendance:work-calendar:update";
    }
}
