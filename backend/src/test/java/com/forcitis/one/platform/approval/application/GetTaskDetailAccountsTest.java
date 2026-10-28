package com.forcitis.one.platform.approval.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.forcitis.one.cost.timesheet.api.TimesheetApprovalProcess;
import com.forcitis.one.fixture.doubles.TestAccounts;
import com.forcitis.one.kernel.DocumentId;
import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.kernel.DocumentTypeCode;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.approval.api.ApprovalError;
import com.forcitis.one.platform.approval.api.ApprovalSummary;
import com.forcitis.one.platform.approval.api.ApprovalTaskStatus;
import com.forcitis.one.platform.approval.api.NodeKey;
import com.forcitis.one.platform.approval.api.ProcessName;
import com.forcitis.one.platform.approval.domain.ApprovalEngine;
import com.forcitis.one.platform.approval.domain.ApprovalTaskQueries;
import com.forcitis.one.platform.approval.domain.TaskRecord;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Who may look at a task: its initiator (an employee), its handler or a candidate (accounts). Employee 1001 started the approval, so
 * the viewer is the account 6001; the account numbered 1001 belongs to somebody else and sees nothing.
 */
class GetTaskDetailAccountsTest {
    static final EmployeeId INITIATOR = TestAccounts.employee(1001);
    static final UserId INITIATORS_ACCOUNT = TestAccounts.account(1001);
    static final UserId HANDLER = TestAccounts.account(1002);
    static final UserId ACCOUNT_NUMBERED_LIKE_THE_INITIATOR = TestAccounts.unknownAccount(1001);
    static final Instant NOW = Instant.parse("2026-10-14T02:00:00Z");

    final ApprovalEngine engine = mock(ApprovalEngine.class);
    final ApprovalTaskQueries tasks = mock(ApprovalTaskQueries.class);
    final BusinessTime time = mock(BusinessTime.class);
    final GetTaskDetail detail = new GetTaskDetail(engine, tasks, OpenTasks.employed(1001, 1002), time, TestAccounts.everyoneHasOne());

    GetTaskDetailAccountsTest() {
        when(time.now()).thenReturn(NOW);
        when(engine.processNameOf(TimesheetApprovalProcess.KEY)).thenReturn(new ProcessName("工时审批"));
    }

    private static TaskRecord record(ApprovalTaskStatus status) {
        return new TaskRecord(OpenTasks.TASK, OpenTasks.INSTANCE, TimesheetApprovalProcess.KEY,
                new DocumentReference(new DocumentTypeCode("TIMESHEET"), com.forcitis.one.TestApprovalIds.document("7")), new NodeKey("approverReview"), INITIATOR, status, Optional.of(HANDLER),
                NOW, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), new ApprovalSummary("TS-1"), 3, 1);
    }

    private void assertNotFound(UserId viewer) {
        assertThatThrownBy(() -> detail.execute(OpenTasks.TASK, viewer)).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.errorCode()).isEqualTo(ApprovalError.PLATFORM_APPROVAL_TASK_NOT_FOUND));
    }

    @Test
    @DisplayName("an open task is shown to the account of its initiator's employee, and not to the account that has the initiator's number")
    void openTaskIsShownToTheInitiatorsAccount() {
        when(engine.findTask(OpenTasks.TASK)).thenReturn(Optional.of(OpenTasks.claimedBy(INITIATOR, HANDLER)));
        when(tasks.find(OpenTasks.TASK)).thenReturn(Optional.of(record(ApprovalTaskStatus.CLAIMED)));

        assertThat(detail.execute(OpenTasks.TASK, INITIATORS_ACCOUNT).taskId()).isEqualTo(OpenTasks.TASK);
        assertThat(detail.execute(OpenTasks.TASK, HANDLER).canAct()).isTrue();
        assertNotFound(ACCOUNT_NUMBERED_LIKE_THE_INITIATOR);
    }

    @Test
    @DisplayName("a decided task is shown to the account of its initiator's employee, and not to the account that has the initiator's number")
    void decidedTaskIsShownToTheInitiatorsAccount() {
        when(engine.findTask(OpenTasks.TASK)).thenReturn(Optional.empty());
        when(tasks.find(OpenTasks.TASK)).thenReturn(Optional.of(record(ApprovalTaskStatus.COMPLETED)));

        assertThat(detail.execute(OpenTasks.TASK, INITIATORS_ACCOUNT).taskId()).isEqualTo(OpenTasks.TASK);
        assertNotFound(ACCOUNT_NUMBERED_LIKE_THE_INITIATOR);
    }
}
