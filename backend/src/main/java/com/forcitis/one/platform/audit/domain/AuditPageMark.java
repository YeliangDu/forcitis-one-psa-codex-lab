package com.forcitis.one.platform.audit.domain;

import java.time.Instant;

/** The sort key of the last entry of a page; the next page starts after it. */
public record AuditPageMark(Instant createdAt, AuditEntryId entryId) {
}
