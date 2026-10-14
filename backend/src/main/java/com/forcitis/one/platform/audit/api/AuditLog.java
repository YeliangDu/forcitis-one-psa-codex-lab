package com.forcitis.one.platform.audit.api;

public interface AuditLog {
    /** Joins the caller's transaction: the entry exists only if the business change commits. */
    void record(AuditEntry entry);

    /** Own transaction: survives the caller's rollback (failed logins, denied actions). */
    void recordIndependently(AuditEntry entry);
}
