package com.forcitis.one.platform.approval;

import com.forcitis.one.cost.timesheet.api.TimesheetApprovalProcess;
import com.forcitis.one.fixture.doubles.TestAccounts;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.platform.approval.api.RoundNumber;
import com.forcitis.one.kernel.Reason;
import com.forcitis.one.cost.timesheet.api.TimesheetApprovalField;
import com.forcitis.one.cost.timesheet.api.TimesheetRoutingFlag;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.approval.api.ApprovalField;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.api.ApprovalOutcome;
import com.forcitis.one.platform.approval.api.RetractionResult;
import com.forcitis.one.platform.approval.api.WithdrawCheck;
import com.forcitis.one.platform.approval.application.DecideTasks;
import com.forcitis.one.platform.approval.application.DecisionOutcome;
import com.forcitis.one.platform.approval.application.TaskDecision;
import com.forcitis.one.platform.approval.domain.ApprovalRequestRepository;
import java.util.List;
import java.util.Set;
import org.flowable.engine.HistoryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;

/** Withdraw and cancel are the initiator's, share one check with the display, and stop at the cut-off node. */
class RetractionIT extends ApprovalIntegrationSupport {

    @Autowired
    DecideTasks decideTasks;

    @Autowired
    HistoryService history;

    @Autowired
    ApprovalRequestRepository requests;

    record Staff(long initiator, long manager, long stranger) {
    }

    Staff staff() {
        long unit = newId();
        Staff s = new Staff(newId(), newId(), newId());
        organisation.unit(unit, null, s.manager()).person(s.manager(), unit, null).person(s.initiator(), unit, s.manager())
                .person(s.stranger(), unit, null);
        return s;
    }

    ApprovalInstanceId startFor(String key, Staff s) {
        return tx.execute(status -> approvals.start(startRequest(key, TestAccounts.employee(s.initiator()),
                Set.of(TimesheetRoutingFlag.PROJECT_HOURS.flagName()),
                List.of(new ApprovalField(TimesheetApprovalField.PROJECT_MANAGER.fieldName(), List.of(TestAccounts.employee(s.manager()).toString()))))));
    }

    RetractionResult withdraw(String key, long actor, String reason) {
        return tx.execute(status -> approvals.withdraw(TimesheetApprovalProcess.KEY, document(key), TestAccounts.account(actor), reason));
    }

    RetractionResult cancel(String key, long actor, String reason) {
        return tx.execute(status -> approvals.cancel(TimesheetApprovalProcess.KEY, document(key), TestAccounts.account(actor), reason));
    }

    WithdrawCheck check(String key, long actor) {
        return tx.execute(status -> approvals.checkWithdraw(TimesheetApprovalProcess.KEY, document(key), TestAccounts.account(actor)));
    }

    @Test
    @DisplayName("RULE-269 D-1a-72 the initiator withdraws a running approval with a reason: the round ends RETURNED, the tasks close, a new round can start")
    void withdrawEndsTheRoundAndAllowsANewOne() {
        Staff s = staff();
        actAs(s.initiator());
        String key = newKey("withdraw");
        ApprovalInstanceId first = startFor(key, s);
        assertThat(check(key, s.initiator())).isEqualTo(WithdrawCheck.ALLOWED);

        assertThat(withdraw(key, s.initiator(), "wrong hours")).isEqualTo(RetractionResult.DONE);

        assertThat(requestStatus(key)).isEqualTo("RETURNED");
        assertThat(approvals.isFinished(first)).isTrue();
        assertThat(taskService.createTaskQuery().processInstanceId(first.value().toString()).count()).isZero();
        assertThat(jdbc.queryForList("select status from platform_approval.approval_tasks where instance_id = ?", String.class, first.value()))
                .containsExactly("CLOSED");
        eventually(() -> assertThat(events.completed()).anySatisfy(e -> {
            assertThat(e.document().documentId().toString()).isEqualTo(key);
            assertThat(e.outcome()).isEqualTo(ApprovalOutcome.RETURNED);
            assertThat(e.reason()).isEqualTo(new Reason("wrong hours"));
            assertThat(e.round()).isEqualTo(new RoundNumber(1));
        }));
        assertThat(events.completed().getFirst().returnSource()).as("D-1a-72 a withdrawal is a return whose source is the initiator")
                .isEqualTo(com.forcitis.one.platform.approval.api.ReturnSource.INITIATOR);
        assertThat(events.completed().getFirst().envelope().actorId()).isEqualTo(TestAccounts.account(s.initiator()).value());

        ApprovalInstanceId second = startFor(key, s);
        assertThat(second).isNotEqualTo(first);
        assertThat(requests.rounds(TimesheetApprovalProcess.KEY, document(key))).hasSize(2);
    }

    @Test
    @DisplayName("RULE-268/I-7 only the initiator may withdraw or cancel: anybody else is refused with 403 semantics, audited, and nothing changes")
    void strangersAreRefusedAndAudited() {
        Staff s = staff();
        String key = newKey("withdraw-stranger");
        ApprovalInstanceId instance = startFor(key, s);
        assertThat(check(key, s.stranger())).isEqualTo(WithdrawCheck.NOT_INITIATOR);

        assertThatThrownBy(() -> withdraw(key, s.stranger(), "mine now")).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> cancel(key, s.manager(), "approver cancels")).isInstanceOf(AccessDeniedException.class);

