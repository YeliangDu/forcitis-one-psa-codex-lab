package com.forcitis.one.platform.approval.application;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.approval.api.ApprovalHook;
import com.forcitis.one.platform.approval.api.HookAction;
import com.forcitis.one.platform.approval.api.HookContext;
import com.forcitis.one.platform.approval.api.HookDecision;
import com.forcitis.one.platform.approval.domain.TaskView;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * The BEFORE-hook commit gate (RULE-277/283). Runs inside the decide transaction, before the task completes. A node
 * without a hook key has no hook; a key nobody registered blocks the action (never silently passes); two hooks with the
 * same key stop startup.
 */
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

    /** Returns the decision of a committing hook (its variable updates are applied by the caller); throws when blocked. */
    HookDecision check(TaskView task, UserId actor, HookAction action, String reason) {
        String key = task.beforeHookKey();
        if (key == null || key.isBlank()) {
            return HookDecision.allow();
        }
        ApprovalHook hook = hooks.get(key);
        if (hook == null) {
            throw new ApprovalOperationException(ApprovalError.PLATFORM_APPROVAL_HOOK_NOT_REGISTERED,
                    "Node " + task.nodeKey() + " names the BEFORE hook " + key + ", which is not registered");
        }
        HookDecision decision = hook.before(new HookContext(task.processKey(), task.businessKey(), task.instanceId(),
                task.taskId(), task.nodeKey(), actor, action, reason));
        if (decision == null || !decision.outcome().commits()) {
            String message = decision == null ? "The hook returned no decision" : decision.outcome() + ": " + decision.message();
            throw new ApprovalOperationException(ApprovalError.PLATFORM_APPROVAL_HOOK_BLOCKED, message);
        }
        return decision;
    }
}
