package com.forcitis.one.platform.approval;

import com.forcitis.one.fixture.doubles.TestAccounts;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import com.forcitis.one.kernel.KernelError;
import com.forcitis.one.kernel.Reason;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.approval.api.ApprovalField;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.api.ApprovalStatus;
import com.forcitis.one.platform.approval.api.ApprovalTaskId;
import com.forcitis.one.platform.approval.api.HookAction;
import com.forcitis.one.platform.approval.api.HookDecision;
import com.forcitis.one.platform.approval.api.HookOutcome;
import com.forcitis.one.platform.approval.api.ApprovalError;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.platform.approval.application.DecideTasks;
import com.forcitis.one.platform.approval.application.DecisionOutcome;
import com.forcitis.one.platform.approval.application.ReturnToInitiator;
import com.forcitis.one.platform.approval.application.TaskDecision;
import java.util.List;
import org.flowable.engine.HistoryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** The hook is a scripted test double; a test variant of the timesheet process names it on the department node and is deployed as a newer version. */
class BeforeHookIT extends ApprovalIntegrationSupport {

    @org.springframework.beans.factory.annotation.Autowired
    CommitGate commitGate;

    @Autowired
    HistoryService history;

    @Autowired
    ScriptedHook hook;

    @Autowired
    DecideTasks decideTasks;

    @Autowired
    ReturnToInitiator returnToInitiator;

    void deploy(String resource) {
        deployVariant(resource.substring(0, resource.indexOf('/')));
        hook.next = HookDecision.allow();
        hook.onCall = null;
        hook.calls.clear();
    }

    record Staff(long initiator, long manager, long head) {
    }

    Staff staff() {
        long unit = newId();
        Staff s = new Staff(newId(), newId(), newId());
        organisation.unit(unit, null, s.head()).person(s.head(), unit, null).person(s.initiator(), unit, null).person(s.manager(), unit, null);
        return s;
    }

    ApprovalInstanceId atDepartmentNode(Staff s, String key) {
        ApprovalInstanceId instance = start(key, s.initiator(), s.manager());
        decideTasks.execute(TestAccounts.account(s.manager()), List.of(new TaskDecision(openTaskId(instance), DecisionOutcome.APPROVE, "ok")), java.util.OptionalLong.empty());
        assertThat(openTask(instance).getTaskDefinitionKey()).isEqualTo("departmentReview");
        return instance;
    }

    @Test
    @DisplayName("RULE-290 a top-level reject ends the approval REJECTED and keeps the history (RULE-288)")
    void topLevelRejectEndsTheApproval() {
        deploy("hooked/cost_timesheet_approval.bpmn20.xml");
        Staff s = staff();
        String key = newKey("hook");
        ApprovalInstanceId instance = atDepartmentNode(s, key);

        var rejected = decideTasks.execute(TestAccounts.account(s.head()),
                List.of(new TaskDecision(openTaskId(instance), DecisionOutcome.REJECT, "price too low")), java.util.OptionalLong.empty());

        assertThat(rejected.status()).isEqualTo(ApprovalStatus.REJECTED);
        assertThat(requestStatus(key)).isEqualTo("REJECTED");
        assertThat(approvals.isFinished(instance)).isTrue();
        assertThat(history.createHistoricProcessInstanceQuery().processInstanceId(instance.value().toString()).count()).isEqualTo(1);
        eventually(() -> assertThat(events.completed()).anySatisfy(e -> {
            assertThat(e.document().documentId().toString()).isEqualTo(key);
            assertThat(e.outcome()).isEqualTo(com.forcitis.one.platform.approval.api.ApprovalOutcome.REJECTED);
            assertThat(e.reason()).isEqualTo(new Reason("price too low"));
        }));
    }

