package com.forcitis.one.platform.approval.application;

import com.forcitis.one.platform.approval.api.ApprovalTaskId;
import java.util.Objects;

public record TaskDecision(ApprovalTaskId taskId, DecisionOutcome outcome, String reason) {

    public TaskDecision {
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(outcome, "outcome");
    }
}
