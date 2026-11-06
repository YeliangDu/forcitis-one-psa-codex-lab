package com.forcitis.one.platform.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.cost.timesheet.api.TimesheetApprovalField;
import com.forcitis.one.fixture.doubles.TestAccounts;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.platform.approval.api.ApprovalError;
import com.forcitis.one.platform.approval.api.ApprovalField;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.api.ApprovalStartRequest;
import com.forcitis.one.platform.approval.api.ApprovalTaskId;
import com.forcitis.one.platform.approval.api.ProcessKey;
import com.forcitis.one.platform.approval.application.ClaimTask;
import com.forcitis.one.platform.approval.application.ReassignTask;
import com.forcitis.one.platform.approval.application.TransferTask;
import java.util.List;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * #1436: on a node that takes no fallback, a task is handed over (by its handler or by the process administrator) only to a person the node's own rule names;
 * nobody else, the administrator included, can end up deciding it. Other nodes are unchanged (TaskTransferIT, TaskHandlingRecordsIT).
 */
class NodeWithoutFallbackTransferIT extends ApprovalIntegrationSupport {

    static final ProcessKey POOL = ProcessKey.of("cost_demo_pool_review");

    @Autowired
    TransferTask transferTask;

    @Autowired
    ReassignTask reassignTask;

    @Autowired
    ClaimTask claimTask;

    record Scene(ApprovalInstanceId instance, ApprovalTaskId task, long initiator, long first, long second, long outsider) {
    }

    private Scene scene() {
        long unit = newId();
        long initiator = newId();
        long first = newId();
        long second = newId();
        long outsider = newId();
        organisation.unit(unit, null, null).person(initiator, unit, null).person(first, unit, null).person(second, unit, null).person(outsider, unit, null);
        ApprovalStartRequest base = startRequest(newKey("nofallback-pool"), TestAccounts.employee(initiator), Set.of(),
                List.of(new ApprovalField(TimesheetApprovalField.PROJECT_MANAGER.fieldName(),
                        List.of(TestAccounts.employee(first).toString(), TestAccounts.employee(second).toString()))), 1);
        ApprovalStartRequest request = new ApprovalStartRequest(POOL, base.document(), base.legalEntityId(), base.initiator(), base.round(), UUID.randomUUID(),
                Set.of(), base.formFields(), base.contentHash(), base.summary());
        ApprovalInstanceId instance = tx.execute(status -> approvals.start(request));
        ApprovalTaskId task = openTaskId(instance);
        claimTask.execute(task, TestAccounts.account(first), OptionalLong.empty());
        return new Scene(instance, task, initiator, first, second, outsider);
    }

    private void withTheNode(Runnable test) {
        String bpmn = repository.createDeployment().addClasspathResource("nofallback/cost_demo_pool_review.bpmn20.xml").deploy().getId();
        try {
            test.run();
        } finally {
            repository.deleteDeployment(bpmn, true);
        }
    }

    private void assertRefused(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.errorCode()).isEqualTo(ApprovalError.PLATFORM_APPROVAL_TRANSFER_TARGET_INVALID));
    }

    @Test
    @DisplayName("#1436 a handler cannot hand a no-fallback task to somebody the node's rule does not name")
    void aHandlerCannotTransferToAnOutsider() {
        withTheNode(() -> {
            Scene s = scene();

            assertRefused(() -> transferTask.execute(s.task(), TestAccounts.account(s.first()), TestAccounts.employee(s.outsider()), "r", OptionalLong.empty()));

            assertThat(openTask(s.instance())).extracting(task -> task.getAssignee()).isEqualTo(TestAccounts.account(s.first()).value().toString());
        });
    }

    @Test
    @DisplayName("#1436 the process administrator cannot reassign a no-fallback task to himself or to anybody else the node's rule does not name")
    void theAdministratorCannotReassignToAnOutsider() {
        withTheNode(() -> {
            Scene s = scene();

            assertRefused(() -> reassignTask.execute(s.task(), TestAccounts.account(ADMINISTRATOR), TestAccounts.employee(ADMINISTRATOR), "r", OptionalLong.empty()));
            assertRefused(() -> reassignTask.execute(s.task(), TestAccounts.account(ADMINISTRATOR), TestAccounts.employee(s.outsider()), "r", OptionalLong.empty()));

            assertThat(openTask(s.instance())).extracting(task -> task.getAssignee()).isEqualTo(TestAccounts.account(s.first()).value().toString());
        });
    }

    @Test
    @DisplayName("#1436 peers named by the node's rule can still hand the task to each other, and the administrator can reassign it to one of them")
    void peersCanStillTransferAndTheAdministratorMayChooseAPeer() {
        withTheNode(() -> {
            Scene s = scene();

            transferTask.execute(s.task(), TestAccounts.account(s.first()), TestAccounts.employee(s.second()), "on leave", OptionalLong.empty());
            assertThat(openTask(s.instance())).extracting(task -> task.getAssignee()).isEqualTo(TestAccounts.account(s.second()).value().toString());

            reassignTask.execute(s.task(), TestAccounts.account(ADMINISTRATOR), TestAccounts.employee(s.first()), "back", OptionalLong.empty());
            assertThat(openTask(s.instance())).extracting(task -> task.getAssignee()).isEqualTo(TestAccounts.account(s.first()).value().toString());
        });
    }
}
