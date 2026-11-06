package com.forcitis.one.platform.approval.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.forcitis.one.fixture.doubles.TestAccounts;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.platform.approval.api.ApprovalTaskStatus;
import com.forcitis.one.platform.approval.domain.ApprovalEngine;
import com.forcitis.one.platform.approval.domain.ApprovalRequest;
import com.forcitis.one.platform.approval.domain.ApprovalRequestRepository;
import com.forcitis.one.platform.approval.domain.ApprovalTask;
import com.forcitis.one.platform.approval.domain.DeadlinePlan;
import com.forcitis.one.platform.approval.domain.Moment;
import com.forcitis.one.platform.approval.domain.ProcessAdministrator;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * An overdue task goes up to the superior of its holder, unless that is the initiator. The holder is account 6002 (employee 1002);
 * the superior is looked up as an employee, set against the initiator (an employee), and the task goes to the superior's account.
 */
class TaskDeadlineAccountsTest {
    static final EmployeeId INITIATOR = TestAccounts.employee(1001);
    static final UserId HOLDER = TestAccounts.account(1002);
    static final UserId PROCESS_ADMINISTRATOR = TestAccounts.account(1009);
    static final Instant LONG_AGO = Instant.parse("2026-10-01T02:00:00Z");
    static final Moment NOW = new Moment(Instant.parse("2026-10-14T02:00:00Z"), LocalDate.of(2026, 10, 14));

    final ApprovalRequestRepository requests = mock(ApprovalRequestRepository.class);
    final ApprovalEngine engine = mock(ApprovalEngine.class);
    final OrgDirectory org = mock(OrgDirectory.class);
    final ProcessAdministrator administrator = mock(ProcessAdministrator.class);
    final ApprovalRequest request = mock(ApprovalRequest.class);
    final BusinessTime time = mock(BusinessTime.class);
    final TaskDeadline deadline = new TaskDeadline(requests, mock(Approvals.class), engine, org, administrator, new SimpleMeterRegistry(), time,
            TestAccounts.everyoneHasOne(), new NodeShares(engine));

    TaskDeadlineAccountsTest() {
        ApprovalTask task = mock(ApprovalTask.class);
        when(task.status()).thenReturn(ApprovalTaskStatus.CLAIMED);
        when(task.instanceId()).thenReturn(OpenTasks.INSTANCE);
        when(task.overdueAt()).thenReturn(Optional.of(LONG_AGO));
        when(task.escalatedAt()).thenReturn(Optional.empty());
        when(task.deadline()).thenReturn(Optional.of(new DeadlinePlan(LONG_AGO, LONG_AGO, LONG_AGO)));
        when(request.task(OpenTasks.TASK)).thenReturn(task);
        when(request.instanceId()).thenReturn(OpenTasks.INSTANCE);
        when(requests.findByTaskForUpdate(OpenTasks.TASK)).thenReturn(Optional.of(request));
        when(engine.findTask(OpenTasks.TASK)).thenReturn(Optional.of(OpenTasks.claimedBy(INITIATOR, HOLDER)));
        when(administrator.holders()).thenReturn(Set.of(PROCESS_ADMINISTRATOR));
        when(time.moment()).thenReturn(NOW);
    }

    @Test
    @DisplayName("the holder's superior takes the task as the superior's account")
    void superiorGetsTheTaskAsItsAccount() {
        when(org.findSupervisor(TestAccounts.employee(1002))).thenReturn(Optional.of(TestAccounts.employee(1003)));

        deadline.enforce(OpenTasks.TASK);

        verify(engine).addCandidates(eq(OpenTasks.TASK), eq(List.of(TestAccounts.account(1003))));
        verify(request).escalateTask(eq(OpenTasks.TASK), eq(List.of(TestAccounts.account(1003))), eq(false), any());
    }

    @Test
    @DisplayName("when the superior is the initiator the task goes to the process administrator, found by employee although the account numbers differ")
    void initiatorAsSuperiorIsSkipped() {
        when(org.findSupervisor(TestAccounts.employee(1002))).thenReturn(Optional.of(INITIATOR));

        deadline.enforce(OpenTasks.TASK);

        verify(engine).addCandidates(eq(OpenTasks.TASK), eq(List.of(PROCESS_ADMINISTRATOR)));
        verify(request).escalateTask(eq(OpenTasks.TASK), eq(List.of(PROCESS_ADMINISTRATOR)), eq(true), any());
    }

    @Test
    @DisplayName("the superior is looked up by the holder's employee: the employee numbered like the holder's account has another superior")
    void holderIsLookedUpAsItsEmployee() {
        when(org.findSupervisor(TestAccounts.employee(6002))).thenReturn(Optional.of(TestAccounts.employee(1003)));

        deadline.enforce(OpenTasks.TASK);

        verify(engine).addCandidates(eq(OpenTasks.TASK), eq(List.of(PROCESS_ADMINISTRATOR)));
    }

    @Test
    @DisplayName("a direct superior without an account ends the walk: the task goes to the process administrator, not to the superior above")
    void superiorWithoutAccountGoesToTheAdministrator() {
        TaskDeadline withoutAccount = new TaskDeadline(requests, mock(Approvals.class), engine, org, administrator, new SimpleMeterRegistry(), time,
                TestAccounts.except(TestAccounts.employee(1003)), new NodeShares(engine));
        when(org.findSupervisor(TestAccounts.employee(1002))).thenReturn(Optional.of(TestAccounts.employee(1003)));
        when(org.findSupervisor(TestAccounts.employee(1003))).thenReturn(Optional.of(TestAccounts.employee(1004)));

        withoutAccount.enforce(OpenTasks.TASK);

        verify(request).escalateTask(eq(OpenTasks.TASK), eq(List.of(PROCESS_ADMINISTRATOR)), eq(true), any());
    }
}
