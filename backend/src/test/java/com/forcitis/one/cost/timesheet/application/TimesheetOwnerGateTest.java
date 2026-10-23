package com.forcitis.one.cost.timesheet.application;

import com.forcitis.one.cost.timesheet.api.TimesheetApprovalProcess;
import com.forcitis.one.fixture.doubles.TestAccounts;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.forcitis.one.TestIds;
import com.forcitis.one.cost.timesheet.api.TimesheetError;
import com.forcitis.one.cost.timesheet.api.TimesheetId;
import com.forcitis.one.cost.timesheet.api.TimesheetStatus;
import com.forcitis.one.cost.timesheet.domain.Timesheet;
import com.forcitis.one.cost.timesheet.domain.TimesheetRepository;
import com.forcitis.one.kernel.BusinessZone;
import com.forcitis.one.kernel.DocumentTypeCode;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.Hours;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.DomainEvents;
import com.forcitis.one.organization.workforce.api.EmployeeLegalEntities;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.platform.approval.api.ApprovalProcesses;
import com.forcitis.one.platform.approval.api.RetractionResult;
import com.forcitis.one.platform.approval.api.WithdrawCheck;
import com.forcitis.one.platform.identity.api.CurrentUser;
import com.forcitis.one.platform.numbering.api.DocumentNumber;
import com.forcitis.one.platform.numbering.api.DocumentNumbers;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.OptionalLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.forcitis.one.fixture.doubles.TestOrgIds;
import com.forcitis.one.platform.numbering.api.IssuedNumber;
import com.forcitis.one.platform.numbering.api.NumberingCounterId;
import com.forcitis.one.platform.numbering.api.NumberingRuleVersionId;

/**
 * "whose timesheet is it" is answered on the employee side. The caller's account and employee carry different
 * numbers here, and the foreign timesheet belongs to the employee whose number is the caller's account number: a check that set the
 * timesheet's employee against the account would let that one through.
 */
class TimesheetOwnerGateTest {
    static final UserId ACCOUNT = TestAccounts.account(6001);
    static final EmployeeId EMPLOYEE = TestAccounts.employee(1001);
    static final EmployeeId OWNER_OF_THE_ACCOUNT_NUMBER = TestAccounts.employee(6001);
    static final TimesheetId ID = new TimesheetId(TestIds.uuid(7));
    static final LocalDate DAY = LocalDate.of(2026, 10, 13);
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-14T02:00:00Z"), ZoneOffset.UTC);

    final CurrentUser currentUser = mock(CurrentUser.class);
    final TimesheetRepository timesheets = mock(TimesheetRepository.class);
    final ApprovalProcesses approvals = mock(ApprovalProcesses.class);

    TimesheetOwnerGateTest() {
        when(currentUser.userId()).thenReturn(ACCOUNT);
        when(currentUser.employeeId()).thenReturn(EMPLOYEE);
    }

    static Timesheet submittedBy(EmployeeId employee) {
        return Timesheet.restore(ID, new DocumentNumber("TS-ABC-202610-0007"), null, employee, new LegalEntityId(TestIds.uuid(1)), TestOrgIds.orgUnit(5), DAY,
                Hours.of("8"), TestAccounts.employee(40), TimesheetStatus.SUBMITTED, 1, DAY, 3, 2);
    }

