package com.forcitis.one.platform.approval;

import com.forcitis.one.cost.timesheet.api.TimesheetApprovalField;
import com.forcitis.one.cost.timesheet.api.TimesheetRoutingFlag;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.api.ApprovalOutcome;
import com.forcitis.one.platform.approval.api.ApprovalTaskId;
import com.forcitis.one.platform.approval.api.ProcessKey;
import com.forcitis.one.platform.approval.api.TaskOutcome;
import com.forcitis.one.platform.approval.application.ApprovalError;
import com.forcitis.one.platform.approval.application.ApprovalOperationException;
import com.forcitis.one.platform.approval.application.ClaimTask;
import com.forcitis.one.platform.approval.application.DecideTasks;
import com.forcitis.one.platform.approval.application.DecisionOutcome;
import com.forcitis.one.platform.approval.application.DecisionResult;
import com.forcitis.one.platform.approval.application.ReturnToInitiator;
import com.forcitis.one.platform.approval.application.TaskDecision;
import com.forcitis.one.platform.approval.domain.ApprovalRequestRepository;
import java.util.List;
import org.flowable.engine.HistoryService;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** The 1a task lifecycle on the real engine and database: candidates, claim, decide, return, reject, resubmit, results. */
class TaskLifecycleIT extends ApprovalIntegrationSupport {

    @Autowired
    ClaimTask claimTask;

    @Autowired
    DecideTasks decideTasks;

    @Autowired
    ReturnToInitiator returnToInitiator;

    @Autowired
    HistoryService history;

    @Autowired
    ApprovalRequestRepository requests;

    record Staff(long initiator, long manager, long head) {
    }

    Staff staff() {
        long unit = newId();
        long head = newId();
        long initiator = newId();
        long manager = newId();
        organisation.unit(unit, null, head).person(head, unit, null).person(initiator, unit, head).person(manager, unit, null);
        return new Staff(initiator, manager, head);
    }

    DecisionResult decide(long actor, ApprovalTaskId task, DecisionOutcome outcome, String reason) {
        return decideTasks.execute(new UserId(actor), List.of(new TaskDecision(task, outcome, reason)));
    }

    @Test
    @DisplayName("RULE-249 a node is a candidate group: the resolved people may claim, nobody is picked at random")
    void candidatesClaimInsteadOfRandomPick() {
        Staff s = staff();
        long second = newId();
        organisation.person(second, 1, null);
        String key = newKey("lifecycle");
        ApprovalInstanceId instance = tx.execute(status -> approvals.start(new com.forcitis.one.platform.approval.api.ApprovalRequest(
                com.forcitis.one.platform.approval.api.ProcessKey.COST_TIMESHEET_APPROVAL, key, new UserId(s.initiator()),
                java.util.Set.of(TimesheetRoutingFlag.PROJECT_HOURS.flagName()), List.of(new com.forcitis.one.platform.approval.api.ApprovalField(TimesheetApprovalField.PROJECT_MANAGER.fieldName(),
                        List.of(String.valueOf(s.manager()), String.valueOf(second)))))));
        Task task = openTask(instance);
        assertThat(task.getTaskDefinitionKey()).isEqualTo("projectManagerReview");
        assertThat(task.getAssignee()).isNull();
        assertThat(taskService.getIdentityLinksForTask(task.getId()).stream().map(l -> l.getUserId()).toList())
                .containsExactlyInAnyOrder(String.valueOf(s.manager()), String.valueOf(second));
    }

