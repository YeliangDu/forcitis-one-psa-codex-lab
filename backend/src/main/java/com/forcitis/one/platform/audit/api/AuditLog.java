package com.forcitis.one.platform.audit.api;

public interface AuditLog {
    /** Needs the caller's transaction and joins it: the entry exists only if the business change commits. Returns the id of the entry written. */
    AuditEntryId record(AuditEntry entry);

    /** Own transaction: survives the caller's rollback (failed logins, denied actions). */
    void recordIndependently(AuditEntry entry);
}
