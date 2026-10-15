package com.forcitis.one.platform.approval;

import com.forcitis.one.fixture.doubles.TestAccounts;
import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.Reason;
import com.forcitis.one.cost.timesheet.api.TimesheetApprovalField;
import com.forcitis.one.cost.timesheet.api.TimesheetRoutingFlag;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.approval.api.ApprovalField;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.api.ApprovalOutcome;
import com.forcitis.one.platform.approval.api.ApprovalStatus;
import com.forcitis.one.platform.approval.api.ApprovalTaskId;
import com.forcitis.one.platform.approval.api.TaskEffect;
import com.forcitis.one.platform.approval.api.TaskOutcome;
import com.forcitis.one.platform.approval.application.DecideTasks;
import com.forcitis.one.platform.approval.application.DecisionOutcome;
import com.forcitis.one.platform.approval.application.DecisionResult;
import com.forcitis.one.platform.approval.application.TaskDecision;
import java.util.List;
import java.util.Set;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** TIMESHEET route nodes are countersign nodes (production v15): one task per distinct approver, all must approve. */
class CountersignIT extends ApprovalIntegrationSupport {

    @Autowired
    DecideTasks decideTasks;

    record Staff(long initiator, long first, long second, long third, long supervisor) {
    }

    Staff staff() {
        long unit = newId();
        Staff s = new Staff(newId(), newId(), newId(), newId(), newId());
        organisation.unit(unit, null, s.supervisor()).person(s.supervisor(), unit, null).person(s.initiator(), unit, s.supervisor())
                .person(s.first(), unit, null).person(s.second(), unit, null).person(s.third(), unit, null);
        return s;
    }

    ApprovalInstanceId start(String key, Staff s, long... managers) {
        return tx.execute(status -> approvals.start(startRequest(key, new EmployeeId(s.initiator()),
                Set.of(TimesheetRoutingFlag.PROJECT_HOURS.flagName()), List.of(new ApprovalField(
                        TimesheetApprovalField.PROJECT_MANAGER.fieldName(), java.util.Arrays.stream(managers).mapToObj(String::valueOf).toList())))));
    }

    List<Task> openTasks(ApprovalInstanceId instance) {
        return taskService.createTaskQuery().processInstanceId(instance.value()).includeIdentityLinks().list();
    }

    String candidateOf(Task task) {
        return taskService.getIdentityLinksForTask(task.getId()).stream().filter(l -> "candidate".equals(l.getType()))
                .map(l -> l.getUserId()).findFirst().orElseThrow();
    }

    DecisionResult decide(long actor, Task task, DecisionOutcome outcome, String reason) {
        return decideTasks.execute(TestAccounts.account(actor), List.of(new TaskDecision(new ApprovalTaskId(task.getId()), outcome, reason)), java.util.OptionalLong.empty());
    }

    @Test
    @DisplayName("RULE-248 a countersign node creates one task per distinct approver, each with exactly that approver as candidate")
    void oneTaskPerDistinctApprover() {
        Staff s = staff();
        ApprovalInstanceId instance = start(newKey("countersign"), s, s.first(), s.second(), s.first(), s.third());

        List<Task> tasks = openTasks(instance);
        assertThat(tasks).hasSize(3);
        assertThat(tasks).extracting(Task::getTaskDefinitionKey).containsOnly("projectManagerReview");
        assertThat(tasks.stream().map(this::candidateOf).toList()).containsExactlyInAnyOrder(TestAccounts.account(s.first()).value().toString(),
                TestAccounts.account(s.second()).value().toString(), TestAccounts.account(s.third()).value().toString());
        assertThat(jdbc.queryForObject("select count(*) from platform_approval.approval_tasks where instance_id = ? and status = 'PENDING'",
                Integer.class, instance.value())).isEqualTo(3);
    }

    @Test
    @DisplayName("RULE-248 the node is passed only when every approver has approved; one approval is not enough")
    void everyApproverMustApprove() {
        Staff s = staff();
        String key = newKey("countersign-all");
        ApprovalInstanceId instance = start(key, s, s.first(), s.second());

        Task firstTask = openTasks(instance).stream().filter(t -> candidateOf(t).equals(TestAccounts.account(s.first()).value().toString())).findFirst().orElseThrow();
        assertThat(decide(s.first(), firstTask, DecisionOutcome.APPROVE, null).status()).isEqualTo(ApprovalStatus.RUNNING);
        assertThat(openTasks(instance)).hasSize(1);
        assertThat(requestStatus(key)).isEqualTo("RUNNING");
        assertThat(approvals.isFinished(instance)).isFalse();

        Task secondTask = openTasks(instance).getFirst();
        assertThat(candidateOf(secondTask)).isEqualTo(TestAccounts.account(s.second()).value().toString());
        assertThat(decide(s.second(), secondTask, DecisionOutcome.APPROVE, "ok").status()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(requestStatus(key)).isEqualTo("APPROVED");
        eventually(() -> assertThat(events.completed()).anySatisfy(e -> {
            assertThat(e.document().documentId().value()).isEqualTo(key);
            assertThat(e.outcome()).isEqualTo(ApprovalOutcome.APPROVED);
        }));
    }

    @Test
    @DisplayName("RULE-248 a person can act only on their own task of the node")
    void anApproverCannotActOnAnothersTask() {
        Staff s = staff();
        ApprovalInstanceId instance = start(newKey("countersign-own"), s, s.first(), s.second());
        Task secondsTask = openTasks(instance).stream().filter(t -> candidateOf(t).equals(TestAccounts.account(s.second()).value().toString())).findFirst().orElseThrow();

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> decide(s.first(), secondsTask, DecisionOutcome.APPROVE, null))
                .isInstanceOfSatisfying(com.forcitis.one.kernel.DomainException.class, e -> org.assertj.core.api.Assertions.assertThat(e.errorCode()).isEqualTo(com.forcitis.one.platform.approval.api.ApprovalError.PLATFORM_APPROVAL_TASK_NOT_FOUND));
        assertThat(openTasks(instance)).hasSize(2);
    }

