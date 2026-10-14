package com.forcitis.one.platform.approval.infrastructure.replay;

/**
 * Result of replaying one node (REIMAGINED_ARCHITECTURE §3.5 (a)). MATCH means every approver who actually acted is in the new
 * candidate set. The REVIEWED_* verdicts are differences that the reviewed rules (RULE_REVIEWS.md) change on purpose, and are
 * accepted by category. DEFECT is anything else: a real defect of the new engine (or of the export), fixed one by one.
 * Differences between the source and the old tables are step (b) and never appear here.
 */
enum ReplayVerdict {
    MATCH,
    /** The old approver was disabled or separated; the new candidate set excludes such people (RULE-253). */
    REVIEWED_DISABLED_APPROVER,
    /** The old approver was a hard-coded fallback person; the new rule goes to the process administrator (RULE-254). */
    REVIEWED_HARDCODED_FALLBACK,
    /** The old reporting line was incomplete; the new rule pushes up to a department head (RULE-250). */
    REVIEWED_REPORTING_LINE,
    /** The initiator approved their own request; the new guard escalates (RULE-307). */
    REVIEWED_SELF_APPROVAL,
    /** Nobody could be found; the new engine routes to the process administrator and alerts (RULE-250/254). */
    REVIEWED_ADMINISTRATOR_FALLBACK,
    DEFECT
}
