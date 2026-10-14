package com.forcitis.one.platform.approval.domain;

/** skipWhenEmpty passes the node when its rule finds nobody; selfApprovalAllowed exempts it from the self-approval guard; beforeHookKey null = none. */
public record NodePolicy(AssignmentRule rule, boolean selfApprovalAllowed, boolean skipWhenEmpty, String beforeHookKey) {
}