    @Test
    @DisplayName("RULE-273 one rejection ends the round: the other open tasks are closed and nothing is left to approve")
    void oneRejectionEndsTheRoute() {
        Staff s = staff();
        String key = newKey("countersign-reject");
        ApprovalInstanceId instance = start(key, s, s.first(), s.second(), s.third());
        Task firstTask = openTasks(instance).stream().filter(t -> candidateOf(t).equals(TestAccounts.account(s.first()).value().toString())).findFirst().orElseThrow();
        Task secondTask = openTasks(instance).stream().filter(t -> candidateOf(t).equals(TestAccounts.account(s.second()).value().toString())).findFirst().orElseThrow();
        decide(s.first(), firstTask, DecisionOutcome.APPROVE, null);

        DecisionResult result = decide(s.second(), secondTask, DecisionOutcome.REJECT, "price too high");

        assertThat(result.status()).isEqualTo(ApprovalStatus.RETURNED);
        assertThat(requestStatus(key)).isEqualTo("RETURNED");
        assertThat(openTasks(instance)).isEmpty();
        assertThat(jdbc.queryForList("select status from platform_approval.approval_tasks where instance_id = ? order by task_id", String.class,
                instance.value())).containsExactlyInAnyOrder("COMPLETED", "COMPLETED", "CLOSED");
        eventually(() -> assertThat(events.completed()).anySatisfy(e -> {
            assertThat(e.document().documentId().value()).isEqualTo(key);
            assertThat(e.outcome()).isEqualTo(ApprovalOutcome.RETURNED);
            assertThat(e.reason()).isEqualTo(new Reason("price too high"));
        }));
    }

    @Test
    @DisplayName("RULE-273/291 a rejection by an added co-signer is advisory: recorded and published as advisory, the round goes on")
    void advisoryRejectionDoesNotEndTheRound() {
        Staff s = staff();
        String key = newKey("countersign-advisory");
        ApprovalInstanceId instance = start(key, s, s.first(), s.second());
        Task advisory = openTasks(instance).stream().filter(t -> candidateOf(t).equals(TestAccounts.account(s.second()).value().toString())).findFirst().orElseThrow();
        taskService.setVariableLocal(advisory.getId(), "advisory", true);
        Task regular = openTasks(instance).stream().filter(t -> candidateOf(t).equals(TestAccounts.account(s.first()).value().toString())).findFirst().orElseThrow();

        DecisionResult afterAdvice = decide(s.second(), advisory, DecisionOutcome.REJECT, "I would not");

        assertThat(afterAdvice.status()).isEqualTo(ApprovalStatus.RUNNING);
        assertThat(requestStatus(key)).isEqualTo("RUNNING");
        assertThat(openTasks(instance)).extracting(Task::getId).containsExactly(regular.getId());

        assertThat(decide(s.first(), regular, DecisionOutcome.APPROVE, null).status()).isEqualTo(ApprovalStatus.APPROVED);
        eventually(() -> {
            assertThat(events.resulted().stream().filter(e -> e.document().documentId().value().equals(key)).toList())
                    .anySatisfy(e -> {
                        assertThat(e.effect()).isEqualTo(TaskEffect.ADVISORY);
                        assertThat(e.outcome()).isEqualTo(TaskOutcome.REJECTED);
                        assertThat(e.reason()).isEqualTo(new Reason("I would not"));
                    });
            assertThat(events.completed().stream().filter(e -> e.document().documentId().value().equals(key)).map(e -> e.outcome()).toList())
                    .containsExactly(ApprovalOutcome.APPROVED);
        });
    }

    @Test
    @DisplayName("RULE-248/300 a route whose field names nobody who exists fails the start instead of passing silently")
    void invalidApproverFailsInsteadOfSkipping() {
        Staff s = staff();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> start(newKey("countersign-invalid"), s, 424242L))
                .isInstanceOfSatisfying(com.forcitis.one.kernel.DomainException.class, e -> org.assertj.core.api.Assertions.assertThat(e.errorCode()).isEqualTo(com.forcitis.one.platform.approval.api.ApprovalError.PLATFORM_APPROVAL_ASSIGNEE_INPUT_INVALID));
    }
}
