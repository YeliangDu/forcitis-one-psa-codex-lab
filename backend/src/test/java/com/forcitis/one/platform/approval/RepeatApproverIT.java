package com.forcitis.one.platform.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

import com.forcitis.one.cost.timesheet.api.TimesheetApprovalField;
import com.forcitis.one.cost.timesheet.api.TimesheetRoutingFlag;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.approval.api.ApprovalField;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.api.ApprovalOutcome;
import com.forcitis.one.platform.approval.api.ApprovalStatus;
import com.forcitis.one.platform.approval.api.HookDecision;
import com.forcitis.one.platform.approval.api.HookOutcome;
import com.forcitis.one.platform.approval.api.TaskOutcome;
import com.forcitis.one.platform.approval.application.DecideTasks;
import com.forcitis.one.platform.approval.application.DecisionOutcome;
import com.forcitis.one.platform.approval.application.TaskDecision;
import java.util.List;
import java.util.Set;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** RULE-302: the same approver on the same node of the same instance is passed by the system, unless the content changed since. */
class RepeatApproverIT extends ApprovalIntegrationSupport {

    @Autowired
    DecideTasks decideTasks;

    @Autowired
    ScriptedHook hook;

    @Autowired
    org.flowable.engine.HistoryService history;

    ApprovalInstanceId startRepeating(String key, long initiator, long manager) {
        return tx.execute(status -> approvals.start(startRequest(key, new UserId(initiator), Set.of("again"),
                List.of(new ApprovalField(TimesheetApprovalField.PROJECT_MANAGER.fieldName(), List.of(String.valueOf(manager)))))));
    }

    @Test
    @DisplayName("RULE-302 the same approver coming back to the same node with unchanged content is auto-approved as a system action")
    void unchangedContentPassesAutomatically() {
        deployVariant("repeat");
        hook.next = HookDecision.allow();
        long initiator = newId();
        long manager = newId();
        organisation.person(initiator, 1, null).person(manager, 1, null);
        String key = newKey("repeat");
        ApprovalInstanceId instance = startRepeating(key, initiator, manager);

        decideTasks.execute(new UserId(manager), List.of(new TaskDecision(openTaskId(instance), DecisionOutcome.APPROVE, null)));

        assertThat(approvals.isFinished(instance)).isTrue();
        assertThat(requestStatus(key)).isEqualTo("APPROVED");
        List<HistoricTaskInstance> visits = history.createHistoricTaskInstanceQuery().processInstanceId(instance.value())
                .taskDefinitionKey("repeatReview").orderByHistoricTaskInstanceStartTime().asc().list();
        assertThat(visits).hasSize(2);
        assertThat(visits.get(0).getAssignee()).isEqualTo(String.valueOf(manager));
        assertThat(visits.get(1).getAssignee()).as("nobody claimed the repeat: the system completed it").isNull();
        assertThat(jdbc.queryForList("select status from platform_approval.approval_tasks where instance_id = ? order by created_at", String.class,
                instance.value())).containsExactly("COMPLETED", "COMPLETED");
        verify(audit, atLeastOnce()).record(argThat(entry -> entry.action().equals("platform.approval.task.auto-approve")
                && entry.actorId() == null && entry.subjectKey().startsWith("approval-task:")));
        eventually(() -> {
            assertThat(events.assigned().stream().filter(e -> e.document().documentId().value().equals(key)).count())
                    .as("no card for a task the system passes").isEqualTo(1);
            assertThat(events.resulted().stream().filter(e -> e.document().documentId().value().equals(key)).map(e -> e.outcome()).toList())
                    .as("no result for an automatic pass").containsExactly(TaskOutcome.APPROVED);
            assertThat(events.completed().stream().filter(e -> e.document().documentId().value().equals(key)).map(e -> e.outcome()).toList())
                    .containsExactly(ApprovalOutcome.APPROVED);
        });
    }

    @Test
    @DisplayName("RULE-302 content that changed after the approval must be approved again by a person")
    void changedContentNeedsAFreshApproval() {
        deployVariant("repeat");
        long initiator = newId();
        long manager = newId();
        organisation.person(initiator, 1, null).person(manager, 1, null);
        String key = newKey("repeat-changed");
        ApprovalInstanceId instance = startRepeating(key, initiator, manager);
        hook.next = new HookDecision(HookOutcome.ALLOW, null, List.of(new ApprovalField("contentDigest", List.of("digest-after-edit"))));
        hook.calls.clear();

        ApprovalStatus afterFirst = decideTasks.execute(new UserId(manager),
                List.of(new TaskDecision(openTaskId(instance), DecisionOutcome.APPROVE, null))).status();

        assertThat(afterFirst).isEqualTo(ApprovalStatus.RUNNING);
        assertThat(approvals.isFinished(instance)).isFalse();
        assertThat(openTask(instance).getAssignee()).isNull();

        hook.next = HookDecision.allow();
        assertThat(decideTasks.execute(new UserId(manager), List.of(new TaskDecision(openTaskId(instance), DecisionOutcome.APPROVE, "again")))
                .status()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(history.createHistoricTaskInstanceQuery().processInstanceId(instance.value()).taskDefinitionKey("repeatReview").list())
                .extracting(HistoricTaskInstance::getAssignee).containsOnly(String.valueOf(manager));
    }

    @Test
    @DisplayName("RULE-302 approvals on other nodes never count: the same person on two nodes approves both by hand")
    void otherNodesNeverCount() {
        long unit = newId();
        long person = newId();
        long initiator = newId();
        organisation.unit(unit, null, person).person(person, unit, null).person(initiator, unit, person);
        String key = newKey("two-nodes");
        ApprovalInstanceId instance = tx.execute(status -> approvals.start(startRequest(key, new UserId(initiator),
                Set.of(TimesheetRoutingFlag.PROJECT_HOURS.flagName(), TimesheetRoutingFlag.OVERHEAD_HOURS.flagName()),
                List.of(new ApprovalField(TimesheetApprovalField.PROJECT_MANAGER.fieldName(), List.of(String.valueOf(person)))))));

        assertThat(taskService.createTaskQuery().processInstanceId(instance.value()).count()).isEqualTo(2);
        decideTasks.execute(new UserId(person), List.of(new TaskDecision(openTaskId(instance, "projectManagerReview"), DecisionOutcome.APPROVE, null)));
        assertThat(taskService.createTaskQuery().processInstanceId(instance.value()).taskDefinitionKey("supervisorReview").count())
                .as("still waiting for the person's own decision on the other node").isEqualTo(1);
        assertThat(approvals.isFinished(instance)).isFalse();
    }
}
