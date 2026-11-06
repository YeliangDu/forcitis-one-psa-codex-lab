package com.forcitis.one.organization.recruitment.web;

/** Copies of platform.identity Permission.Codes (recruitment may not depend on identity); NamingSurfaceTest G43 checks them. */
final class RecruitmentPermissions {
    static final String SUBMIT = "organization.recruitment:requisition:submit";
    static final String READ = "organization.recruitment:requisition:read";
    static final String UPDATE = "organization.recruitment:requisition:update";

    /** Copy of platform.identity SessionRoles.ORDINARY: an administrator-mode session holds no business reads (I-11). */
    static final String ORDINARY_SESSION = "ORDINARY_SESSION";

    private RecruitmentPermissions() {
    }
}
