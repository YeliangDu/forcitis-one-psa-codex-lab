package com.forcitis.one.organization.workforce.web;

/**
 * Permission codes this module's endpoints require. They are owned by platform.identity (Permission.Codes); the
 * strings are repeated here because organization.workforce may not depend on platform.identity (module graph:
 * identity depends on workforce). WorkforcePermissionsTest keeps both lists identical, and NamingSurfaceTest (G43)
 * checks every @PreAuthorize code against Permission.Codes.
 */
final class WorkforcePermissions {

    static final String ORG_UNIT_READ = "organization.workforce:org-unit:read";
    static final String SYNC_RUN_READ = "organization.workforce:sync-run:read";
    static final String SYNC_RUN_EXECUTE = "organization.workforce:sync-run:execute";
    static final String LEGAL_ENTITY_UPDATE = "organization.workforce:legal-entity:update";

    private WorkforcePermissions() {
    }
}
