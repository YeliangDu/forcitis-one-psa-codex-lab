package com.forcitis.one.platform.audit.domain;

/** Reading side of the audit trail. */
public interface AuditEntryQueries {

    AuditPage findPage(AuditQuery query);
}
