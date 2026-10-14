package com.forcitis.one.platform.approval.application;

import com.forcitis.one.platform.approval.api.ApprovalError;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.approval.api.ApprovalHook;
import com.forcitis.one.platform.approval.api.HookAction;
import com.forcitis.one.platform.approval.api.HookContext;
import com.forcitis.one.platform.approval.api.HookDecision;
import com.forcitis.one.platform.approval.domain.TaskView;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/** A node without a hook key has no hook; an unregistered key blocks the action, never silently passes. */
@Component
class HookGate {

    private final Map<String, ApprovalHook> hooks = new HashMap<>();

    HookGate(ObjectProvider<ApprovalHook> registered) {
        registered.orderedStream().forEach(hook -> {
            if (hooks.putIfAbsent(hook.hookKey(), hook) != null) {
                throw new IllegalStateException("Two ApprovalHook beans register the key " + hook.hookKey());
            }
        });
    }

    HookDecision check(TaskView task, UserId actor, HookAction action, String reason) {
        Optional<String> key = task.beforeHookKey().filter(k -> !k.isBlank());
        if (key.isEmpty()) {
            return HookDecision.allow();
        }
        ApprovalHook hook = hooks.get(key.get());
        if (hook == null) {
            throw ApprovalError.PLATFORM_APPROVAL_HOOK_NOT_REGISTERED.refusal("Node " + task.nodeKey() + " names the BEFORE hook " + key.get() + ", which is not registered");
        }
        HookDecision decision = hook.before(new HookContext(task.processKey(), task.document(), task.instanceId(),
                task.taskId(), task.nodeKey(), actor, action, reason));
        if (decision == null || !decision.outcome().commits()) {
            String message = decision == null ? "The hook returned no decision" : decision.outcome() + ": " + decision.message();
            throw ApprovalError.PLATFORM_APPROVAL_HOOK_BLOCKED.refusal(message);
        }
        return decision;
    }
}
