package com.forcitis.one.platform.approval.application;

import com.forcitis.one.kernel.KernelError;
import com.forcitis.one.kernel.Reason;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.approval.api.ApprovalError;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.api.ApprovalTaskId;
import com.forcitis.one.platform.approval.api.HookAction;
import com.forcitis.one.platform.approval.api.HookDecision;
import com.forcitis.one.platform.approval.api.TaskOutcome;
import com.forcitis.one.platform.approval.domain.ApprovalEngine;
import com.forcitis.one.platform.approval.domain.ApprovalRequest;
import com.forcitis.one.platform.approval.domain.ProcessVariables;
import com.forcitis.one.platform.approval.domain.TaskView;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
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
    private final Approvals approvals;
    private final TaskRegistrar registrar;
    private final BusinessTime time;

    DecideTasks(TaskGuard guard, HookGate hooks, ApprovalEngine engine, Approvals approvals, TaskRegistrar registrar, BusinessTime time) {
        this.guard = guard;
        this.hooks = hooks;
        this.engine = engine;
        this.approvals = approvals;
        this.registrar = registrar;
        this.time = time;
    }

    @Transactional
    public DecisionResult execute(UserId actor, List<TaskDecision> decisions, OptionalLong expectedVersion) {
        if (decisions == null || decisions.isEmpty()) {
            throw ApprovalError.PLATFORM_APPROVAL_DECISION_EMPTY.refusal("No decision given");
        }
        Set<ApprovalTaskId> seen = new HashSet<>();
        for (TaskDecision decision : decisions) {
            if (!seen.add(decision.taskId())) {
                throw ApprovalError.PLATFORM_APPROVAL_DECISION_DUPLICATE_TASK.refusal("Task " + decision.taskId().value() + " appears twice");
            }
            guard.requireMayAct(guard.requireTask(decision.taskId()), actor, "decide");
        }
        ApprovalInstanceId instanceId = guard.requireTask(decisions.getFirst().taskId()).instanceId();
        ApprovalRequest request = approvals.lock(instanceId);
        approvals.requireVersion(request, expectedVersion);
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

        try (TaskRegistrar.Scope scope = registrar.open(request)) {
            for (Map.Entry<TaskDecision, TaskView> entry : ordered) {
                apply(request, entry.getKey(), entry.getValue(), actor);
            }
        }
        approvals.save(request);
        return new DecisionResult(instanceId, request.status());
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
                throw ApprovalError.PLATFORM_APPROVAL_DECISION_CROSS_INSTANCE.refusal("One submission may only decide tasks of one approval");
            }
            if (!nodes.add(task.nodeKey())) {
                throw ApprovalError.PLATFORM_APPROVAL_DECISION_SINGLE_ONLY.refusal("Node " + task.nodeKey() + " takes exactly one decision per submission");
            }
            if (decision.outcome() == DecisionOutcome.REJECT && isBlank(decision.reason())) {
                throw KernelError.KERNEL_REASON_REQUIRED.refusal("A rejection needs a reason");
            }
        }
    }

    private void apply(ApprovalRequest request, TaskDecision decision, TaskView view, UserId actor) {
        if (!view.isClaimed()) {
            approvals.requireOneTaskOfNode(request, view, actor);
            approvals.claim(request, view, actor);
        }
        Reason reason = Reason.ofNullable(decision.reason());
        if (decision.outcome() == DecisionOutcome.APPROVE) {
            request.completeTask(view.taskId(), actor, TaskOutcome.APPROVED, false, reason, view.contentHash(), time.moment());
            engine.completeTask(view.taskId());
            approvals.finishIfDone(request);
            return;
        }
        if (view.advisory()) {
            request.completeTask(view.taskId(), actor, TaskOutcome.REJECTED, true, reason, view.contentHash(), time.moment());
            engine.completeTask(view.taskId());
            approvals.finishIfDone(request);
            return;
        }
        TaskOutcome outcome = view.insideSubProcess() ? TaskOutcome.RETURNED : TaskOutcome.REJECTED;
        request.completeTask(view.taskId(), actor, outcome, false, reason, view.contentHash(), time.moment());
        if (view.insideSubProcess()) {
            approvals.returned(request, reason);
        } else {
            approvals.reject(request, reason);
        }
    }

    private static HookAction hookAction(DecisionOutcome outcome) {
        return outcome == DecisionOutcome.APPROVE ? HookAction.APPROVE : HookAction.REJECT;
    }

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }
}
