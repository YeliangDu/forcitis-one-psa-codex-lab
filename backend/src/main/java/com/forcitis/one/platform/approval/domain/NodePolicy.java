package com.forcitis.one.platform.approval.domain;

import java.util.Optional;

/**
 * Read from the node's BPMN extension element (D-1a-100: no decision table). routingFlag: the node runs only when the owning module
 * set that flag (no flag: always); skipWhenEmpty passes the node when its rule finds nobody; selfApprovalAllowed exempts it from the
 * self-approval guard; cutoff marks a node whose finished task ends the right to withdraw or cancel (RULE-309); autoApproveRepeat lets
 * an approver who already approved the same content on this node pass automatically (RULE-302); noFallback keeps the node from handing the task to
 * anyone the rule did not name: no escalation to the initiator's supervisor, no administrator fallback, an empty set is UNRESOLVED.
 */
public record NodePolicy(AssignmentRule rule, boolean selfApprovalAllowed, boolean skipWhenEmpty, Optional<String> beforeHookKey,
                         boolean cutoff, Optional<String> routingFlag, boolean autoApproveRepeat, Optional<NodeDeadline> deadline, boolean noFallback) {
}
