package com.forcitis.one.platform.identity.api;

import com.forcitis.one.kernel.PermissionGrantMode;
import com.forcitis.one.organization.workforce.api.WorkforcePermissions;

/** Code format (G43): {subdomain}.{bc}:{resource}:{action}, action from standard_actions in glossary/terms.yaml; use Permission.Codes in @PreAuthorize. */
public enum Permission {
    TIMESHEET_SUBMIT(Codes.TIMESHEET_SUBMIT),
    TIMESHEET_APPROVE(Codes.TIMESHEET_APPROVE),
    PLATFORM_NUMBERING_RULE_UPDATE(Codes.PLATFORM_NUMBERING_RULE_UPDATE),
    PLATFORM_NUMBERING_RULE_READ(Codes.PLATFORM_NUMBERING_RULE_READ),
    PLATFORM_NUMBERING_RULE_APPROVE(Codes.PLATFORM_NUMBERING_RULE_APPROVE, PermissionGrantMode.TWO_PERSON_INDIVIDUAL),
    PLATFORM_NUMBERING_VOUCHER_RULE_APPROVE(Codes.PLATFORM_NUMBERING_VOUCHER_RULE_APPROVE, PermissionGrantMode.TWO_PERSON_INDIVIDUAL),
    PLATFORM_NUMBERING_LEGACY_NUMBER_READ(Codes.PLATFORM_NUMBERING_LEGACY_NUMBER_READ),
    PLATFORM_APPROVAL_TASK_READ(Codes.PLATFORM_APPROVAL_TASK_READ),
    PLATFORM_APPROVAL_TASK_APPROVE(Codes.PLATFORM_APPROVAL_TASK_APPROVE),
    PLATFORM_IDENTITY_ROLE_READ(Codes.PLATFORM_IDENTITY_ROLE_READ),
    PLATFORM_IDENTITY_ROLE_UPDATE(Codes.PLATFORM_IDENTITY_ROLE_UPDATE),
    PLATFORM_IDENTITY_ACCOUNT_ROLE_UPDATE(Codes.PLATFORM_IDENTITY_ACCOUNT_ROLE_UPDATE, PermissionGrantMode.TWO_PERSON_INDIVIDUAL),
    PLATFORM_IDENTITY_SESSION_DELETE(Codes.PLATFORM_IDENTITY_SESSION_DELETE),
    PLATFORM_IDENTITY_PERMISSION_ADJUSTMENT_READ(Codes.PLATFORM_IDENTITY_PERMISSION_ADJUSTMENT_READ),
    PLATFORM_IDENTITY_PERMISSION_ADJUSTMENT_UPDATE(Codes.PLATFORM_IDENTITY_PERMISSION_ADJUSTMENT_UPDATE),
    PLATFORM_INTEGRATION_SOURCE_REFERENCE_UPDATE(Codes.PLATFORM_INTEGRATION_SOURCE_REFERENCE_UPDATE),
    PLATFORM_INTEGRATION_SOURCE_REFERENCE_APPROVE(Codes.PLATFORM_INTEGRATION_SOURCE_REFERENCE_APPROVE),
    PLATFORM_FILE_FILE_CREATE(Codes.PLATFORM_FILE_FILE_CREATE),
    PLATFORM_FILE_FILE_READ(Codes.PLATFORM_FILE_FILE_READ),
    PLATFORM_AUDIT_ENTRY_READ(Codes.PLATFORM_AUDIT_ENTRY_READ, PermissionGrantMode.TWO_PERSON_INDIVIDUAL),
    PLATFORM_AUDIT_ENTRY_EXPORT(Codes.PLATFORM_AUDIT_ENTRY_EXPORT, PermissionGrantMode.TWO_PERSON_INDIVIDUAL),
    PLATFORM_AUDIT_OWN_LOGIN_RECORD_READ(Codes.PLATFORM_AUDIT_OWN_LOGIN_RECORD_READ),
    ORGANIZATION_WORKFORCE_ORG_UNIT_READ(Codes.ORGANIZATION_WORKFORCE_ORG_UNIT_READ),
    ORGANIZATION_RECRUITMENT_REQUISITION_SUBMIT(Codes.ORGANIZATION_RECRUITMENT_REQUISITION_SUBMIT),
    ORGANIZATION_RECRUITMENT_REQUISITION_READ(Codes.ORGANIZATION_RECRUITMENT_REQUISITION_READ),
    ORGANIZATION_RECRUITMENT_REQUISITION_UPDATE(Codes.ORGANIZATION_RECRUITMENT_REQUISITION_UPDATE),
    // Retired with the org reconciliation (#1222); it was granted from #934 until then and is kept, no longer granted (ADR-0034 decision 1 item 3).
    ORGANIZATION_WORKFORCE_ORG_RECONCILIATION_EXECUTE("organization.workforce:org-reconciliation:execute", true),
    // Retired with the old sync (#934); kept so that old role versions still read (ADR-0034 decision 1 item 3), no longer granted.
    ORGANIZATION_WORKFORCE_SYNC_RUN_READ("organization.workforce:sync-run:read", true),
    ORGANIZATION_WORKFORCE_SYNC_RUN_EXECUTE("organization.workforce:sync-run:execute", true),
    ORGANIZATION_WORKFORCE_SEPARATION_HOLD_APPROVE(Codes.ORGANIZATION_WORKFORCE_SEPARATION_HOLD_APPROVE, PermissionGrantMode.TWO_PERSON_INDIVIDUAL),
    ORGANIZATION_WORKFORCE_LEGAL_ENTITY_UPDATE(Codes.ORGANIZATION_WORKFORCE_LEGAL_ENTITY_UPDATE),
    ORGANIZATION_WORKFORCE_PERSONNEL_COMMAND_CREATE(Codes.ORGANIZATION_WORKFORCE_PERSONNEL_COMMAND_CREATE),
    ORGANIZATION_WORKFORCE_EMPLOYEE_READ(Codes.ORGANIZATION_WORKFORCE_EMPLOYEE_READ),
    ORGANIZATION_WORKFORCE_ORG_STRUCTURE_COMMAND_CREATE(Codes.ORGANIZATION_WORKFORCE_ORG_STRUCTURE_COMMAND_CREATE),
    ORGANIZATION_WORKFORCE_LEGAL_ENTITY_ATTRIBUTION_CORRECTION_CREATE(Codes.ORGANIZATION_WORKFORCE_LEGAL_ENTITY_ATTRIBUTION_CORRECTION_CREATE),
    ORGANIZATION_WORKFORCE_LEGAL_ENTITY_ATTRIBUTION_APPROVE(Codes.ORGANIZATION_WORKFORCE_LEGAL_ENTITY_ATTRIBUTION_APPROVE),
    ORGANIZATION_ATTENDANCE_WORK_CALENDAR_READ(Codes.ORGANIZATION_ATTENDANCE_WORK_CALENDAR_READ),
    ORGANIZATION_ATTENDANCE_WORK_CALENDAR_UPDATE(Codes.ORGANIZATION_ATTENDANCE_WORK_CALENDAR_UPDATE),
    ORGANIZATION_ATTENDANCE_LOCKED_DAY_UPDATE(Codes.ORGANIZATION_ATTENDANCE_LOCKED_DAY_UPDATE),
    ORGANIZATION_ATTENDANCE_STANDARD_HOURS_PUBLISH(Codes.ORGANIZATION_ATTENDANCE_STANDARD_HOURS_PUBLISH),
    ORGANIZATION_ATTENDANCE_STANDARD_HOURS_READ(Codes.ORGANIZATION_ATTENDANCE_STANDARD_HOURS_READ),
    ORGANIZATION_PAYROLL_BONUS_SUBMIT(Codes.ORGANIZATION_PAYROLL_BONUS_SUBMIT, PermissionGrantMode.TWO_PERSON_INDIVIDUAL),
    ORGANIZATION_PAYROLL_BONUS_UPDATE(Codes.ORGANIZATION_PAYROLL_BONUS_UPDATE, PermissionGrantMode.TWO_PERSON_INDIVIDUAL),
    ORGANIZATION_PAYROLL_BONUS_READ(Codes.ORGANIZATION_PAYROLL_BONUS_READ, PermissionGrantMode.TWO_PERSON_INDIVIDUAL),
    ACCOUNTING_PERIOD_FISCAL_YEAR_CREATE(Codes.ACCOUNTING_PERIOD_FISCAL_YEAR_CREATE);

