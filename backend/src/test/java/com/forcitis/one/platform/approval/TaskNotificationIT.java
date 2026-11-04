package com.forcitis.one.platform.approval;

import com.forcitis.one.fixture.doubles.TestAccounts;
import com.forcitis.one.cost.timesheet.api.TimesheetApprovalField;
import com.forcitis.one.cost.timesheet.api.TimesheetRoutingFlag;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.WecomUserId;
import com.forcitis.one.platform.approval.api.ApprovalField;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.api.ApprovalStartRequest;
import com.forcitis.one.platform.approval.api.ApprovalTaskId;
import com.forcitis.one.platform.approval.api.ProcessKey;
import com.forcitis.one.platform.approval.application.ClaimTask;
import com.forcitis.one.platform.approval.application.DecideTasks;
import com.forcitis.one.platform.approval.application.DecisionOutcome;
import com.forcitis.one.platform.approval.application.TaskDecision;
import com.forcitis.one.platform.integration.api.WecomCard;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class TaskNotificationIT extends ApprovalIntegrationSupport {

    @org.junit.jupiter.api.BeforeEach
    void candidateGroupNodes() {
        deployVariant("groupnode");
    }

    @Autowired
    ClaimTask claimTask;

    @Autowired
    DecideTasks decideTasks;

    ApprovalInstanceId startWithManagers(String key, long initiator, long... managers) {
        return tx.execute(s -> approvals.start(startRequest(key, TestAccounts.employee(initiator), Set.of(TimesheetRoutingFlag.PROJECT_HOURS.flagName()),
                List.of(new ApprovalField(TimesheetApprovalField.PROJECT_MANAGER.fieldName(), java.util.Arrays.stream(managers).mapToObj(m -> TestAccounts.employee(m).toString()).toList())))));
    }

    static com.forcitis.one.platform.integration.api.DeliverySubject dedupKey(ApprovalTaskId task) {
        return com.forcitis.one.platform.integration.api.DeliverySubject.of(com.forcitis.one.platform.integration.api.DeliveryPurpose.TASK_ASSIGNED, task.value());
    }

    // RULE-499
    @Test
    @DisplayName("Q-3 one card to every candidate, with the dedup key and a link to the task; no broadcast fields exist on the card")
    void everyCandidateGetsTheCard() {
        long initiator = newId();
        long first = newId();
        long second = newId();
        organisation.person(initiator, 1, null).person(first, 1, null).person(second, 1, null);
        String key = newKey("notify");
        ApprovalInstanceId instance = startWithManagers(key, initiator, first, second);
        ApprovalTaskId task = openTaskId(instance);

        verify(wecom, timeout(10_000).times(1)).send(argThat(card -> card.subject().equals(dedupKey(task))));
        var captor = org.mockito.ArgumentCaptor.forClass(WecomCard.class);
        verify(wecom, org.mockito.Mockito.atLeastOnce()).send(captor.capture());
        WecomCard card = captor.getAllValues().stream().filter(c -> c.subject().equals(dedupKey(task))).findFirst().orElseThrow();
        assertThat(card.wecomUserIds()).containsExactlyInAnyOrder(new WecomUserId(organisation.wecomOf(first)),
                new WecomUserId(organisation.wecomOf(second)));
        assertThat(card.title()).contains("项目经理审批");
        assertThat(card.targetUrl()).contains(task.value().toString());
        assertThat(card.description()).contains("工时审批").contains("summary of " + key).contains("发起人").contains("发起时间").contains("截止");
        assertThat(card.title() + card.description()).doesNotContain("cost_timesheet_approval").doesNotContain("timesheet:");
    }

    // RULE-499
    @Test
    @DisplayName("Q-3 a claim marks the SAME card handled by the claimer and sends no further reminder")
    void claimMarksTheCardHandled() {
        long initiator = newId();
        long first = newId();
        long second = newId();
        organisation.person(initiator, 1, null).person(first, 1, null).person(second, 1, null);
        ApprovalInstanceId instance = startWithManagers(newKey("notify"), initiator, first, second);
        ApprovalTaskId task = openTaskId(instance);
        verify(wecom, timeout(10_000).times(1)).send(argThat(card -> card.subject().equals(dedupKey(task))));

        claimTask.execute(task, TestAccounts.account(second), java.util.OptionalLong.empty());

        verify(wecom, timeout(10_000).times(1)).markHandled(eq(dedupKey(task)), argThat(note -> note.contains("person-" + second)));
        verify(wecom, after(1_000).times(1)).send(argThat(card -> card.subject().equals(dedupKey(task))));
    }

    @Test
    @DisplayName("Q-3 deciding an unclaimed task claims it implicitly, so the other candidates' cards are marked handled as well")
    void decidingAnUnclaimedTaskMarksTheCardHandled() {
        long initiator = newId();
        long first = newId();
        long second = newId();
        organisation.person(initiator, 1, null).person(first, 1, null).person(second, 1, null);
        ApprovalInstanceId instance = startWithManagers(newKey("notify"), initiator, first, second);
        ApprovalTaskId task = openTaskId(instance);

        decideTasks.execute(TestAccounts.account(first), List.of(new TaskDecision(task, DecisionOutcome.APPROVE, "ok")), java.util.OptionalLong.empty());

        verify(wecom, timeout(10_000).times(1)).markHandled(eq(dedupKey(task)), argThat(note -> note.contains("person-" + first)));
    }

    @Test
    @DisplayName("I-6/RULE-374 a candidate without a WeCom id is skipped; the others still get the card, only by exact id")
    void candidateWithoutWecomIdIsSkipped() {
        long initiator = newId();
        long reachable = newId();
        long unreachable = newId();
        organisation.person(initiator, 1, null).person(reachable, 1, null).personWithoutWecom(unreachable, 1, null);
        ApprovalInstanceId instance = startWithManagers(newKey("notify"), initiator, reachable, unreachable);
        ApprovalTaskId task = openTaskId(instance);

        verify(wecom, timeout(10_000)).send(argThat(card -> card.subject().equals(dedupKey(task))
                && card.wecomUserIds().equals(List.of(new WecomUserId(organisation.wecomOf(reachable))))));
    }

    @Test
    @DisplayName("I-6 a task whose candidates all lack a WeCom id sends nothing at all")
    void nobodyReachableSendsNothing() {
        long initiator = newId();
        long unreachable = newId();
        organisation.person(initiator, 1, null).personWithoutWecom(unreachable, 1, null);
        ApprovalInstanceId instance = startWithManagers(newKey("notify"), initiator, unreachable);
        ApprovalTaskId task = openTaskId(instance);

        eventually(() -> assertThat(events.assigned()).anyMatch(e -> e.taskId().equals(task)));
        verify(wecom, after(1_500).never()).send(argThat(card -> card.subject().equals(dedupKey(task))));
    }

    @Test
    @DisplayName("D-1a-135 a return ends the round: no further task exists, no further task card goes out, and the initiator gets the completion card")
    void returnSendsTheCompletionCardOnly() {
        long initiator = newId();
        long manager = newId();
        organisation.person(initiator, 1, null).person(manager, 1, null);
        ApprovalInstanceId instance = startWithManagers(newKey("notify"), initiator, manager);
        ApprovalTaskId task = openTaskId(instance);
        verify(wecom, timeout(10_000).times(1)).send(argThat(card -> card.subject().equals(dedupKey(task))));

        decideTasks.execute(TestAccounts.account(manager), List.of(new TaskDecision(task, DecisionOutcome.REJECT, "again")), java.util.OptionalLong.empty());

        assertThat(taskService.createTaskQuery().processInstanceId(instance.value().toString()).count()).isZero();
        verify(wecom, timeout(10_000).times(1)).send(argThat(card -> card.subject().equals(com.forcitis.one.platform.integration.api.DeliverySubject.of(com.forcitis.one.platform.integration.api.DeliveryPurpose.APPROVAL_COMPLETED, instance.value()))
                && card.wecomUserIds().equals(List.of(new WecomUserId(organisation.wecomOf(initiator))))
                && card.title().contains("已退回") && card.description().contains("again")));
        verify(wecom, after(1_000).times(1)).send(argThat(card -> card.subject().id().equals(task.value())));
    }
}
