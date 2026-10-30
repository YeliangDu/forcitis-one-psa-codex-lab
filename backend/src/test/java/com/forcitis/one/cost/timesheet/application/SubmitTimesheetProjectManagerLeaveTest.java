package com.forcitis.one.cost.timesheet.application;

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
import com.forcitis.one.cost.timesheet.domain.TimesheetRepository;
import com.forcitis.one.fixture.doubles.TestAccounts;
import com.forcitis.one.fixture.doubles.TestOrgIds;
import com.forcitis.one.kernel.BusinessZone;
import com.forcitis.one.kernel.CurrentUser;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.DocumentTypeCode;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.Hours;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.DomainEvents;
import com.forcitis.one.organization.workforce.api.EmployeeLegalEntities;
import com.forcitis.one.organization.workforce.api.EmployeeProfile;
import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.platform.approval.api.ApprovalProcesses;
import com.forcitis.one.platform.numbering.api.DocumentNumber;
import com.forcitis.one.platform.numbering.api.DocumentNumbers;
import com.forcitis.one.platform.numbering.api.IssuedNumber;
import com.forcitis.one.platform.numbering.api.NumberingCounterId;
import com.forcitis.one.platform.numbering.api.NumberingRuleVersionId;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** IMP-1030-PAR-B5c (#926, user decision 2026-10-30 「提交就拒绝」): a timesheet whose project manager is on unpaid leave on the work date is refused at submission. */
class SubmitTimesheetProjectManagerLeaveTest {
    static final UserId ACCOUNT = TestAccounts.account(6001);
    static final EmployeeId EMPLOYEE = TestAccounts.employee(1001);
    static final EmployeeId MANAGER = TestAccounts.employee(40);
    static final TimesheetId ID = new TimesheetId(TestIds.uuid(7));
    static final LocalDate DAY = LocalDate.of(2026, 10, 13);

    final OrgDirectory directory = mock(OrgDirectory.class);
    final TimesheetRepository timesheets = mock(TimesheetRepository.class);
    final SubmitTimesheet submit;
    final LegalEntityId legalEntity = new LegalEntityId(TestIds.uuid(1));

    SubmitTimesheetProjectManagerLeaveTest() {
        CurrentUser currentUser = mock(CurrentUser.class);
        when(currentUser.userId()).thenReturn(ACCOUNT);
        when(currentUser.employeeId()).thenReturn(EMPLOYEE);
        when(directory.findEmployee(EMPLOYEE)).thenReturn(Optional.of(new EmployeeProfile(EMPLOYEE, "me", TestOrgIds.orgUnit(5), null, EmploymentStatus.EMPLOYED, false)));
        when(directory.findEmployee(MANAGER)).thenReturn(Optional.of(new EmployeeProfile(MANAGER, "manager", TestOrgIds.orgUnit(5), null, EmploymentStatus.EMPLOYED, false)));
        when(timesheets.findForUpdate(ID)).thenReturn(Optional.empty());
        EmployeeLegalEntities legalEntities = mock(EmployeeLegalEntities.class);
        DocumentNumbers numbers = mock(DocumentNumbers.class);
        when(legalEntities.on(eq(EMPLOYEE), any())).thenReturn(legalEntity);
        when(numbers.next(eq(new DocumentTypeCode("TIMESHEET")), eq(legalEntity), any())).thenReturn(new IssuedNumber(new DocumentNumber("TS-ABC-202610-0007"), new NumberingCounterId(TestIds.uuid(11)), new NumberingRuleVersionId(TestIds.uuid(12))));
        submit = new SubmitTimesheet(mock(ApprovalProcesses.class), currentUser, directory, legalEntities, numbers, timesheets,
                mock(DomainEvents.class), Clock.fixed(Instant.parse("2026-10-14T02:00:00Z"), ZoneOffset.UTC), new BusinessZone(ZoneId.of("Asia/Shanghai")));
    }

    @Test
    @DisplayName("IMP-1030-PAR-B5c a project manager on unpaid leave on the work date: the timesheet is refused with its own code and nothing is saved")
    void aManagerOnLeaveIsRefused() {
        when(directory.isOnUnpaidLeave(MANAGER, DAY)).thenReturn(true);

        assertThatThrownBy(() -> submit.execute(new SubmitTimesheetCommand(ID, DAY, Hours.of("8"), MANAGER)))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.errorCode()).isEqualTo(TimesheetError.COST_TIMESHEET_PROJECT_MANAGER_ON_UNPAID_LEAVE));
        verify(timesheets, never()).save(any());
    }

    @Test
    @DisplayName("IMP-1030-PAR-B5c the check is by the work date: a manager on leave on another day does not stop this one (before the leave starts, after it ends)")
    void theLeaveIsReadForTheWorkDate() {
        when(directory.isOnUnpaidLeave(MANAGER, DAY.plusDays(5))).thenReturn(true);

        submit.execute(new SubmitTimesheetCommand(ID, DAY, Hours.of("8"), MANAGER));

        verify(timesheets).save(any());
        verify(directory).isOnUnpaidLeave(MANAGER, DAY);
    }
}