    @Test
    @DisplayName("RULE-277 the hook runs inside the decide transaction, before the task completes, with a server-built context")
    void hookRunsInsideTheTransactionBeforeCompletion() {
        deploy("hooked/cost_timesheet_approval.bpmn20.xml");
        Staff s = staff();
        String key = newKey("hook");
        ApprovalInstanceId instance = atDepartmentNode(s, key);
        ApprovalTaskId task = openTaskId(instance);
        boolean[] observed = new boolean[2];
        hook.onCall = context -> {
            observed[0] = TransactionSynchronizationManager.isActualTransactionActive();
            observed[1] = taskService.createTaskQuery().taskId(context.taskId().value().toString()).count() == 1;
        };

        decideTasks.execute(TestAccounts.account(s.head()), List.of(new TaskDecision(task, DecisionOutcome.APPROVE, "fine")), java.util.OptionalLong.empty());

        assertThat(observed).containsExactly(true, true);
        assertThat(hook.calls).hasSize(1);
        var context = hook.calls.getFirst();
        assertThat(context.action()).isEqualTo(HookAction.APPROVE);
        assertThat(context.document().documentId().toString()).isEqualTo(key);
        assertThat(context.nodeKey()).isEqualTo("departmentReview");
        assertThat(context.actor()).isEqualTo(TestAccounts.account(s.head()));
        assertThat(context.instanceId()).isEqualTo(instance);
        assertThat(context.processKey()).isEqualTo(com.forcitis.one.cost.timesheet.api.TimesheetApprovalProcess.KEY);
        assertThat(requestStatus(key)).isEqualTo("APPROVED");
    }

    @Test
    @DisplayName("RULE-277 only ALLOW and NOT_APPLICABLE commit: WARN, REJECT and SUPPLEMENT block and change nothing")
    void blockingOutcomesChangeNothing() {
        deploy("hooked/cost_timesheet_approval.bpmn20.xml");
        Staff s = staff();
        String key = newKey("hook");
        ApprovalInstanceId instance = atDepartmentNode(s, key);
        ApprovalTaskId task = openTaskId(instance);
        for (HookOutcome outcome : List.of(HookOutcome.WARN, HookOutcome.REJECT, HookOutcome.SUPPLEMENT)) {
            hook.next = HookDecision.block(outcome, "not yet");
            assertThatThrownBy(() -> decideTasks.execute(TestAccounts.account(s.head()), List.of(new TaskDecision(task, DecisionOutcome.APPROVE, "x")), java.util.OptionalLong.empty()))
                    .isInstanceOfSatisfying(DomainException.class,
                            e -> assertThat(e.errorCode()).isEqualTo(ApprovalError.PLATFORM_APPROVAL_HOOK_BLOCKED));
            assertThat(openTaskId(instance)).isEqualTo(task);
            assertThat(taskService.createTaskQuery().taskId(task.value().toString()).singleResult().getAssignee()).isNull();
            assertThat(requestStatus(key)).isEqualTo("RUNNING");
        }
        hook.next = new HookDecision(HookOutcome.NOT_APPLICABLE, null, List.of());
        decideTasks.execute(TestAccounts.account(s.head()), List.of(new TaskDecision(task, DecisionOutcome.APPROVE, "x")), java.util.OptionalLong.empty());
        assertThat(requestStatus(key)).isEqualTo("APPROVED");
    }

    @Test
    @DisplayName("RULE-280 variable updates of the hook and the task completion are one transaction: if completion fails, neither happens")
    void variableUpdatesAndCompletionAreAtomic() {
        deploy("hooked/cost_timesheet_approval.bpmn20.xml");
        Staff s = staff();
        String key = newKey("hook");
        ApprovalInstanceId instance = atDepartmentNode(s, key);
        ApprovalTaskId task = openTaskId(instance);
        hook.next = new HookDecision(HookOutcome.ALLOW, null, List.of(new ApprovalField("approvedAmount", List.of("100"))));

        commitGate.failing = true;
        try {
            assertThatThrownBy(() -> decideTasks.execute(TestAccounts.account(s.head()), List.of(new TaskDecision(task, DecisionOutcome.APPROVE, "x")), java.util.OptionalLong.empty()))
                    .isInstanceOf(IllegalStateException.class);
        } finally {
            commitGate.failing = false;
        }

        assertThat(runtime.getVariable(instance.value().toString(), "approvedAmount")).isNull();
        assertThat(openTaskId(instance)).isEqualTo(task);
        assertThat(requestStatus(key)).isEqualTo("RUNNING");
    }

