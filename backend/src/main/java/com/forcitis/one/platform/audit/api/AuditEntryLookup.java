package com.forcitis.one.platform.audit.api;

/** Lets another module check that an audit entry it is about to refer to exists; the entry itself is not handed out. */
public interface AuditEntryLookup {

    boolean exists(AuditEntryId id);
}
