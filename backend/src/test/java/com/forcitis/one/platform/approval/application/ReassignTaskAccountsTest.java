package com.forcitis.one.platform.approval.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.forcitis.one.fixture.doubles.TestAccounts;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.approval.api.ApprovalError;
import com.forcitis.one.platform.approval.domain.ApprovalEngine;
import com.forcitis.one.platform.approval.domain.ApprovalRequest;
import com.forcitis.one.platform.approval.domain.Moment;
import com.forcitis.one.platform.approval.domain.ProcessAdministrator;
import java.time.Instant;
import java.time.LocalDate;
import java.util.OptionalLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The process administrator reassigns a task to an employee, who must not be the initiator (an employee); the task then goes to that
 * employee's account. Employee 1001 started the approval; accounts are the employee's number plus 5000.
 */
class ReassignTaskAccountsTest {
    static final EmployeeId INITIATOR = TestAccounts.employee(1001);
    static final UserId ADMINISTRATOR = TestAccounts.account(1009);

    final ProcessAdministrator administrator = mock(ProcessAdministrator.class);
    final TaskGuard guard = mock(TaskGuard.class);
    final ApprovalEngine engine = mock(ApprovalEngine.class);
    final Approvals approvals = mock(Approvals.class);
    final ApprovalRequest request = mock(ApprovalRequest.class);
    final BusinessTime time = mock(BusinessTime.class);
    final ReassignTask reassign = new ReassignTask(administrator, guard, engine, approvals, OpenTasks.employed(1001, 1003, 6001), mock(DenialAudit.class),
            time, TestAccounts.everyoneHasOne(), new NodeShares(engine));

    ReassignTaskAccountsTest() {
        when(administrator.isAdministrator(ADMINISTRATOR)).thenReturn(true);
        when(guard.requireTask(OpenTasks.TASK)).thenReturn(OpenTasks.claimedBy(INITIATOR, TestAccounts.account(1002)));
        when(approvals.lock(OpenTasks.INSTANCE)).thenReturn(request);
        when(time.moment()).thenReturn(new Moment(Instant.parse("2026-10-14T02:00:00Z"), LocalDate.of(2026, 10, 14)));
    }

    private void reassignTo(long employee) {
        reassign.execute(OpenTasks.TASK, ADMINISTRATOR, TestAccounts.employee(employee), "swap", OptionalLong.empty());
    }

    @Test
    @DisplayName("the initiator is refused as the new handler, found by employee although the account numbers differ")
    void initiatorRefusedWhenNumbersDiffer() {
        assertThatThrownBy(() -> reassignTo(1001)).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.errorCode()).isEqualTo(ApprovalError.PLATFORM_APPROVAL_TRANSFER_TARGET_INVALID));
        verify(engine, never()).reassignTask(any(), any());
    }

    @Test
    @DisplayName("the employee numbered like the initiator's account is another person; the task goes to the new handler's account")
    void taskGoesToTheTargetsAccount() {
        reassignTo(6001);
        reassignTo(1003);

        verify(engine).reassignTask(OpenTasks.TASK, TestAccounts.account(6001));
        verify(engine).reassignTask(OpenTasks.TASK, TestAccounts.account(1003));
        verify(request).reassignTask(any(), any(), org.mockito.ArgumentMatchers.eq(TestAccounts.account(1003)), any(), any());
    }
}
