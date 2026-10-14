package com.forcitis.one.platform.approval.domain;

/** Anything but RESOLVED is worth an alert or a replay classification. */
public enum ResolutionKind {
    RESOLVED,
    ESCALATED_REPORTING_LINE,
    ESCALATED_SELF_APPROVAL,
    ADMINISTRATOR_FALLBACK,
    SKIPPED,
    UNRESOLVED
}