    @Test
    @DisplayName("RULE-276 only the claimer may act after a claim; RULE-280 pass defaults the reason; RULE-289 ends APPROVED with a typed event")
    void claimDecideAndFinish() {
        Staff s = staff();
        String key = newKey("lifecycle");
        ApprovalInstanceId instance = startWithSupervisor(key, s.initiator(), s.manager());
        ApprovalTaskId review = openTaskId(instance, "projectManagerReview");

        claimTask.execute(review, new UserId(s.manager()));
        assertThat(openTask(instance, "projectManagerReview").getAssignee()).isEqualTo(String.valueOf(s.manager()));

        DecisionResult afterReview = decide(s.manager(), review, DecisionOutcome.APPROVE, "");
        assertThat(afterReview.status()).isEqualTo("RUNNING");
        assertThat(requestStatus(key)).isEqualTo("RUNNING");
        Task department = openTask(instance, "supervisorReview");
        assertThat(taskService.getIdentityLinksForTask(department.getId()).stream().map(l -> l.getUserId()).toList())
                .containsExactly(String.valueOf(s.head()));

        DecisionResult done = decide(s.head(), new ApprovalTaskId(department.getId()), DecisionOutcome.APPROVE, "ok");
        assertThat(done.status()).isEqualTo("APPROVED");
        assertThat(requestStatus(key)).isEqualTo("APPROVED");
        assertThat(approvals.isFinished(instance)).isTrue();

        eventually(() -> {
            assertThat(events.completed()).anySatisfy(e -> {
                assertThat(e.businessKey()).isEqualTo(key);
                assertThat(e.outcome()).isEqualTo(ApprovalOutcome.APPROVED);
                assertThat(e.round()).isEqualTo(1);
            });
            assertThat(events.resulted().stream().filter(e -> e.businessKey().equals(key)).map(e -> e.outcome()).toList())
                    .containsExactlyInAnyOrder(TaskOutcome.APPROVED, TaskOutcome.APPROVED);
        });
        org.mockito.Mockito.verify(audit, org.mockito.Mockito.atLeastOnce()).record(org.mockito.ArgumentMatchers.argThat(
                entry -> entry.action().equals("platform.approval.task.approve") && "同意".equals(entry.detail())));
    }

    @Test
    @DisplayName("RULE-271 and M3 a reject inside the sub-process returns the approval: the round ends RETURNED, a resubmission opens round 2 with a new instance")
    void rejectInsideSubProcessEndsTheRound() {
        Staff s = staff();
        String key = newKey("lifecycle");
        ApprovalInstanceId first = start(key, s.initiator(), s.manager());

        DecisionResult returned = decide(s.manager(), openTaskId(first), DecisionOutcome.REJECT, "hours are wrong");
        assertThat(returned.status()).isEqualTo("RETURNED");
        assertThat(requestStatus(key)).isEqualTo("RETURNED");
        assertThat(approvals.isFinished(first)).as("the round is over").isTrue();
        assertThat(taskService.createTaskQuery().processInstanceId(first.value()).count()).isZero();
        assertThat(history.createHistoricProcessInstanceQuery().processInstanceId(first.value()).singleResult().getDeleteReason())
                .isEqualTo("RETURNED");
        eventually(() -> {
            assertThat(events.resulted()).anySatisfy(e -> {
                assertThat(e.businessKey()).isEqualTo(key);
                assertThat(e.outcome()).isEqualTo(TaskOutcome.RETURNED);
                assertThat(e.reason()).isEqualTo("hours are wrong");
            });
            assertThat(events.completed()).anySatisfy(e -> {
                assertThat(e.businessKey()).isEqualTo(key);
                assertThat(e.outcome()).isEqualTo(ApprovalOutcome.RETURNED);
                assertThat(e.round()).isEqualTo(1);
                assertThat(e.reason()).isEqualTo("hours are wrong");
            });
        });

        ApprovalInstanceId second = start(key, s.initiator(), s.manager());

        assertThat(second).isNotEqualTo(first);
        assertThat(requestStatus(key)).isEqualTo("RUNNING");
        assertThat(openTask(second).getTaskDefinitionKey()).isEqualTo("projectManagerReview");
        assertThat(requests.rounds(ProcessKey.COST_TIMESHEET_APPROVAL, key)).extracting(r -> r.round(), r -> r.instanceId())
                .containsExactly(org.assertj.core.groups.Tuple.tuple(1, first), org.assertj.core.groups.Tuple.tuple(2, second));
        // the history of round 1 is still there, round by round
        assertThat(history.createHistoricTaskInstanceQuery().processInstanceId(first.value()).count()).isEqualTo(1);
        assertThat(history.createHistoricProcessInstanceQuery().processInstanceId(first.value()).count()).isEqualTo(1);
    }

