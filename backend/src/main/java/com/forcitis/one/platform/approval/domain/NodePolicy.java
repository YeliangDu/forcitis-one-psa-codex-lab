package com.forcitis.one.platform.approval.domain;

/**
 * Everything the BPMN says about one user task, read from its forcitis:assignmentRule element (RULE-262: exactly one).
 * selfApprovalAllowed exempts employee activities from the self-approval guard (RULE-307);
 * beforeHookKey names the BEFORE hook (RULE-283), null = none.
 */
public record NodePolicy(AssignmentRule rule, boolean selfApprovalAllowed, String beforeHookKey) {
}