    private final String code;
    private final PermissionGrantMode grantMode;
    /** The acceptance test DeprecatedCodeIT (role_versioning) sets this field by reflection under this name: rename it only together with that test. */
    private final boolean deprecated;

    Permission(String code) {
        this(code, false);
    }

    /** A code is never removed from the catalogue (ADR-0034 decision 1 item 3): one that is no longer granted is kept and marked deprecated. */
    Permission(String code, boolean deprecated) {
        this(code, PermissionGrantMode.INDIVIDUAL, deprecated);
    }

    Permission(String code, PermissionGrantMode grantMode) {
        this(code, grantMode, false);
    }

    Permission(String code, PermissionGrantMode grantMode, boolean deprecated) {
        this.code = code;
        this.grantMode = grantMode;
        this.deprecated = deprecated;
    }

    public PermissionGrantMode grantMode() { return grantMode; }

    public String code() {
        return code;
    }

    /** Kept so that old role versions can still be read, but no longer granted: writing it is refused, reading it ignores it with an alert. */
    public boolean deprecated() {
        return deprecated;
    }

    /** True when the code is in the catalogue and not deprecated: the only codes a role version may be written with or may grant. */
    public static boolean isLive(String code) {
        // Preserve the facade's live/deprecated read semantics for stored legacy
        // versions (including the retained DeprecatedCodeIT reflection seam).
        for (Permission permission : values()) {
            if (permission.code.equals(code)) { return !permission.deprecated; }
        }
        return PermissionCatalogue.fromClasspath().isLive(code);
    }

