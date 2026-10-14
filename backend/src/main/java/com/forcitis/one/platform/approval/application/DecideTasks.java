package com.forcitis.one.platform.approval.application;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.api.ApprovalTaskId;
import com.forcitis.one.platform.approval.api.HookAction;
import com.forcitis.one.platform.approval.api.HookDecision;
import com.forcitis.one.platform.approval.api.TaskOutcome;
import com.forcitis.one.platform.approval.domain.ApprovalEngine;
import com.forcitis.one.platform.approval.domain.ApprovalRequest;
import com.forcitis.one.platform.approval.api.ApprovalStatus;
import com.forcitis.one.platform.approval.domain.ApprovalTask;
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

/**
 * Order: tasks exist (404), actor may act on every task else whole batch 403, shape rules, every BEFORE hook, then complete
 * (approvals first, rejections last). An approval without a reason stays without one (RULE-280); a rejection of an added
 * co-signer is advisory and does not end the round (RULE-273).
 */
@Service
public class DecideTasks {

    private final TaskGuard guard;
    private final HookGate hooks;
    private final ApprovalEngine engine;
    private final ApprovalLifecycle lifecycle;
    private final Aggregates aggregates;
    private final AuditLog audit;
    private final BusinessTime time;

    DecideTasks(TaskGuard guard, HookGate hooks, ApprovalEngine engine, ApprovalLifecycle lifecycle, Aggregates aggregates,
            AuditLog audit, BusinessTime time) {
        this.guard = guard;
        this.hooks = hooks;
        this.engine = engine;
        this.lifecycle = lifecycle;
        this.aggregates = aggregates;
        this.audit = audit;
        this.time = time;
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
        ApprovalRequest request = lifecycle.lock(instanceId);
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
            status = apply(request, entry.getKey(), entry.getValue(), actor);
        }
        return new DecisionResult(instanceId, status);
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
            boolean hookDecidesReason = task.beforeHookKey().filter(key -> !key.isBlank()).isPresent();
            if (decision.outcome() == DecisionOutcome.REJECT && isBlank(decision.reason()) && !hookDecidesReason) {
                throw new ApprovalOperationException(ApprovalError.PLATFORM_APPROVAL_REASON_REQUIRED,
                        "A rejection needs a reason");
            }
        }
    }

    private ApprovalStatus apply(ApprovalRequest request, TaskDecision decision, TaskView view, UserId actor) {
        ApprovalTask task = aggregates.task(view.taskId());
        if (!view.isClaimed()) {
            lifecycle.claim(task, view, actor);
        }
        String reason = isBlank(decision.reason()) ? null : decision.reason();
        audit.record(new AuditEntry("platform.approval.task." + decision.outcome().name().toLowerCase(Locale.ROOT),
                actor, null, "approval-task:" + view.taskId().value(), reason));
        if (decision.outcome() == DecisionOutcome.APPROVE) {
            engine.completeTask(view.taskId());
            Transitions.run(() -> task.complete(actor, TaskOutcome.APPROVED, false, reason, view.contentDigest(), time.today()));
            aggregates.save(task);
            return lifecycle.finishIfDone(request);
        }
        if (view.advisory()) {
            engine.completeTask(view.taskId());
            Transitions.run(() -> task.complete(actor, TaskOutcome.REJECTED, true, reason, view.contentDigest(), time.today()));
            aggregates.save(task);
            return lifecycle.finishIfDone(request);
        }
        TaskOutcome outcome = view.insideSubProcess() ? TaskOutcome.RETURNED : TaskOutcome.REJECTED;
        Transitions.run(() -> task.complete(actor, outcome, false, reason, view.contentDigest(), time.today()));
        aggregates.save(task);
        return view.insideSubProcess() ? lifecycle.returned(request, reason) : lifecycle.reject(request, reason);
    }

    private static HookAction hookAction(DecisionOutcome outcome) {
        return outcome == DecisionOutcome.APPROVE ? HookAction.APPROVE : HookAction.REJECT;
    }

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }
}
