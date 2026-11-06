package com.forcitis.one.platform.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.cost.timesheet.api.TimesheetApprovalField;
import com.forcitis.one.cost.timesheet.api.TimesheetRoutingFlag;
import com.forcitis.one.fixture.doubles.TestAccounts;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.platform.approval.api.ApprovalError;
import com.forcitis.one.platform.approval.api.ApprovalField;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.api.ApprovalTaskId;
import com.forcitis.one.platform.approval.application.ClaimTask;
import com.forcitis.one.platform.approval.application.DecideTasks;
import com.forcitis.one.platform.approval.application.DecisionOutcome;
import com.forcitis.one.platform.approval.application.ReassignTask;
import com.forcitis.one.platform.approval.application.TaskDecision;
import com.forcitis.one.platform.approval.application.TransferTask;
import com.forcitis.one.platform.approval.domain.ApprovalEngine;
import java.util.List;
import java.util.OptionalLong;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * #1542: on a countersign node (one share per approver, every share must approve) one person cannot decide two shares: a share is not handed, by its holder or
 * by the process administrator, to a person who already decided another share of the same round. The timesheet's project manager node is the countersign node
 * under test; a hand-over to somebody outside the round still works.
 */
class CountersignDistinctPeopleIT extends ApprovalIntegrationSupport {

    @Autowired
    TransferTask transferTask;

    @Autowired
    ReassignTask reassignTask;

    @Autowired
    ClaimTask claimTask;

    @Autowired
    DecideTasks decideTasks;

    @Autowired
    ApprovalEngine engine;

    record Scene(ApprovalInstanceId instance, ApprovalTaskId aShare, ApprovalTaskId bShare, long a, long b, long outsider) {
    }

    /** Two project managers, one share each; A has approved the share of A, the share of B is open. */
    private Scene sceneWhereAHasDecided() {
        long unit = newId();
        long initiator = newId();
        long a = newId();
        long b = newId();
        long outsider = newId();
        organisation.unit(unit, null, null).person(initiator, unit, null).person(a, unit, null).person(b, unit, null).person(outsider, unit, null);
        ApprovalInstanceId instance = tx.execute(s -> approvals.start(startRequest(newKey("countersign"), TestAccounts.employee(initiator),
                Set.of(TimesheetRoutingFlag.PROJECT_HOURS.flagName()), List.of(new ApprovalField(TimesheetApprovalField.PROJECT_MANAGER.fieldName(),
                        List.of(TestAccounts.employee(a).toString(), TestAccounts.employee(b).toString()))))));
        ApprovalTaskId aShare = shareOf(instance, a);
        ApprovalTaskId bShare = shareOf(instance, b);
        assertThat(aShare).isNotEqualTo(bShare);
        actAs(a);
        claimTask.execute(aShare, TestAccounts.account(a), OptionalLong.empty());
        decideTasks.execute(TestAccounts.account(a), List.of(new TaskDecision(aShare, DecisionOutcome.APPROVE, null)), OptionalLong.empty());
        actAs(b);
        claimTask.execute(bShare, TestAccounts.account(b), OptionalLong.empty());
        return new Scene(instance, aShare, bShare, a, b, outsider);
    }

    private ApprovalTaskId shareOf(ApprovalInstanceId instance, long person) {
        return engine.findOpenTasksFor(TestAccounts.account(person)).stream().filter(task -> task.instanceId().equals(instance)).findFirst().orElseThrow()
                .taskId();
    }

    private void assertRefused(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.errorCode()).isEqualTo(ApprovalError.PLATFORM_APPROVAL_TRANSFER_TARGET_INVALID));
    }

    private String holderOf(Scene s) {
        return openTask(s.instance()).getAssignee();
    }

    @Test
    @DisplayName("#1542 the holder of a share cannot hand it to the person who already decided another share of the round: the round is unchanged")
    void aShareIsNotTransferredToSomebodyWhoDecidedAnotherShare() {
        Scene s = sceneWhereAHasDecided();

        assertRefused(() -> transferTask.execute(s.bShare(), TestAccounts.account(s.b()), TestAccounts.employee(s.a()), "r", OptionalLong.empty()));

        assertThat(holderOf(s)).isEqualTo(TestAccounts.account(s.b()).value().toString());
    }

    @Test
    @DisplayName("#1542 the process administrator cannot reassign a share to the person who already decided another share of the round")
    void aShareIsNotReassignedToSomebodyWhoDecidedAnotherShare() {
        Scene s = sceneWhereAHasDecided();

        assertRefused(() -> reassignTask.execute(s.bShare(), TestAccounts.account(ADMINISTRATOR), TestAccounts.employee(s.a()), "r", OptionalLong.empty()));

        assertThat(holderOf(s)).isEqualTo(TestAccounts.account(s.b()).value().toString());
    }

    @Test
    @DisplayName("#1542 a share can still be handed to somebody outside the round, by its holder and by the administrator")
    void aShareStillGoesToSomebodyOutsideTheRound() {
        Scene s = sceneWhereAHasDecided();

        transferTask.execute(s.bShare(), TestAccounts.account(s.b()), TestAccounts.employee(s.outsider()), "on leave", OptionalLong.empty());
        assertThat(holderOf(s)).isEqualTo(TestAccounts.account(s.outsider()).value().toString());

        reassignTask.execute(s.bShare(), TestAccounts.account(ADMINISTRATOR), TestAccounts.employee(s.b()), "back", OptionalLong.empty());
        assertThat(holderOf(s)).isEqualTo(TestAccounts.account(s.b()).value().toString());
    }
}
