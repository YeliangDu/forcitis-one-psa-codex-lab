package com.forcitis.one.platform.approval.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.forcitis.one.TestIds;
import com.forcitis.one.cost.timesheet.api.TimesheetApprovalProcess;
import com.forcitis.one.fixture.doubles.TestAccounts;
import com.forcitis.one.kernel.DocumentId;
import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.kernel.DocumentTypeCode;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.api.ApprovalStatus;
import com.forcitis.one.platform.approval.api.ApprovalSummary;
import com.forcitis.one.platform.approval.api.RetractionResult;
import com.forcitis.one.platform.approval.api.WithdrawCheck;
import com.forcitis.one.platform.approval.domain.ApprovalEngine;
import com.forcitis.one.platform.approval.domain.ApprovalRequest;
import com.forcitis.one.platform.approval.domain.ApprovalRequestRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

/**
 * who may withdraw or cancel an approval is decided by setting the initiator, an employee, against the
 * employee behind the acting account. The initiator is employee 1001 whose account is 6001; the account numbered 1001 belongs to
 * somebody else, and must not pass for the initiator.
 */
class RetractionInitiatorGateTest {
    static final EmployeeId INITIATOR = TestAccounts.employee(1001);
    static final UserId INITIATORS_ACCOUNT = TestAccounts.account(1001);
    static final UserId ACCOUNT_NUMBERED_LIKE_THE_INITIATOR = TestAccounts.unknownAccount(1001);
    static final DocumentReference DOCUMENT = new DocumentReference(new DocumentTypeCode("TIMESHEET"), com.forcitis.one.TestApprovalIds.document("7"));

    final ApprovalRequestRepository requests = mock(ApprovalRequestRepository.class);
    final ApprovalEngine engine = mock(ApprovalEngine.class);
    final Approvals approvals = mock(Approvals.class);
    final DenialAudit denials = mock(DenialAudit.class);
    final BusinessTime time = mock(BusinessTime.class);
    final Retraction retraction = new Retraction(requests, engine, approvals, denials, time, TestAccounts.everyoneHasOne());
    final ApprovalRequest running = ApprovalRequest.restore(com.forcitis.one.TestApprovalIds.requestId(), com.forcitis.one.TestApprovalIds.generator(), TimesheetApprovalProcess.KEY, DOCUMENT, new LegalEntityId(TestIds.uuid(1)), INITIATOR, 1,
            com.forcitis.one.TestApprovalIds.instance("i-1"), ApprovalStatus.RUNNING, null, new ApprovalSummary("TS-1"), List.of(), 3, 2);

    RetractionInitiatorGateTest() {
        when(requests.find(TimesheetApprovalProcess.KEY, DOCUMENT)).thenReturn(Optional.of(running));
        when(requests.findForUpdate(TimesheetApprovalProcess.KEY, DOCUMENT)).thenReturn(Optional.of(running));
        when(time.moment()).thenReturn(new com.forcitis.one.platform.approval.domain.Moment(java.time.Instant.parse("2026-10-14T02:00:00Z"),
                java.time.LocalDate.of(2026, 10, 14)));
    }

    @Test
    @DisplayName("the check allows the account of the initiator's employee and answers NOT_INITIATOR for the account that merely has the initiator's number")
    void checkIsOnTheEmployeeSide() {
        assertThat(retraction.check(TimesheetApprovalProcess.KEY, DOCUMENT, INITIATORS_ACCOUNT)).isEqualTo(WithdrawCheck.ALLOWED);
        assertThat(retraction.check(TimesheetApprovalProcess.KEY, DOCUMENT, ACCOUNT_NUMBERED_LIKE_THE_INITIATOR))
                .isEqualTo(WithdrawCheck.NOT_INITIATOR);
    }

    @Test
    @DisplayName("withdrawing ends the round for the initiator's account; the account with the initiator's number is refused, audited, and changes nothing")
    void withdrawIsOnTheEmployeeSide() {
        assertThatThrownBy(() -> retraction.withdraw(TimesheetApprovalProcess.KEY, DOCUMENT, ACCOUNT_NUMBERED_LIKE_THE_INITIATOR, "wrong amount"))
                .isInstanceOf(AccessDeniedException.class);
        verify(denials).record(any());
        verify(engine, never()).endAsReturned(any());

        assertThat(retraction.withdraw(TimesheetApprovalProcess.KEY, DOCUMENT, INITIATORS_ACCOUNT, "wrong amount")).isEqualTo(RetractionResult.DONE);
        verify(engine).endAsReturned(com.forcitis.one.TestApprovalIds.instance("i-1"));
    }

    @Test
    @DisplayName("cancelling ends the approval for the initiator's account; the account with the initiator's number is refused and audited")
    void cancelIsOnTheEmployeeSide() {
        assertThatThrownBy(() -> retraction.cancel(TimesheetApprovalProcess.KEY, DOCUMENT, ACCOUNT_NUMBERED_LIKE_THE_INITIATOR, "not needed"))
                .isInstanceOf(AccessDeniedException.class);
        verify(denials).record(any());
        verify(engine, never()).terminate(any(), any());

        assertThat(retraction.cancel(TimesheetApprovalProcess.KEY, DOCUMENT, INITIATORS_ACCOUNT, "not needed")).isEqualTo(RetractionResult.DONE);
        verify(engine).terminate(com.forcitis.one.TestApprovalIds.instance("i-1"), "CANCELLED");
    }
}
