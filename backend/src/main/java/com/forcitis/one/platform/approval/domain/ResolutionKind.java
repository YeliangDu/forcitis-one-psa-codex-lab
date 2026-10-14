package com.forcitis.one.platform.approval.domain;

/** How a candidate set came about; anything but RESOLVED is worth an alert or a replay classification. */
public enum ResolutionKind {
    /** The rule produced employed people (the normal case). */
    RESOLVED,
    /** The reporting line was incomplete and the rule pushed up to a department head (RULE-250). */
    ESCALATED_REPORTING_LINE,
    /** The initiator was the only candidate and was replaced by the direct leader (RULE-307). */
    ESCALATED_SELF_APPROVAL,
    /** Nobody could be found: the process administrator takes the task and an alert is raised (RULE-250/254). */
    ADMINISTRATOR_FALLBACK,
    /** Nobody found and no administrator configured: the caller must fail loudly. */
    UNRESOLVED
}
