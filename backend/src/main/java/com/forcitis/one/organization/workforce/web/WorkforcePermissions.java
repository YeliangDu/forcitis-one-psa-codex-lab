package com.forcitis.one.organization.workforce.web;

/** Copies of platform.identity Permission.Codes (workforce may not depend on identity); NamingSurfaceTest G43 checks them. */
final class WorkforcePermissions {
    static final String EMPLOYEE_READ = "organization.workforce:employee:read";
    static final String ORG_UNIT_READ = "organization.workforce:org-unit:read";
    static final String SEPARATION_HOLD_APPROVE = "organization.workforce:separation-hold:approve";
    static final String PERSONNEL_COMMAND_CREATE = "organization.workforce:personnel-command:create";
    static final String LEGAL_ENTITY_UPDATE = "organization.workforce:legal-entity:update";
    static final String LEGAL_ENTITY_ATTRIBUTION_APPROVE = "organization.workforce:legal-entity-attribution:approve";
    static final String ORG_STRUCTURE_COMMAND_CREATE = "organization.workforce:org-structure-command:create";
    static final String LEGAL_ENTITY_ATTRIBUTION_CORRECTION_CREATE = "organization.workforce:legal-entity-attribution-correction:create";

    private WorkforcePermissions() {
    }
}
