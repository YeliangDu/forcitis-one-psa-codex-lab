package com.forcitis.one.platform.approval.application;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.api.ApprovalTaskId;
import com.forcitis.one.platform.approval.api.HookAction;
import com.forcitis.one.platform.approval.api.HookDecision;
import com.forcitis.one.platform.approval.api.TaskOutcome;
import com.forcitis.one.platform.approval.domain.ApprovalEngine;
import com.forcitis.one.platform.approval.domain.ApprovalRequestRecord;
import com.forcitis.one.platform.approval.domain.ApprovalStatus;
import com.forcitis.one.platform.approval.domain.ProcessVariables;
import com.forcitis.one.platform.approval.domain.TaskView;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.audit.api.AuditLog;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Order: tasks exist (404), actor may act on every task else whole batch 403, shape rules, every BEFORE hook, then complete (approvals first, rejections last). */
@Service
public class DecideTasks {

    static final String DEFAULT_APPROVE_REASON = "同意";

    private final TaskGuard guard;
    private final HookGate hooks;
    private final ApprovalEngine engine;
    private final ApprovalLifecycle lifecycle;
    private final AuditLog audit;

    DecideTasks(TaskGuard guard, HookGate hooks, ApprovalEngine engine, ApprovalLifecycle lifecycle, AuditLog audit) {
        this.guard = guard;
        this.hooks = hooks;
        this.engine = engine;
        this.lifecycle = lifecycle;
        this.audit = audit;
    }

    @Transactional
    public DecisionResult execute(UserId actor, List<TaskDecision> decisions) {
        if (decisions == null || decisions.isEmpty()) {
            throw new ApprovalOperationException(ApprovalError.PLATFORM_APPROVAL_DECISION_EMPTY, "No decision given");
        }
        Set<ApprovalTaskId> seen = new HashSet<>();
        for (TaskDecision decision : decisions) {
            if (!seen.add(decision.taskId())) {
                throw new ApprovalOperationException(ApprovalError.PLATFORM_APPROVAL_DECISION_DUPLICATE_TASK,
                        "Task " + decision.taskId().value() + " appears twice");
            }
            guard.requireMayAct(guard.requireTask(decision.taskId()), actor, "decide");
        }
        // Serialise with other decisions, then look again: another candidate may have claimed or finished a task while we waited.
        ApprovalInstanceId instanceId = guard.requireTask(decisions.getFirst().taskId()).instanceId();
        ApprovalRequestRecord request = lifecycle.lock(instanceId);
        Map<TaskDecision, TaskView> tasks = new LinkedHashMap<>();
        for (TaskDecision decision : decisions) {
            TaskView fresh = guard.requireTask(decision.taskId());
            guard.requireMayAct(fresh, actor, "decide");
            tasks.put(decision, fresh);
        }
        validateShape(tasks);

        List<Map.Entry<TaskDecision, TaskView>> ordered = new ArrayList<>(tasks.entrySet());
        ordered.sort(Comparator.comparing((Map.Entry<TaskDecision, TaskView> e) -> e.getKey().outcome() == DecisionOutcome.REJECT));

        List<HookDecision> hookDecisions = new ArrayList<>();
        for (Map.Entry<TaskDecision, TaskView> entry : ordered) {
            hookDecisions.add(hooks.check(entry.getValue(), actor, hookAction(entry.getKey().outcome()), entry.getKey().reason()));
        }
        hookDecisions.stream().flatMap(d -> d.variableUpdates().stream()).forEach(update -> engine.updateVariables(instanceId,
                new ProcessVariables(Map.of(), Map.of(update.name(), update.values()))));

        ApprovalStatus status = ApprovalStatus.RUNNING;
        for (Map.Entry<TaskDecision, TaskView> entry : ordered) {
            status = apply(request, entry.getKey(), entry.getValue(), actor, status);
        }
        return new DecisionResult(instanceId.value(), status.name());
    }

    private void validateShape(Map<TaskDecision, TaskView> tasks) {
        Set<String> nodes = new HashSet<>();
        ApprovalInstanceId instance = null;
        for (Map.Entry<TaskDecision, TaskView> entry : tasks.entrySet()) {
            TaskDecision decision = entry.getKey();
            TaskView task = entry.getValue();
            if (instance == null) {
                instance = task.instanceId();
            } else if (!instance.equals(task.instanceId())) {
                throw new ApprovalOperationException(ApprovalError.PLATFORM_APPROVAL_DECISION_CROSS_INSTANCE,
                        "One submission may only decide tasks of one approval");
            }
            if (!nodes.add(task.nodeKey())) {
                throw new ApprovalOperationException(ApprovalError.PLATFORM_APPROVAL_DECISION_SINGLE_ONLY,
                        "Node " + task.nodeKey() + " takes exactly one decision per submission");
            }
            boolean hookDecidesReason = task.beforeHookKey() != null && !task.beforeHookKey().isBlank();
            if (decision.outcome() == DecisionOutcome.REJECT && isBlank(decision.reason()) && !hookDecidesReason) {
                throw new ApprovalOperationException(ApprovalError.PLATFORM_APPROVAL_REASON_REQUIRED,
                        "A rejection needs a reason");
            }
        }
    }

    private ApprovalStatus apply(ApprovalRequestRecord request, TaskDecision decision, TaskView task, UserId actor,
            ApprovalStatus current) {
        claimIfNeeded(task, actor);
        String reason = decision.outcome() == DecisionOutcome.APPROVE && isBlank(decision.reason())
                ? DEFAULT_APPROVE_REASON : decision.reason();
        audit.record(new AuditEntry("platform.approval.task." + decision.outcome().name().toLowerCase(Locale.ROOT),
                actor, null, "approval-task:" + task.taskId().value(), reason));
        if (decision.outcome() == DecisionOutcome.APPROVE) {
            engine.completeTask(task.taskId());
            lifecycle.publishTaskResult(task, actor, TaskOutcome.APPROVED, reason);
            return lifecycle.finishIfDone(request, ApprovalStatus.RUNNING);
        }
        return task.insideSubProcess()
                ? lifecycle.returned(request, task, actor, reason)
                : lifecycle.reject(request, task, actor, reason);
    }

    private void claimIfNeeded(TaskView task, UserId actor) {
        if (!task.isClaimed()) {
            lifecycle.claim(task, actor);
        }
    }

    private static HookAction hookAction(DecisionOutcome outcome) {
        return outcome == DecisionOutcome.APPROVE ? HookAction.APPROVE : HookAction.REJECT;
    }

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }
}
