package com.forcitis.one.platform.approval.domain;

import java.util.Optional;

/**
 * skipWhenEmpty passes the node when its rule finds nobody (a decision table may override it); selfApprovalAllowed exempts it from the
 * self-approval guard; cutoff marks a node whose finished task ends the right to withdraw or cancel (RULE-309).
 */
public record NodePolicy(AssignmentRule rule, boolean selfApprovalAllowed, boolean skipWhenEmpty, Optional<String> beforeHookKey,
                         boolean cutoff) {
}
