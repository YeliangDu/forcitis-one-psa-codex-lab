package com.forcitis.one.platform.audit.api;

/** A domain event that says, in one line, what it changed; the audit trail stores the line as the entry's detail. Ids and kinds only, no personal content. */
public interface AuditDetail {
    String auditDetail();
}
