package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.AccountEmployees;
import com.forcitis.one.kernel.BusinessZone;
import com.forcitis.one.kernel.CurrentUser;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.EmployeeAccountAccess;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.IdGenerator;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.ChangeEmployeeProfile;
import com.forcitis.one.organization.workforce.api.EmployeeProfiles;
import com.forcitis.one.organization.workforce.api.ProfileChangeRequestId;
import com.forcitis.one.organization.workforce.api.WorkforceError;
import com.forcitis.one.organization.workforce.domain.PersonalProfile;
import com.forcitis.one.organization.workforce.domain.PersonnelCommandRepository;
import com.forcitis.one.organization.workforce.domain.ProfileChangeRequest;
import com.forcitis.one.organization.workforce.domain.ProfileChangeRequestRepository;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.audit.api.AuditLog;
import com.forcitis.one.platform.audit.api.AuditSubject;
import com.forcitis.one.platform.audit.api.AuditSubjectKind;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The current profile of an employee is changed in place (W2 section 3.1 items 3 and 4): the root row takes the new values at once, nothing is versioned, and
 * the audit entry holds the old and new values of the columns that changed. The employee code has no way in. The same permission as the personnel commands.
 * Moving or clearing a set WeCom user id on an account with a role beyond employee, and filling one in within 30 days after a clear a second person confirmed
 * (user answer Q2 a), writes nothing: the whole proposed profile waits for a second person (design note wecom-rebind-second-person.md).
 */
@Component
class EmployeeProfileService implements EmployeeProfiles {
    static final String PERMISSION = PersonnelCommandService.PERMISSION;
    static final int FILL_IN_WINDOW_DAYS = 30;

    private final PersonnelCommandRepository employees;
    private final AuditLog audit;
    private final CurrentUser currentUser;
    private final AccountEmployees accountEmployees;
    private final EmployeeAccountAccess access;
    private final ProfileChangeRequestRepository requests;
    private final IdGenerator ids;
    private final Clock clock;
    private final BusinessZone zone;

    EmployeeProfileService(PersonnelCommandRepository employees, AuditLog audit, CurrentUser currentUser, AccountEmployees accountEmployees, EmployeeAccountAccess access,
                           ProfileChangeRequestRepository requests, IdGenerator ids, Clock clock, BusinessZone zone) {
        this.requests = requests;
        this.ids = ids;
        this.accountEmployees = accountEmployees;
        this.access = access;
        this.employees = employees;
        this.audit = audit;
        this.currentUser = currentUser;
        this.clock = clock;
        this.zone = zone;
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('" + PERMISSION + "')")
    public Optional<ProfileChangeRequestId> change(ChangeEmployeeProfile request) {
        Instant now = clock.instant();
        LocalDate effective = request.effectiveDate();
        if (effective != null && effective.isAfter(zone.dateOf(now))) {
            throw new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_EMPLOYEE_PROFILE_INVALID, ErrorKind.UNPROCESSABLE,
                    "a profile change takes effect at once; a later effective date is refused");
        }
        UserId by = currentUser.userId();
        if (accountEmployees.employeeOf(by).filter(request.employeeId()::equals).isPresent()) {
            throw new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_OWN_PROFILE_CHANGE_FORBIDDEN, ErrorKind.FORBIDDEN, "nobody changes their own employee record");
        }
        if (!employees.lockEmployee(request.employeeId())) {
            throw new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_EMPLOYEE_NOT_FOUND, ErrorKind.NOT_FOUND, "no such employee");
        }
        if (requests.pendingFor(request.employeeId())) {
            throw new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_PROFILE_CHANGE_PENDING, ErrorKind.CONFLICT,
                    "a profile change of this employee waits for a second person; the requester withdraws it first");
        }
        PersonalProfile after = new PersonalProfile(request.displayName(), request.gender(), request.email(), request.wecomUserId());
        PersonalProfile before = employees.profileOf(request.employeeId()).orElseThrow();
        PersonalProfile.Change change = after.changeFrom(before).orElse(null);
        if (change == null) {
            return Optional.empty();
        }
        if (!AuditEntry.acceptsText(change.beforeText()) || !AuditEntry.acceptsText(change.afterText())) {
            throw new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_EMPLOYEE_PROFILE_INVALID, ErrorKind.UNPROCESSABLE, "the profile text is too long or looks like a secret");
        }
        boolean wecomChanged = !Objects.equals(after.wecomUserId(), before.wecomUserId());
        if (after.wecomUserId() != null && wecomChanged && employees.wecomUserIdHeld(after.wecomUserId(), request.employeeId())) {
            throw new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_WECOM_USER_ID_TAKEN, ErrorKind.UNPROCESSABLE, "the WeCom user id belongs to another employee");
        }
        if (needsSecondPerson(request.employeeId(), before, after, now)) {
            ProfileChangeRequest waiting = ProfileChangeRequest.open(new ProfileChangeRequestId(ids.next()), request.employeeId(), by, now, before, after);
            requests.insert(waiting, now);
            audit.record(new AuditEntry("organization.workforce.employee-profile.change-requested", by, null, AuditSubject.of(AuditSubjectKind.EMPLOYEE, request.employeeId().value()),
                    "profile change requested and waits for a second person (request " + waiting.id().value() + ")"));
            return Optional.of(waiting.id());
        }
        employees.updateProfile(request.employeeId(), after, now);
        if (wecomChanged) {
            access.revokeSessions(request.employeeId());
        }
        recordChanged(by, request.employeeId(), "profile changed", change);
        return Optional.empty();
    }

    /**
     * The audit entry of an applied profile change, with the old and new values of the columns that changed (row 26 of the audit content table; this class is the
     * only writer of it): for a change applied at once, and for one a second person confirmed, whose detail names the request.
     */
    void recordChanged(UserId by, EmployeeId employee, String detail, PersonalProfile.Change change) {
        audit.record(new AuditEntry("organization.workforce.employee-profile.changed", by, null, AuditSubject.of(AuditSubjectKind.EMPLOYEE, employee.value()), detail)
                .withChange("profile change", change.beforeText(), change.afterText()));
    }

    /**
     * The WeCom user id is the login binding. On an account with a role beyond employee, moving or clearing a set one needs a second person, and so does filling one
     * in while 30 days have not passed since the business day a second person confirmed a clear of it (the last day of the window is the 30th day after).
     */
    private boolean needsSecondPerson(EmployeeId employee, PersonalProfile before, PersonalProfile after, Instant now) {
        if (Objects.equals(after.wecomUserId(), before.wecomUserId()) || access.holdsOnlyEmployeeRole(employee)) {
            return false;
        }
        if (before.wecomUserId() != null) {
            return true;
        }
        return requests.lastConfirmedClearAt(employee).map(confirmed -> !zone.dateOf(now).isAfter(zone.dateOf(confirmed).plusDays(FILL_IN_WINDOW_DAYS))).orElse(false);
    }
}