    public interface Codes {
        String TIMESHEET_SUBMIT = "cost.timesheet:timesheet:submit";
        String TIMESHEET_APPROVE = "cost.timesheet:timesheet:approve";
        String PLATFORM_NUMBERING_RULE_UPDATE = "platform.numbering:numbering-rule:update";
        String PLATFORM_NUMBERING_RULE_READ = "platform.numbering:numbering-rule:read";
        String PLATFORM_NUMBERING_RULE_APPROVE = "platform.numbering:numbering-rule:approve";
        String PLATFORM_NUMBERING_VOUCHER_RULE_APPROVE = "platform.numbering:voucher-numbering-rule:approve";
        String PLATFORM_NUMBERING_LEGACY_NUMBER_READ = "platform.numbering:legacy-number:read";
        String PLATFORM_APPROVAL_TASK_READ = "platform.approval:task:read";
        String PLATFORM_APPROVAL_TASK_APPROVE = "platform.approval:task:approve";
        String PLATFORM_IDENTITY_ROLE_READ = "platform.identity:role:read";
        String PLATFORM_IDENTITY_ROLE_UPDATE = "platform.identity:role:update";
        String PLATFORM_IDENTITY_ACCOUNT_ROLE_UPDATE = "platform.identity:account-role:update";
        String PLATFORM_IDENTITY_SESSION_DELETE = "platform.identity:session:delete";
        String PLATFORM_IDENTITY_PERMISSION_ADJUSTMENT_READ = "platform.identity:permission-adjustment:read";
        String PLATFORM_IDENTITY_PERMISSION_ADJUSTMENT_UPDATE = "platform.identity:permission-adjustment:update";
        String PLATFORM_INTEGRATION_SOURCE_REFERENCE_UPDATE = "platform.integration:source-reference:update";
        String PLATFORM_INTEGRATION_SOURCE_REFERENCE_APPROVE = "platform.integration:source-reference:approve";
        String PLATFORM_FILE_FILE_CREATE = "platform.file:file:create";
        String PLATFORM_FILE_FILE_READ = "platform.file:file:read";
        String PLATFORM_AUDIT_ENTRY_READ = "platform.audit:audit-entry:read";
        String PLATFORM_AUDIT_ENTRY_EXPORT = "platform.audit:audit-entry:export";
        String PLATFORM_AUDIT_OWN_LOGIN_RECORD_READ = "platform.audit:own-login-record:read";
        String ORGANIZATION_WORKFORCE_ORG_UNIT_READ = WorkforcePermissions.ORG_UNIT_READ;
        String ORGANIZATION_RECRUITMENT_REQUISITION_SUBMIT = "organization.recruitment:requisition:submit";
        String ORGANIZATION_RECRUITMENT_REQUISITION_READ = "organization.recruitment:requisition:read";
        String ORGANIZATION_RECRUITMENT_REQUISITION_UPDATE = "organization.recruitment:requisition:update";
        String ORGANIZATION_WORKFORCE_SEPARATION_HOLD_APPROVE = WorkforcePermissions.SEPARATION_HOLD_APPROVE;
        String ORGANIZATION_WORKFORCE_PERSONNEL_COMMAND_CREATE = WorkforcePermissions.PERSONNEL_COMMAND_CREATE;
        String ORGANIZATION_WORKFORCE_EMPLOYEE_READ = WorkforcePermissions.EMPLOYEE_READ;
        String ORGANIZATION_WORKFORCE_ORG_STRUCTURE_COMMAND_CREATE = WorkforcePermissions.ORG_STRUCTURE_COMMAND_CREATE;
        String ORGANIZATION_WORKFORCE_LEGAL_ENTITY_UPDATE = WorkforcePermissions.LEGAL_ENTITY_UPDATE;
        String ORGANIZATION_WORKFORCE_LEGAL_ENTITY_ATTRIBUTION_CORRECTION_CREATE = WorkforcePermissions.LEGAL_ENTITY_ATTRIBUTION_CORRECTION_CREATE;
        String ORGANIZATION_WORKFORCE_LEGAL_ENTITY_ATTRIBUTION_APPROVE = WorkforcePermissions.LEGAL_ENTITY_ATTRIBUTION_APPROVE;
        String ORGANIZATION_ATTENDANCE_WORK_CALENDAR_READ = "organization.attendance:work-calendar:read";
        String ORGANIZATION_ATTENDANCE_WORK_CALENDAR_UPDATE = "organization.attendance:work-calendar:update";
        String ORGANIZATION_ATTENDANCE_LOCKED_DAY_UPDATE = "organization.attendance:locked-day:update";
        String ORGANIZATION_ATTENDANCE_STANDARD_HOURS_PUBLISH = "organization.attendance:standard-hours:publish";
        String ORGANIZATION_ATTENDANCE_STANDARD_HOURS_READ = "organization.attendance:standard-hours:read";
        String ORGANIZATION_PAYROLL_BONUS_SUBMIT = "organization.payroll:bonus-input:submit";
        String ORGANIZATION_PAYROLL_BONUS_UPDATE = "organization.payroll:bonus-input:update";
        String ORGANIZATION_PAYROLL_BONUS_READ = "organization.payroll:bonus-input:read";
        String ACCOUNTING_PERIOD_FISCAL_YEAR_CREATE = "accounting.period:fiscal-year:create";
    }
}
