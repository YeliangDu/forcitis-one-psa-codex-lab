package com.forcitis.one.platform.approval.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import com.forcitis.one.platform.approval.domain.TaskView;
import java.time.Instant;
import java.time.LocalDate;
import java.util.OptionalLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Handing a task over: the target is chosen as an employee, tested against the initiator (an employee) and the handler (an account),
 * and written into the task as an account. Employee 1001 started the approval, account 6002 (employee 1002) holds the task; accounts
 * are the employee's number plus 5000.
 */
class TransferTaskAccountsTest {
    static final EmployeeId INITIATOR = TestAccounts.employee(1001);
    static final UserId HANDLER = TestAccounts.account(1002);

    final TaskGuard guard = mock(TaskGuard.class);
    final ApprovalEngine engine = mock(ApprovalEngine.class);
    final Approvals approvals = mock(Approvals.class);
    final ApprovalRequest request = mock(ApprovalRequest.class);
    final BusinessTime time = mock(BusinessTime.class);
    final TransferTask transfer = new TransferTask(guard, engine, approvals, OpenTasks.employed(1001, 1002, 1003, 6001), time, TestAccounts.everyoneHasOne(), new NodeShares(engine));

    TransferTaskAccountsTest() {
        TaskView task = OpenTasks.claimedBy(INITIATOR, HANDLER);
        when(guard.requireHandler(eq(OpenTasks.TASK), eq(HANDLER), any())).thenReturn(task);
        when(approvals.lock(OpenTasks.INSTANCE)).thenReturn(request);
        when(time.moment()).thenReturn(new Moment(Instant.parse("2026-10-14T02:00:00Z"), LocalDate.of(2026, 10, 14)));
    }

    private void transferTo(long employee) {
        transfer.execute(OpenTasks.TASK, HANDLER, TestAccounts.employee(employee), "on leave", OptionalLong.empty());
    }

    private void assertRefused(long employee) {
        assertThatThrownBy(() -> transferTo(employee)).isInstanceOfSatisfying(DomainException.class,
                e -> org.assertj.core.api.Assertions.assertThat(e.errorCode()).isEqualTo(ApprovalError.PLATFORM_APPROVAL_TRANSFER_TARGET_INVALID));
        verify(engine, never()).reassignTask(any(), any());
    }

    @Test
    @DisplayName("the initiator is refused as the target, found by employee although the account numbers differ")
    void initiatorRefusedAsTargetWhenNumbersDiffer() {
        assertRefused(1001);
    }

    @Test
    @DisplayName("the handler's own employee is refused as the target")
    void handlerRefusedAsTargetWhenNumbersDiffer() {
        assertRefused(1002);
    }

    @Test
    @DisplayName("the employee numbered like the initiator's account is another person and may take the task")
    void employeeNumberedLikeTheInitiatorsAccountMayTakeIt() {
        assertThatCode(() -> transferTo(6001)).doesNotThrowAnyException();
        verify(engine).reassignTask(OpenTasks.TASK, TestAccounts.account(6001));
    }

    @Test
    @DisplayName("the task is written to the target as the target's account, in the engine and in the approval")
    void targetGetsTheTaskAsItsAccount() {
        transferTo(1003);

        verify(engine).reassignTask(OpenTasks.TASK, TestAccounts.account(1003));
        verify(request).transferTask(eq(OpenTasks.TASK), eq(HANDLER), eq(TestAccounts.account(1003)), any(), any());
    }
}
