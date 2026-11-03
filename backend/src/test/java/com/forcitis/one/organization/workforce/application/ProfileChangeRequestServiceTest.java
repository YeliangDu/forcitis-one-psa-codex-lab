package com.forcitis.one.organization.workforce.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.forcitis.one.fixture.doubles.TestAccounts;
import com.forcitis.one.kernel.AccountEmployees;
import com.forcitis.one.kernel.CurrentUser;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.EmployeeAccountAccess;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.ExpectedVersion;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.WecomUserId;
import com.forcitis.one.organization.workforce.api.ProfileChangeRequestId;
import com.forcitis.one.organization.workforce.api.ProfileChangeStatus;
import com.forcitis.one.organization.workforce.api.ProfileVoidReason;
import com.forcitis.one.organization.workforce.api.WorkforceError;
import com.forcitis.one.organization.workforce.domain.PersonalProfile;
import com.forcitis.one.organization.workforce.domain.PersonnelCommandRepository;
import com.forcitis.one.organization.workforce.domain.ProfileChangeRequest;
import com.forcitis.one.organization.workforce.domain.ProfileChangeRequestRepository;
import com.forcitis.one.platform.audit.api.AuditLog;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("#980 ProfileChangeRequestService: a decided request takes no further decision; nothing is written before that is known")
class ProfileChangeRequestServiceTest {
    static final Instant AT = Instant.parse("2026-11-03T08:00:00Z");
    static final ProfileChangeRequestId ID = new ProfileChangeRequestId(UUID.fromString("019a0000-0000-7000-8000-0000000000c1"));
    static final EmployeeId EMPLOYEE = new EmployeeId(UUID.fromString("019a0000-0000-7000-8000-000000000e01"));
    static final UserId REQUESTER = TestAccounts.account(1);
    static final UserId DECIDER = TestAccounts.account(2);
    static final UserId THIRD = TestAccounts.account(3);

    final ProfileChangeRequestRepository requests = mock(ProfileChangeRequestRepository.class);
    final PersonnelCommandRepository employees = mock(PersonnelCommandRepository.class);
    final AuditLog audit = mock(AuditLog.class);
    final CurrentUser currentUser = mock(CurrentUser.class);
    final AccountEmployees accountEmployees = mock(AccountEmployees.class);
    final EmployeeAccountAccess access = mock(EmployeeAccountAccess.class);
    final EmployeeProfileService profiles = mock(EmployeeProfileService.class);
    final ProfileChangeRequestService service = new ProfileChangeRequestService(requests, employees, audit, currentUser, accountEmployees, access, profiles,
            Clock.fixed(AT.plusSeconds(60), ZoneOffset.UTC));

    private ProfileChangeRequest decided(ProfileChangeStatus status) {
        PersonalProfile base = new PersonalProfile("Synthetic name", null, null, new WecomUserId("old"));
        PersonalProfile after = new PersonalProfile("Synthetic name", null, null, new WecomUserId("new"));
        boolean rejected = status == ProfileChangeStatus.REJECTED;
        ProfileChangeRequest request = ProfileChangeRequest.restore(ID, EMPLOYEE, REQUESTER, AT, base, after, status, 1, DECIDER, AT.plusSeconds(5),
                status == ProfileChangeStatus.VOIDED ? ProfileVoidReason.BASE_CHANGED : null, rejected ? "synthetic" : null);
        when(requests.find(ID)).thenReturn(Optional.of(request));
        when(employees.lockEmployee(EMPLOYEE)).thenReturn(true);
        when(currentUser.userId()).thenReturn(THIRD);
        return request;
    }

    @Test
    void aDecidedRequestAnswersNotPendingBeforeAnythingIsWritten() {
        for (ProfileChangeStatus status : new ProfileChangeStatus[] {ProfileChangeStatus.CONFIRMED, ProfileChangeStatus.REJECTED, ProfileChangeStatus.VOIDED}) {
            decided(status);

            assertThatThrownBy(() -> service.confirm(ID, ExpectedVersion.of(1))).isInstanceOfSatisfying(DomainException.class,
                    refused -> assertThat(refused.errorCode()).as("confirm " + status).isEqualTo(WorkforceError.ORGANIZATION_WORKFORCE_PROFILE_CHANGE_REQUEST_NOT_PENDING));
            assertThatThrownBy(() -> service.reject(ID, "synthetic: late", ExpectedVersion.of(1))).isInstanceOfSatisfying(DomainException.class,
                    refused -> assertThat(refused.errorCode()).as("reject " + status).isEqualTo(WorkforceError.ORGANIZATION_WORKFORCE_PROFILE_CHANGE_REQUEST_NOT_PENDING));
        }
        verifyNoInteractions(access, audit, profiles);
        verify(employees, org.mockito.Mockito.never()).updateProfile(any(), any(), any());
        verify(requests, org.mockito.Mockito.never()).update(any(), org.mockito.ArgumentMatchers.anyLong(), any());
    }

    @Test
    void aVersionThatIsNotTheRequestsAnswersBeforeThePendingCheck() {
        decided(ProfileChangeStatus.CONFIRMED);

        assertThatThrownBy(() -> service.confirm(ID, ExpectedVersion.of(0))).isInstanceOf(com.forcitis.one.kernel.VersionMismatchException.class);
    }

    @Test
    void aMissingRequestOrEmployeeAnswersNotFound() {
        when(requests.find(ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.confirm(ID, ExpectedVersion.of(0))).isInstanceOfSatisfying(DomainException.class,
                refused -> assertThat(refused.errorCode()).isEqualTo(WorkforceError.ORGANIZATION_WORKFORCE_PROFILE_CHANGE_REQUEST_NOT_FOUND));
        verifyNoMoreInteractions(access, audit);
    }
}
