package com.forcitis.one.organization.workforce.web;

/** Copies of platform.identity Permission.Codes (workforce may not depend on identity); NamingSurfaceTest G43 checks them. */
final class WorkforcePermissions {
    static final String ORG_UNIT_READ = "organization.workforce:org-unit:read";
    static final String SYNC_RUN_READ = "organization.workforce:sync-run:read";
    static final String SYNC_RUN_EXECUTE = "organization.workforce:sync-run:execute";
    static final String LEGAL_ENTITY_UPDATE = "organization.workforce:legal-entity:update";
    static final String LEGAL_ENTITY_ATTRIBUTION_CORRECTION_CREATE = "organization.workforce:legal-entity-attribution-correction:create";

    private WorkforcePermissions() {
    }
}
