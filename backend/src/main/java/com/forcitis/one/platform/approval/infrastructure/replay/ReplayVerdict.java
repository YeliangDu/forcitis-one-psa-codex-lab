package com.forcitis.one.platform.approval.infrastructure.replay;

/** REVIEWED_* are differences the reviewed rules change on purpose; DEFECT is anything else. */
enum ReplayVerdict {
    MATCH,
    REVIEWED_DISABLED_APPROVER,
    REVIEWED_HARDCODED_FALLBACK,
    REVIEWED_REPORTING_LINE,
    REVIEWED_SELF_APPROVAL,
    REVIEWED_ADMINISTRATOR_FALLBACK,
    DEFECT
}
