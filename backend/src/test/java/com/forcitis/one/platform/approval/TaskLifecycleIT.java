package com.forcitis.one.platform.approval;

import com.forcitis.one.cost.timesheet.api.TimesheetApprovalProcess;
import com.forcitis.one.fixture.doubles.TestAccounts;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.KernelError;
import com.forcitis.one.platform.approval.api.RoundNumber;
import com.forcitis.one.kernel.Reason;
import com.forcitis.one.cost.timesheet.api.TimesheetApprovalField;
import com.forcitis.one.cost.timesheet.api.TimesheetRoutingFlag;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.api.ApprovalOutcome;
import com.forcitis.one.platform.approval.api.ApprovalStatus;
import com.forcitis.one.platform.approval.api.ApprovalTaskId;
import com.forcitis.one.platform.approval.api.TaskOutcome;
import com.forcitis.one.platform.approval.api.ApprovalError;
import com.forcitis.one.kernel.DomainException;
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
import com.forcitis.one.kernel.event.DomainEvent;

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
        return decideTasks.execute(TestAccounts.account(actor), List.of(new TaskDecision(task, outcome, reason)), java.util.OptionalLong.empty());
    }

    @Test
    @DisplayName("RULE-249 a node is a candidate group: the resolved people may claim, nobody is picked at random")
    void candidatesClaimInsteadOfRandomPick() {
        deployVariant("groupnode");
        Staff s = staff();
        long second = newId();
        organisation.person(second, 1, null);
        String key = newKey("lifecycle");
        ApprovalInstanceId instance = tx.execute(status -> approvals.start(startRequest(key, TestAccounts.employee(s.initiator()),
                java.util.Set.of(TimesheetRoutingFlag.PROJECT_HOURS.flagName()), List.of(new com.forcitis.one.platform.approval.api.ApprovalField(TimesheetApprovalField.PROJECT_MANAGER.fieldName(),
                        List.of(TestAccounts.employee(s.manager()).toString(), TestAccounts.employee(second).toString()))))));
        Task task = openTask(instance);
        assertThat(task.getTaskDefinitionKey()).isEqualTo("projectManagerReview");
        assertThat(task.getAssignee()).isNull();
        assertThat(taskService.getIdentityLinksForTask(task.getId()).stream().map(l -> l.getUserId()).toList())
                .containsExactlyInAnyOrder(TestAccounts.account(s.manager()).value().toString(), TestAccounts.account(second).value().toString());
    }

    @Test
    @DisplayName("RULE-276 only the claimer may act after a claim; RULE-280 a pass without a reason stays without one; RULE-289 ends APPROVED with a typed event")
    void claimDecideAndFinish() {
        Staff s = staff();
        actAs(s.manager());
        String key = newKey("lifecycle");
        ApprovalInstanceId instance = startWithSupervisor(key, s.initiator(), s.manager());
        ApprovalTaskId review = openTaskId(instance, "projectManagerReview");

        claimTask.execute(review, TestAccounts.account(s.manager()), java.util.OptionalLong.empty());
        assertThat(openTask(instance, "projectManagerReview").getAssignee()).isEqualTo(TestAccounts.account(s.manager()).value().toString());

        DecisionResult afterReview = decide(s.manager(), review, DecisionOutcome.APPROVE, "");
        assertThat(afterReview.status()).isEqualTo(ApprovalStatus.RUNNING);
        assertThat(requestStatus(key)).isEqualTo("RUNNING");
        Task department = openTask(instance, "supervisorReview");
        assertThat(taskService.getIdentityLinksForTask(department.getId()).stream().map(l -> l.getUserId()).toList())
                .containsExactly(TestAccounts.account(s.head()).value().toString());

        DecisionResult done = decide(s.head(), new ApprovalTaskId(java.util.UUID.fromString(department.getId())), DecisionOutcome.APPROVE, "ok");
        assertThat(done.status()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(requestStatus(key)).isEqualTo("APPROVED");
        assertThat(approvals.isFinished(instance)).isTrue();

        eventually(() -> {
            assertThat(events.completed()).anySatisfy(e -> {
                assertThat(e.document().documentId().toString()).isEqualTo(key);
                assertThat(e.outcome()).isEqualTo(ApprovalOutcome.APPROVED);
                assertThat(e.round()).isEqualTo(new RoundNumber(1));
            });
            assertThat(events.resulted().stream().filter(e -> e.document().documentId().toString().equals(key)).map(e -> e.outcome()).toList())
                    .containsExactlyInAnyOrder(TaskOutcome.APPROVED, TaskOutcome.APPROVED);
        });
        assertThat(events.resulted().stream().filter(e -> e.document().documentId().toString().equals(key)
                && e.assignee().equals(TestAccounts.account(s.manager()))).map(e -> e.reason()).toList()).containsOnlyNulls();
    }

    @Test
    @DisplayName("RULE-295 one decision publishes its facts once each, with the request's running event numbers and the deciding person as actor")
    void aDecisionPublishesItsFactsOnce() {
        Staff s = staff();
        actAs(s.manager());
        String key = newKey("facts-once");
        ApprovalInstanceId instance = start(key, s.initiator(), s.manager());
        ApprovalTaskId review = openTaskId(instance);
        claimTask.execute(review, TestAccounts.account(s.manager()), java.util.OptionalLong.empty());

        decide(s.manager(), review, DecisionOutcome.APPROVE, "");

        List<com.forcitis.one.kernel.event.EventEnvelope> mine = events.of(com.forcitis.one.kernel.event.DomainEvent.class).stream()
                .map(DomainEvent::envelope).filter(e -> e.aggregateId().equals(requestIdOf(key))).toList();
        assertThat(mine).extracting(e -> e.eventType()).containsExactly("platform.approval.ApprovalRoundStarted", "platform.approval.TaskAssigned",
                "platform.approval.TaskClaimed", "platform.approval.TaskHandled", "platform.approval.ApprovalCompleted");
        assertThat(mine).extracting(e -> e.aggregateVersion()).containsExactly(1L, 2L, 3L, 4L, 5L);
        assertThat(mine.get(2).actorId()).isEqualTo(TestAccounts.account(s.manager()).value());
        assertThat(mine.get(3).actorId()).isEqualTo(TestAccounts.account(s.manager()).value());
    }

    @Test
    @DisplayName("RULE-271 and M3 a reject inside the sub-process returns the approval: the round ends RETURNED, a resubmission opens round 2 with a new instance")
    void rejectInsideSubProcessEndsTheRound() {
        Staff s = staff();
        String key = newKey("lifecycle");
        ApprovalInstanceId first = start(key, s.initiator(), s.manager());

        DecisionResult returned = decide(s.manager(), openTaskId(first), DecisionOutcome.REJECT, "hours are wrong");
        assertThat(returned.status()).isEqualTo(ApprovalStatus.RETURNED);
        assertThat(requestStatus(key)).isEqualTo("RETURNED");
        assertThat(approvals.isFinished(first)).as("the round is over").isTrue();
        assertThat(taskService.createTaskQuery().processInstanceId(first.value().toString()).count()).isZero();
        assertThat(history.createHistoricProcessInstanceQuery().processInstanceId(first.value().toString()).singleResult().getDeleteReason())
                .isEqualTo("RETURNED");
        eventually(() -> {
            assertThat(events.resulted()).anySatisfy(e -> {
                assertThat(e.document().documentId().toString()).isEqualTo(key);
                assertThat(e.outcome()).isEqualTo(TaskOutcome.RETURNED);
                assertThat(e.reason()).isEqualTo(new Reason("hours are wrong"));
            });
            assertThat(events.completed()).anySatisfy(e -> {
                assertThat(e.document().documentId().toString()).isEqualTo(key);
                assertThat(e.outcome()).isEqualTo(ApprovalOutcome.RETURNED);
                assertThat(e.round()).isEqualTo(new RoundNumber(1));
                assertThat(e.reason()).isEqualTo(new Reason("hours are wrong"));
            });
        });

        ApprovalInstanceId second = start(key, s.initiator(), s.manager());

        assertThat(second).isNotEqualTo(first);
        assertThat(requestStatus(key)).isEqualTo("RUNNING");
        assertThat(openTask(second).getTaskDefinitionKey()).isEqualTo("projectManagerReview");
        assertThat(requests.rounds(TimesheetApprovalProcess.KEY, document(key))).extracting(r -> r.round(), r -> r.instanceId())
                .containsExactly(org.assertj.core.groups.Tuple.tuple(1, first), org.assertj.core.groups.Tuple.tuple(2, second));
        assertThat(history.createHistoricTaskInstanceQuery().processInstanceId(first.value().toString()).count()).isEqualTo(1);
        assertThat(history.createHistoricProcessInstanceQuery().processInstanceId(first.value().toString()).count()).isEqualTo(1);
    }

    @Test
    @DisplayName("RULE-275 return to initiator needs a reason; with a reason the round ends RETURNED and nothing is parked")
    void returnNeedsAReason() {
        Staff s = staff();
        String key = newKey("lifecycle");
        ApprovalInstanceId instance = start(key, s.initiator(), s.manager());
        ApprovalTaskId review = openTaskId(instance);

        assertThatThrownBy(() -> returnToInitiator.execute(review, TestAccounts.account(s.manager()), " ", java.util.OptionalLong.empty()))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(KernelError.KERNEL_REASON_REQUIRED));
        assertThat(openTask(instance).getTaskDefinitionKey()).isEqualTo("projectManagerReview");

        DecisionResult result = returnToInitiator.execute(review, TestAccounts.account(s.manager()), "missing attachment", java.util.OptionalLong.empty());
        assertThat(result.status()).isEqualTo(ApprovalStatus.RETURNED);
        assertThat(taskService.createTaskQuery().processInstanceId(instance.value().toString()).count()).isZero();
        assertThat(approvals.isFinished(instance)).isTrue();
        assertThat(requestStatus(key)).isEqualTo("RETURNED");
        eventually(() -> assertThat(events.completed()).anySatisfy(e -> {
            assertThat(e.document().documentId().toString()).isEqualTo(key);
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
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(KernelError.KERNEL_REASON_REQUIRED));
        assertThatThrownBy(() -> decideTasks.execute(TestAccounts.account(s.manager()), List.of(
                new TaskDecision(t1, DecisionOutcome.APPROVE, "ok"), new TaskDecision(t2, DecisionOutcome.APPROVE, "ok")), java.util.OptionalLong.empty()))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ApprovalError.PLATFORM_APPROVAL_DECISION_CROSS_INSTANCE));
        assertThatThrownBy(() -> decideTasks.execute(TestAccounts.account(s.manager()), List.of(
                new TaskDecision(t1, DecisionOutcome.APPROVE, "ok"), new TaskDecision(t1, DecisionOutcome.APPROVE, "ok")), java.util.OptionalLong.empty()))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ApprovalError.PLATFORM_APPROVAL_DECISION_DUPLICATE_TASK));
        assertThatThrownBy(() -> decideTasks.execute(TestAccounts.account(s.manager()), List.of(), java.util.OptionalLong.empty()))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ApprovalError.PLATFORM_APPROVAL_DECISION_EMPTY));
        assertThat(openTaskId(first)).isEqualTo(t1);
        assertThat(openTaskId(second)).isEqualTo(t2);
    }

    @Test
    @DisplayName("RULE-295/303/304/312 a failing listener leaves the publication incomplete; resubmitting delivers it again (at least once)")
    void resultEventsAreDeliveredAtLeastOnce(@Autowired org.springframework.modulith.events.IncompleteEventPublications incomplete) {
        Staff s = staff();
        String key = newKey("flaky");
        flaky.flakyDocument(key);
        ApprovalInstanceId instance = startWithSupervisor(key, s.initiator(), s.manager());
        decide(s.manager(), openTaskId(instance, "projectManagerReview"), DecisionOutcome.APPROVE, "ok");
        decide(s.head(), openTaskId(instance, "supervisorReview"), DecisionOutcome.APPROVE, "ok");

        eventually(() -> assertThat(flaky.failedOnce()).contains(key));
        assertThat(flaky.delivered()).noneMatch(e -> e.document().documentId().toString().equals(key));
        assertThat(jdbc.queryForObject("select count(*) from platform_integration.event_publication where completion_date is null",
                Integer.class)).isGreaterThanOrEqualTo(1);

        incomplete.resubmitIncompletePublications(p -> true);
        eventually(() -> assertThat(flaky.delivered()).anySatisfy(e -> {
            assertThat(e.document().documentId().toString()).isEqualTo(key);
            assertThat(e.outcome()).isEqualTo(ApprovalOutcome.APPROVED);
        }));
    }
}