    static void refused(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.errorCode()).isEqualTo(TimesheetError.COST_TIMESHEET_NOT_FOUND));
    }

    @Test
    @DisplayName("GetTimesheet shows the timesheet of the signed-in employee and refuses one that belongs to the employee numbered like the account")
    void readingIsOwnedByTheEmployee() {
        GetTimesheet get = new GetTimesheet(timesheets, approvals, currentUser);
        when(approvals.checkWithdraw(any(), any(), any())).thenReturn(WithdrawCheck.ALLOWED);

        when(timesheets.find(ID)).thenReturn(Optional.of(submittedBy(OWNER_OF_THE_ACCOUNT_NUMBER)));
        refused(() -> get.execute(ID));

        when(timesheets.find(ID)).thenReturn(Optional.of(submittedBy(EMPLOYEE)));
        assertThat(get.execute(ID).canWithdraw()).isTrue();
        verify(approvals).checkWithdraw(eq(TimesheetApprovalProcess.KEY), any(), eq(ACCOUNT));
    }

    @Test
    @DisplayName("CancelTimesheet cancels the approval of the signed-in employee's timesheet as the account, and refuses the other one before asking the approval")
    void cancellingIsOwnedByTheEmployee() {
        CancelTimesheet cancel = new CancelTimesheet(timesheets, approvals, currentUser);
        when(approvals.cancel(any(), any(), any(), any())).thenReturn(RetractionResult.DONE);

        when(timesheets.find(ID)).thenReturn(Optional.of(submittedBy(OWNER_OF_THE_ACCOUNT_NUMBER)));
        refused(() -> cancel.execute(ID, "wrong day", OptionalLong.empty()));
        verify(approvals, never()).cancel(any(), any(), any(), any());

        when(timesheets.find(ID)).thenReturn(Optional.of(submittedBy(EMPLOYEE)));
        cancel.execute(ID, "wrong day", OptionalLong.empty());
        verify(approvals).cancel(eq(TimesheetApprovalProcess.KEY), any(), eq(ACCOUNT), eq("wrong day"));
    }

    @Test
    @DisplayName("WithdrawTimesheet withdraws the approval of the signed-in employee's timesheet as the account, and refuses the other one before asking the approval")
    void withdrawingIsOwnedByTheEmployee() {
        WithdrawTimesheet withdraw = new WithdrawTimesheet(timesheets, approvals, currentUser);
        when(approvals.withdraw(any(), any(), any(), any())).thenReturn(RetractionResult.DONE);

        when(timesheets.find(ID)).thenReturn(Optional.of(submittedBy(OWNER_OF_THE_ACCOUNT_NUMBER)));
        refused(() -> withdraw.execute(ID, "wrong day", OptionalLong.empty()));
        verify(approvals, never()).withdraw(any(), any(), any(), any());

        when(timesheets.find(ID)).thenReturn(Optional.of(submittedBy(EMPLOYEE)));
        withdraw.execute(ID, "wrong day", OptionalLong.empty());
        verify(approvals).withdraw(eq(TimesheetApprovalProcess.KEY), any(), eq(ACCOUNT), eq("wrong day"));
    }

    @Test
    @DisplayName("SubmitTimesheet takes up an existing timesheet only for the signed-in employee, and files a new one under the employee, not the account")
    void submittingIsOwnedByTheEmployee() {
        OrgDirectory directory = mock(OrgDirectory.class);
        EmployeeLegalEntities legalEntities = mock(EmployeeLegalEntities.class);
        DocumentNumbers numbers = mock(DocumentNumbers.class);
        DomainEvents events = mock(DomainEvents.class);
        SubmitTimesheet submit = new SubmitTimesheet(approvals, currentUser, directory, legalEntities, numbers, timesheets, events, CLOCK,
                new BusinessZone(ZoneId.of("Asia/Shanghai")));
        SubmitTimesheetCommand command = new SubmitTimesheetCommand(ID, DAY, Hours.of("8"), TestAccounts.employee(40));

        when(timesheets.findForUpdate(ID)).thenReturn(Optional.of(submittedBy(OWNER_OF_THE_ACCOUNT_NUMBER)));
        refused(() -> submit.execute(command));
        verify(timesheets, never()).save(any());

        var profile = new com.forcitis.one.organization.workforce.api.EmployeeProfile(EMPLOYEE, "me", TestOrgIds.orgUnit(5), null,
                com.forcitis.one.organization.workforce.api.EmploymentStatus.EMPLOYED, false);
        var manager = new com.forcitis.one.organization.workforce.api.EmployeeProfile(TestAccounts.employee(40), "manager", TestOrgIds.orgUnit(5), null,
                com.forcitis.one.organization.workforce.api.EmploymentStatus.EMPLOYED, false);
        when(timesheets.findForUpdate(ID)).thenReturn(Optional.empty());
        when(directory.findEmployee(EMPLOYEE)).thenReturn(Optional.of(profile));
        when(directory.findEmployee(TestAccounts.employee(40))).thenReturn(Optional.of(manager));
        LegalEntityId legalEntity = new LegalEntityId(TestIds.uuid(1));
        when(legalEntities.on(eq(EMPLOYEE), any())).thenReturn(legalEntity);
        when(numbers.next(eq(new DocumentTypeCode("TIMESHEET")), eq(legalEntity), any())).thenReturn(new IssuedNumber(new DocumentNumber("TS-ABC-202610-0007"), new NumberingCounterId(TestIds.uuid(11)), new NumberingRuleVersionId(TestIds.uuid(12))));

        submit.execute(command);

        org.mockito.ArgumentCaptor<Timesheet> saved = org.mockito.ArgumentCaptor.forClass(Timesheet.class);
        verify(timesheets).save(saved.capture());
        assertThat(saved.getValue().employee()).isEqualTo(EMPLOYEE);
    }
}
