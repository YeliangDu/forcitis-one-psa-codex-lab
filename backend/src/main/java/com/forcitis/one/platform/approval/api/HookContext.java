package com.forcitis.one.platform.approval.api;

import com.forcitis.one.kernel.UserId;
import java.util.Objects;

/** What the server tells a BEFORE hook; rebuilt from the engine, never from the client (RULE-283). */
public record HookContext(ProcessKey processKey, String businessKey, ApprovalInstanceId instanceId, ApprovalTaskId taskId,
                          String nodeKey, UserId actor, HookAction action, String reason) {

    public HookContext {
        Objects.requireNonNull(processKey, "processKey");
        Objects.requireNonNull(businessKey, "businessKey");
        Objects.requireNonNull(instanceId, "instanceId");
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(nodeKey, "nodeKey");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(action, "action");
    }
}
