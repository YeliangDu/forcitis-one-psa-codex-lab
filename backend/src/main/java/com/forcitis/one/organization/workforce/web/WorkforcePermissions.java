package com.forcitis.one.organization.workforce.web;

/** Compatibility aliases for the retained WorkforceConfigurationTest seam; metadata lives in workforce.api. */
final class WorkforcePermissions {
    static final String EMPLOYEE_READ = com.forcitis.one.organization.workforce.api.WorkforcePermissions.EMPLOYEE_READ;
    static final String ORG_UNIT_READ = com.forcitis.one.organization.workforce.api.WorkforcePermissions.ORG_UNIT_READ;
    static final String SEPARATION_HOLD_APPROVE = com.forcitis.one.organization.workforce.api.WorkforcePermissions.SEPARATION_HOLD_APPROVE;
    static final String PERSONNEL_COMMAND_CREATE = com.forcitis.one.organization.workforce.api.WorkforcePermissions.PERSONNEL_COMMAND_CREATE;
    static final String LEGAL_ENTITY_UPDATE = com.forcitis.one.organization.workforce.api.WorkforcePermissions.LEGAL_ENTITY_UPDATE;
    static final String LEGAL_ENTITY_ATTRIBUTION_APPROVE = com.forcitis.one.organization.workforce.api.WorkforcePermissions.LEGAL_ENTITY_ATTRIBUTION_APPROVE;
    static final String ORG_STRUCTURE_COMMAND_CREATE = com.forcitis.one.organization.workforce.api.WorkforcePermissions.ORG_STRUCTURE_COMMAND_CREATE;
    static final String LEGAL_ENTITY_ATTRIBUTION_CORRECTION_CREATE = com.forcitis.one.organization.workforce.api.WorkforcePermissions.LEGAL_ENTITY_ATTRIBUTION_CORRECTION_CREATE;

    private WorkforcePermissions() {
    }
}