    @Test
    @DisplayName("RULE-277 an allowing hook's variable updates are written to the process before the task completes")
    void variableUpdatesAreWritten() {
        deploy("hooked/cost_timesheet_approval.bpmn20.xml");
        Staff s = staff();
        String key = newKey("hook");
        ApprovalInstanceId instance = atDepartmentNode(s, key);
        hook.next = new HookDecision(HookOutcome.ALLOW, null, List.of(new ApprovalField("approvedAmount", List.of("100"))));
        decideTasks.execute(TestAccounts.account(s.head()), List.of(new TaskDecision(openTaskId(instance), DecisionOutcome.APPROVE, "x")), java.util.OptionalLong.empty());
        assertThat(history.createHistoricVariableInstanceQuery().processInstanceId(instance.value().toString()).variableName("approvedAmount")
                .singleResult().getValue()).isEqualTo("100");
    }

    @Test
    @DisplayName("RULE-275 return to initiator passes the node's BEFORE hook too; a blocking hook leaves the task where it was")
    void returnPassesTheHook() {
        deploy("hooked/cost_timesheet_approval.bpmn20.xml");
        Staff s = staff();
        String key = newKey("hook");
        ApprovalInstanceId instance = atDepartmentNode(s, key);
        ApprovalTaskId task = openTaskId(instance);

        hook.next = HookDecision.block(HookOutcome.WARN, "attachments missing in the business document");
        assertThatThrownBy(() -> returnToInitiator.execute(task, TestAccounts.account(s.head()), "cannot approve", java.util.OptionalLong.empty()))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ApprovalError.PLATFORM_APPROVAL_HOOK_BLOCKED));
        assertThat(openTaskId(instance)).isEqualTo(task);
        assertThat(requestStatus(key)).isEqualTo("RUNNING");

        hook.next = HookDecision.allow();
        returnToInitiator.execute(task, TestAccounts.account(s.head()), "cannot approve", java.util.OptionalLong.empty());
        assertThat(hook.calls).extracting(c -> c.action()).containsExactly(HookAction.RETURN, HookAction.RETURN);
        assertThat(requestStatus(key)).isEqualTo("RETURNED");
    }

    @Test
    @DisplayName("RULE-272 a rejection needs a reason even when the node has a hook; the hook is not asked")
    void rejectionWithoutReasonIsRefusedEvenWhenTheNodeHasAHook() {
        deploy("hooked/cost_timesheet_approval.bpmn20.xml");
        Staff s = staff();
        String key = newKey("hook");
        ApprovalInstanceId instance = atDepartmentNode(s, key);
        ApprovalTaskId task = openTaskId(instance);
        int before = hook.calls.size();
        assertThatThrownBy(() -> decideTasks.execute(TestAccounts.account(s.head()), List.of(new TaskDecision(task, DecisionOutcome.REJECT, null)), java.util.OptionalLong.empty()))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.errorCode()).isEqualTo(KernelError.KERNEL_REASON_REQUIRED));
        assertThat(hook.calls).hasSize(before);
        assertThat(requestStatus(key)).isEqualTo("RUNNING");
    }

    @Test
    @DisplayName("RULE-283 a node naming a hook key nobody registered blocks the action (never passes silently)")
    void unregisteredHookBlocks() {
        deploy("hooked-missing/cost_timesheet_approval.bpmn20.xml");
        Staff s = staff();
        String key = newKey("hook");
        ApprovalInstanceId instance = atDepartmentNode(s, key);
        ApprovalTaskId task = openTaskId(instance);
        assertThatThrownBy(() -> decideTasks.execute(TestAccounts.account(s.head()), List.of(new TaskDecision(task, DecisionOutcome.APPROVE, "x")), java.util.OptionalLong.empty()))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ApprovalError.PLATFORM_APPROVAL_HOOK_NOT_REGISTERED));
        assertThat(openTaskId(instance)).isEqualTo(task);
        assertThat(hook.calls).isEmpty();
    }
}