    @Test
    @DisplayName("RULE-275 return to initiator needs a reason; with a reason the round ends RETURNED and nothing is parked")
    void returnNeedsAReason() {
        Staff s = staff();
        String key = newKey("lifecycle");
        ApprovalInstanceId instance = start(key, s.initiator(), s.manager());
        ApprovalTaskId review = openTaskId(instance);

        assertThatThrownBy(() -> returnToInitiator.execute(review, new UserId(s.manager()), " "))
                .isInstanceOfSatisfying(ApprovalOperationException.class,
                        e -> assertThat(e.error()).isEqualTo(ApprovalError.PLATFORM_APPROVAL_REASON_REQUIRED));
        assertThat(openTask(instance).getTaskDefinitionKey()).isEqualTo("projectManagerReview");

        DecisionResult result = returnToInitiator.execute(review, new UserId(s.manager()), "missing attachment");
        assertThat(result.status()).isEqualTo("RETURNED");
        assertThat(taskService.createTaskQuery().processInstanceId(instance.value()).count()).isZero();
        assertThat(approvals.isFinished(instance)).isTrue();
        assertThat(requestStatus(key)).isEqualTo("RETURNED");
        eventually(() -> assertThat(events.completed()).anySatisfy(e -> {
            assertThat(e.businessKey()).isEqualTo(key);
            assertThat(e.outcome()).isEqualTo(ApprovalOutcome.RETURNED);
        }));
    }

    @Test
    @DisplayName("RULE-272 a rejection needs a reason; tasks of two instances cannot be decided together; nothing changes on refusal")
    void decideValidation() {
        Staff s = staff();
        ApprovalInstanceId first = start(newKey("lifecycle"), s.initiator(), s.manager());
        ApprovalInstanceId second = start(newKey("lifecycle"), s.initiator(), s.manager());
        ApprovalTaskId t1 = openTaskId(first);
        ApprovalTaskId t2 = openTaskId(second);

        assertThatThrownBy(() -> decide(s.manager(), t1, DecisionOutcome.REJECT, " "))
                .isInstanceOfSatisfying(ApprovalOperationException.class,
                        e -> assertThat(e.error()).isEqualTo(ApprovalError.PLATFORM_APPROVAL_REASON_REQUIRED));
        assertThatThrownBy(() -> decideTasks.execute(new UserId(s.manager()), List.of(
                new TaskDecision(t1, DecisionOutcome.APPROVE, "ok"), new TaskDecision(t2, DecisionOutcome.APPROVE, "ok"))))
                .isInstanceOfSatisfying(ApprovalOperationException.class,
                        e -> assertThat(e.error()).isEqualTo(ApprovalError.PLATFORM_APPROVAL_DECISION_CROSS_INSTANCE));
        assertThatThrownBy(() -> decideTasks.execute(new UserId(s.manager()), List.of(
                new TaskDecision(t1, DecisionOutcome.APPROVE, "ok"), new TaskDecision(t1, DecisionOutcome.APPROVE, "ok"))))
                .isInstanceOfSatisfying(ApprovalOperationException.class,
                        e -> assertThat(e.error()).isEqualTo(ApprovalError.PLATFORM_APPROVAL_DECISION_DUPLICATE_TASK));
        assertThatThrownBy(() -> decideTasks.execute(new UserId(s.manager()), List.of()))
                .isInstanceOfSatisfying(ApprovalOperationException.class,
                        e -> assertThat(e.error()).isEqualTo(ApprovalError.PLATFORM_APPROVAL_DECISION_EMPTY));
        assertThat(openTaskId(first)).isEqualTo(t1);
        assertThat(openTaskId(second)).isEqualTo(t2);
    }

    @Test
    @DisplayName("RULE-295/303/304/312 a failing listener leaves the publication incomplete; resubmitting delivers it again (at least once)")
    void resultEventsAreDeliveredAtLeastOnce(@Autowired org.springframework.modulith.events.IncompleteEventPublications incomplete) {
        Staff s = staff();
        String key = "flaky-" + newId();
        ApprovalInstanceId instance = startWithSupervisor(key, s.initiator(), s.manager());
        decide(s.manager(), openTaskId(instance, "projectManagerReview"), DecisionOutcome.APPROVE, "ok");
        decide(s.head(), openTaskId(instance, "supervisorReview"), DecisionOutcome.APPROVE, "ok");

        eventually(() -> assertThat(events.failedOnce()).contains(key));
        assertThat(events.completed()).noneMatch(e -> e.businessKey().equals(key));
        assertThat(jdbc.queryForObject("select count(*) from platform_integration.event_publication where completion_date is null",
                Integer.class)).isGreaterThanOrEqualTo(1);

        incomplete.resubmitIncompletePublications(p -> true);
        eventually(() -> assertThat(events.completed()).anySatisfy(e -> {
            assertThat(e.businessKey()).isEqualTo(key);
            assertThat(e.outcome()).isEqualTo(ApprovalOutcome.APPROVED);
        }));
    }
}
