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
import com.forcitis.one.kernel.OrgUnitAccess;
import com.forcitis.one.organization.workforce.domain.OrgSnapshotQueries;
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
    final OrgUnitAccess unitAccess = mock(OrgUnitAccess.class);
    final OrgSnapshotQueries snapshots = mock(OrgSnapshotQueries.class);
    final EmployeeProfileService profiles = mock(EmployeeProfileService.class);
    final ProfileChangeRequestService service = new ProfileChangeRequestService(requests, employees, audit, currentUser, accountEmployees, access, unitAccess, snapshots, profiles,
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

    @Test
    @DisplayName("#730 S4: a caller who sees everything gets a pending request of an employee with no current assignment too; a caller of some units does not")
    void everythingIncludesAnEmployeeWithoutAssignment() {
        ProfileChangeRequest pending = ProfileChangeRequest.restore(ID, EMPLOYEE, REQUESTER, AT, new PersonalProfile("Synthetic name", null, null, new WecomUserId("old")),
                new PersonalProfile("Synthetic name", null, null, new WecomUserId("new")), ProfileChangeStatus.PENDING, 0, null, null, null, null);
        when(requests.pending(null, 51)).thenReturn(java.util.List.of(pending));
        when(snapshots.findStaff(EMPLOYEE)).thenReturn(Optional.empty());

        when(unitAccess.visibleOrgUnits(PersonnelCommandService.PERMISSION)).thenReturn(OrgUnitAccess.Visible.all());
        org.assertj.core.api.Assertions.assertThat(service.pending(com.forcitis.one.kernel.PageRequest.of(50, null, "profile-change-requests")).items()).hasSize(1);

        when(unitAccess.visibleOrgUnits(PersonnelCommandService.PERMISSION)).thenReturn(OrgUnitAccess.Visible.nothing());
        org.assertj.core.api.Assertions.assertThat(service.pending(com.forcitis.one.kernel.PageRequest.of(50, null, "profile-change-requests")).items()).isEmpty();
    }

    @Test
    @DisplayName("#730 S4: a cursor naming a request outside the scope or none at all answers the same empty page")
    void aForgedCursorCannotProbeIds() {
        when(unitAccess.visibleOrgUnits(PersonnelCommandService.PERMISSION)).thenReturn(OrgUnitAccess.Visible.nothing());
        when(snapshots.findAllOrgUnits()).thenReturn(java.util.List.of());
        ProfileChangeRequest existing = decided(ProfileChangeStatus.REJECTED);
        when(requests.find(ID)).thenReturn(Optional.of(existing));
        when(snapshots.findStaff(EMPLOYEE)).thenReturn(Optional.empty());
        UUID unknown = UUID.fromString("019a0000-0000-7000-8000-0000000000c9");
        when(requests.find(new ProfileChangeRequestId(unknown))).thenReturn(Optional.empty());

        var known = service.pending(com.forcitis.one.kernel.PageRequest.of(50, com.forcitis.one.kernel.PageRequest.encode("profile-change-requests", ID.value().toString()), "profile-change-requests"));
        var missing = service.pending(com.forcitis.one.kernel.PageRequest.of(50, com.forcitis.one.kernel.PageRequest.encode("profile-change-requests", unknown.toString()), "profile-change-requests"));

        org.assertj.core.api.Assertions.assertThat(known.items()).isEmpty();
        org.assertj.core.api.Assertions.assertThat(missing.items()).isEmpty();
        org.assertj.core.api.Assertions.assertThat(known.nextCursor()).isEqualTo(missing.nextCursor());
        org.mockito.Mockito.verify(requests, org.mockito.Mockito.never()).pending(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt());
    }

    private ProfileChangeRequest pendingOfEmployee() {
        ProfileChangeRequest request = ProfileChangeRequest.restore(ID, EMPLOYEE, REQUESTER, AT, new PersonalProfile("Synthetic name", null, null, new WecomUserId("old")),
                new PersonalProfile("Synthetic name", null, null, new WecomUserId("new")), ProfileChangeStatus.PENDING, 3, null, null, null, null);
        when(requests.find(ID)).thenReturn(Optional.of(request));
        when(employees.lockEmployee(EMPLOYEE)).thenReturn(true);
        when(currentUser.userId()).thenReturn(THIRD);
        return request;
    }

    private void assertNotFoundWithoutTouchingAnything(String what, org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).as(what).isInstanceOfSatisfying(DomainException.class,
                refused -> assertThat(refused.errorCode()).isEqualTo(WorkforceError.ORGANIZATION_WORKFORCE_PROFILE_CHANGE_REQUEST_NOT_FOUND));
        verify(employees, org.mockito.Mockito.never()).lockEmployee(any());
        verify(employees, org.mockito.Mockito.never()).updateProfile(any(), any(), any());
        verifyNoInteractions(access, audit, profiles);
    }

    @Test
    @DisplayName("#1153: a request outside the caller's scope answers the same not found as an unknown id, also with a stale version, and nothing is locked or written")
    void anOutOfScopeRequestIsNotFoundBeforeTheVersionCheck() {
        pendingOfEmployee();
        when(unitAccess.visibleOrgUnits(PersonnelCommandService.PERMISSION)).thenReturn(OrgUnitAccess.Visible.nothing());
        when(snapshots.findAllOrgUnits()).thenReturn(java.util.List.of());
        when(snapshots.findStaff(EMPLOYEE)).thenReturn(Optional.empty());

        assertNotFoundWithoutTouchingAnything("confirm, stale version", () -> service.confirm(ID, ExpectedVersion.of(0)));
        assertNotFoundWithoutTouchingAnything("reject, stale version", () -> service.reject(ID, "synthetic", ExpectedVersion.of(0)));
        assertNotFoundWithoutTouchingAnything("confirm, current version", () -> service.confirm(ID, ExpectedVersion.of(3)));
    }

    @Test
    @DisplayName("#1153: a scope that cannot be worked out shows nothing, so the id answers like an unknown one (security-invariants I-10)")
    void anUnknownScopeIsNotFound() {
        pendingOfEmployee();
        // any FORBIDDEN refusal stands for platform.identity's PLATFORM_IDENTITY_ACCESS_SCOPE_UNAVAILABLE, which a workforce test does not import
        when(unitAccess.visibleOrgUnits(PersonnelCommandService.PERMISSION)).thenThrow(
                new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_PROFILE_CHANGE_CONFIRMER_NOT_ALLOWED, com.forcitis.one.kernel.ErrorKind.FORBIDDEN, "scope unavailable"));

        assertNotFoundWithoutTouchingAnything("confirm", () -> service.confirm(ID, ExpectedVersion.of(0)));
        assertNotFoundWithoutTouchingAnything("reject", () -> service.reject(ID, "synthetic", ExpectedVersion.of(0)));
    }
}
