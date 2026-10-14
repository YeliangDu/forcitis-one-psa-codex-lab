package com.forcitis.one.platform.approval.api;

import java.util.List;
import java.util.Objects;

/** A hook's verdict. variableUpdates are written to the process before the task completes (RULE-277). */
public record HookDecision(HookOutcome outcome, String message, List<ApprovalField> variableUpdates) {

    public HookDecision {
        Objects.requireNonNull(outcome, "outcome");
        variableUpdates = List.copyOf(variableUpdates);
    }

    public static HookDecision allow() {
        return new HookDecision(HookOutcome.ALLOW, null, List.of());
    }

    public static HookDecision block(HookOutcome outcome, String message) {
        return new HookDecision(outcome, message, List.of());
    }
}
