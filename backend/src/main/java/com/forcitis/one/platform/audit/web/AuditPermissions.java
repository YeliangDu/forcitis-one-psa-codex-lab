package com.forcitis.one.platform.audit.web;

/** Repeated from platform.identity (Permission.Codes) because platform.audit may not depend on it; AuditPermissionsTest keeps them equal. */
final class AuditPermissions {

    static final String ENTRY_READ = "platform.audit:audit-entry:read";
    static final String ENTRY_EXPORT = "platform.audit:audit-entry:export";
    static final String OWN_LOGIN_RECORD_READ = "platform.audit:own-login-record:read";

    private AuditPermissions() {
    }
}
