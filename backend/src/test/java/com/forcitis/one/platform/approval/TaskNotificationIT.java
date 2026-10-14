package com.forcitis.one.platform.approval;

import com.forcitis.one.cost.timesheet.api.TimesheetApprovalField;
import com.forcitis.one.cost.timesheet.api.TimesheetRoutingFlag;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.WecomUserId;
import com.forcitis.one.platform.approval.api.ApprovalField;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.api.ApprovalRequest;
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

/**
 * Q-3 / I-6 / RULE-374: a task assigned to a candidate group sends ONE text card to every candidate (exact WeCom user ids, dedup
 * key approval-task:{taskId}); a claim turns the same card into "handled by X" and reminds nobody else; a candidate without a WeCom id
 * is skipped with a warning, and a task with no reachable candidate sends nothing. WecomGateway is a test double.
 */
class TaskNotificationIT extends ApprovalIntegrationSupport {

    @Autowired
    ClaimTask claimTask;

    @Autowired
    DecideTasks decideTasks;

    ApprovalInstanceId startWithManagers(String key, long initiator, long... managers) {
        return tx.execute(s -> approvals.start(new ApprovalRequest(ProcessKey.COST_TIMESHEET_APPROVAL, key, new UserId(initiator), Set.of(TimesheetRoutingFlag.PROJECT_HOURS.flagName()),
                List.of(new ApprovalField(TimesheetApprovalField.PROJECT_MANAGER.fieldName(), java.util.Arrays.stream(managers).mapToObj(String::valueOf).toList())))));
    }

    static String dedupKey(ApprovalTaskId task) {
        return "approval-task:" + task.value();
    }

    @Test
    @DisplayName("Q-3 one card to every candidate, with the dedup key and a link to the task; no broadcast fields exist on the card")
    void everyCandidateGetsTheCard() {
        long initiator = newId();
        long first = newId();
        long second = newId();
        organisation.person(initiator, 1, null).person(first, 1, null).person(second, 1, null);
        ApprovalInstanceId instance = startWithManagers(newKey("notify"), initiator, first, second);
        ApprovalTaskId task = openTaskId(instance);

        verify(wecom, timeout(10_000).times(1)).send(argThat(card -> card.deduplicationKey().equals(dedupKey(task))));
        var captor = org.mockito.ArgumentCaptor.forClass(WecomCard.class);
        verify(wecom, org.mockito.Mockito.atLeastOnce()).send(captor.capture());
        WecomCard card = captor.getAllValues().stream().filter(c -> c.deduplicationKey().equals(dedupKey(task))).findFirst().orElseThrow();
        assertThat(card.wecomUserIds()).containsExactlyInAnyOrder(new WecomUserId(organisation.wecomOf(first)),
                new WecomUserId(organisation.wecomOf(second)));
        assertThat(card.title()).contains("Project manager review");
        assertThat(card.targetUrl()).contains(task.value());
        assertThat(card.description()).contains(ProcessKey.COST_TIMESHEET_APPROVAL.name());
    }

    @Test
    @DisplayName("Q-3 a claim marks the SAME card handled by the claimer and sends no further reminder")
    void claimMarksTheCardHandled() {
        long initiator = newId();
        long first = newId();
        long second = newId();
        organisation.person(initiator, 1, null).person(first, 1, null).person(second, 1, null);
        ApprovalInstanceId instance = startWithManagers(newKey("notify"), initiator, first, second);
        ApprovalTaskId task = openTaskId(instance);
        verify(wecom, timeout(10_000).times(1)).send(argThat(card -> card.deduplicationKey().equals(dedupKey(task))));

        claimTask.execute(task, new UserId(second));

        verify(wecom, timeout(10_000).times(1)).markHandled(eq(dedupKey(task)), argThat(note -> note.contains("person-" + second)));
        verify(wecom, after(1_000).times(1)).send(argThat(card -> card.deduplicationKey().equals(dedupKey(task))));
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

        decideTasks.execute(new UserId(first), List.of(new TaskDecision(task, DecisionOutcome.APPROVE, "ok")));

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

        verify(wecom, timeout(10_000)).send(argThat(card -> card.deduplicationKey().equals(dedupKey(task))
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
        verify(wecom, after(1_500).never()).send(argThat(card -> card.deduplicationKey().equals(dedupKey(task))));
    }

    @Test
    @DisplayName("a return ends the round: no further task exists and no further card is sent")
    void returnSendsNoFurtherCard() {
        long initiator = newId();
        long manager = newId();
        organisation.person(initiator, 1, null).person(manager, 1, null);
        ApprovalInstanceId instance = startWithManagers(newKey("notify"), initiator, manager);
        ApprovalTaskId task = openTaskId(instance);
        verify(wecom, timeout(10_000).times(1)).send(argThat(card -> card.deduplicationKey().equals(dedupKey(task))));

        decideTasks.execute(new UserId(manager), List.of(new TaskDecision(task, DecisionOutcome.REJECT, "again")));

        assertThat(taskService.createTaskQuery().processInstanceId(instance.value()).count()).isZero();
        verify(wecom, after(1_500).never()).send(argThat(card -> card.wecomUserIds().contains(new WecomUserId(organisation.wecomOf(initiator)))));
    }
}