        assertThat(requestStatus(key)).isEqualTo("RUNNING");
        assertThat(approvals.isFinished(instance)).isFalse();
        verify(audit, atLeastOnce()).recordIndependently(argThat(entry -> entry.action().equals("platform.approval.request.denied")
                && entry.actorId().equals(TestAccounts.account(s.stranger()))));
    }

    @Test
    @DisplayName("RULE-268/269 withdraw and cancel need a reason")
    void aReasonIsRequired() {
        Staff s = staff();
        String key = newKey("retract-reason");
        startFor(key, s);
        for (String blank : new String[] {null, "", "   "}) {
            assertThat(withdraw(key, s.initiator(), blank)).isEqualTo(RetractionResult.REASON_REQUIRED);
            assertThat(cancel(key, s.initiator(), blank)).isEqualTo(RetractionResult.REASON_REQUIRED);
        }
        assertThat(requestStatus(key)).isEqualTo("RUNNING");
    }

    @Test
    @DisplayName("RULE-288/279 cancel ends the whole approval as CANCELLED and keeps the history: former approvers' tasks and the instance remain readable")
    void cancelKeepsTheHistory() {
        Staff s = staff();
        String key = newKey("cancel");
        ApprovalInstanceId instance = startFor(key, s);

        assertThat(cancel(key, s.initiator(), "not needed any more")).isEqualTo(RetractionResult.DONE);

        assertThat(requestStatus(key)).isEqualTo("CANCELLED");
        assertThat(approvals.isFinished(instance)).isTrue();
        assertThat(history.createHistoricProcessInstanceQuery().processInstanceId(instance.value().toString()).count()).isEqualTo(1);
        assertThat(history.createHistoricTaskInstanceQuery().processInstanceId(instance.value().toString()).count()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select result_reason from platform_approval.approval_requests where document_id = cast(? as uuid)", String.class, key))
                .isEqualTo("not needed any more");
        eventually(() -> assertThat(events.completed()).anySatisfy(e -> {
            assertThat(e.document().documentId().toString()).isEqualTo(key);
            assertThat(e.outcome()).isEqualTo(ApprovalOutcome.CANCELLED);
        }));
        assertThat(startFor(key, s)).as("a cancelled approval can be started again").isNotEqualTo(instance);
    }

    @Test
    @DisplayName("RULE-269 an approval that already ended can be neither withdrawn nor cancelled, and the display says why")
    void finishedApprovalsAreNotRetractable() {
        Staff s = staff();
        String key = newKey("retract-finished");
        ApprovalInstanceId instance = startFor(key, s);
        decideTasks.execute(TestAccounts.account(s.manager()), List.of(new TaskDecision(openTaskId(instance), DecisionOutcome.APPROVE, null)), java.util.OptionalLong.empty());
        assertThat(requestStatus(key)).isEqualTo("APPROVED");

        assertThat(check(key, s.initiator())).isEqualTo(WithdrawCheck.NOT_RUNNING);
        assertThat(withdraw(key, s.initiator(), "too late")).isEqualTo(RetractionResult.NOT_RUNNING);
        assertThat(cancel(key, s.initiator(), "too late")).isEqualTo(RetractionResult.NOT_RUNNING);
    }

    @Test
    @DisplayName("RULE-309 once a cut-off node has a finished task neither withdraw nor cancel is possible, and the display agrees")
    void cutoffBlocksBothAndDisplayAgrees() {
        deployVariant("cutoff");
        Staff s = staff();
        String key = newKey("cutoff");
        ApprovalInstanceId instance = startFor(key, s);

        decideTasks.execute(TestAccounts.account(s.manager()), List.of(new TaskDecision(openTaskId(instance, "firstReview"), DecisionOutcome.APPROVE, null)), java.util.OptionalLong.empty());
        assertThat(check(key, s.initiator())).as("a finished task before the cut-off node does not block").isEqualTo(WithdrawCheck.ALLOWED);

        decideTasks.execute(TestAccounts.account(s.manager()), List.of(new TaskDecision(openTaskId(instance, "cutoffReview"), DecisionOutcome.APPROVE, null)), java.util.OptionalLong.empty());
        assertThat(check(key, s.initiator())).isEqualTo(WithdrawCheck.CUTOFF_PASSED);
        assertThat(withdraw(key, s.initiator(), "regret")).isEqualTo(RetractionResult.CUTOFF_PASSED);
        assertThat(cancel(key, s.initiator(), "regret")).isEqualTo(RetractionResult.CUTOFF_PASSED);
        assertThat(requestStatus(key)).isEqualTo("RUNNING");
        assertThat(approvals.isFinished(instance)).isFalse();
    }

    @Test
    @DisplayName("RULE-309 a cut-off node that is only reached, not finished, does not block; the check is false only for non-initiators")
    void reachingTheCutoffNodeIsNotPassingIt() {
        deployVariant("cutoff");
        Staff s = staff();
        String key = newKey("cutoff-reached");
        ApprovalInstanceId instance = startFor(key, s);
        decideTasks.execute(TestAccounts.account(s.manager()), List.of(new TaskDecision(openTaskId(instance, "firstReview"), DecisionOutcome.APPROVE, null)), java.util.OptionalLong.empty());
        assertThat(openTask(instance).getTaskDefinitionKey()).isEqualTo("cutoffReview");

        assertThat(check(key, s.initiator())).isEqualTo(WithdrawCheck.ALLOWED);
        assertThat(check(key, s.stranger())).isEqualTo(WithdrawCheck.NOT_INITIATOR);
        assertThat(cancel(key, s.initiator(), "changed my mind")).isEqualTo(RetractionResult.DONE);
        assertThat(requestStatus(key)).isEqualTo("CANCELLED");
    }
}
