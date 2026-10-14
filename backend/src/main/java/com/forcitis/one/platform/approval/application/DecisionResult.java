package com.forcitis.one.platform.approval.application;

/** Where the approval stands after the decision: RUNNING, RETURNED, APPROVED or REJECTED. */
public record DecisionResult(String instanceId, String status) {
}
