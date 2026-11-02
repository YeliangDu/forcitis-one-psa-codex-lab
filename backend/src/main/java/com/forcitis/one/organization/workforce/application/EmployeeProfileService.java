package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.AccountEmployees;
import com.forcitis.one.kernel.BusinessZone;
import com.forcitis.one.kernel.CurrentUser;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.EmployeeAccountAccess;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.ChangeEmployeeProfile;
import com.forcitis.one.organization.workforce.api.EmployeeProfiles;
import com.forcitis.one.organization.workforce.api.WorkforceError;
import com.forcitis.one.organization.workforce.domain.PersonalProfile;
import com.forcitis.one.organization.workforce.domain.PersonnelCommandRepository;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.audit.api.AuditLog;
import com.forcitis.one.platform.audit.api.AuditSubject;
import com.forcitis.one.platform.audit.api.AuditSubjectKind;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The current profile of an employee is changed in place (W2 section 3.1 items 3 and 4): the root row takes the new values at once, nothing is versioned, and
 * the audit entry holds the old and new values of the columns that changed. The employee code has no way in. The same permission as the personnel commands.
 */
@Component
class EmployeeProfileService implements EmployeeProfiles {
    static final String PERMISSION = PersonnelCommandService.PERMISSION;

    private final PersonnelCommandRepository employees;
    private final AuditLog audit;
    private final CurrentUser currentUser;
    private final AccountEmployees accountEmployees;
    private final EmployeeAccountAccess access;
    private final Clock clock;
    private final BusinessZone zone;

    EmployeeProfileService(PersonnelCommandRepository employees, AuditLog audit, CurrentUser currentUser, AccountEmployees accountEmployees, EmployeeAccountAccess access,
                           Clock clock, BusinessZone zone) {
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
    public void change(ChangeEmployeeProfile request) {
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
        PersonalProfile after = new PersonalProfile(request.displayName(), request.gender(), request.email(), request.wecomUserId());
        PersonalProfile before = employees.profileOf(request.employeeId()).orElseThrow();
        PersonalProfile.Change change = after.changeFrom(before).orElse(null);
        if (change == null) {
            return;
        }
        if (!AuditEntry.acceptsText(change.beforeText()) || !AuditEntry.acceptsText(change.afterText())) {
            throw new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_EMPLOYEE_PROFILE_INVALID, ErrorKind.UNPROCESSABLE, "the profile text is too long or looks like a secret");
        }
        boolean wecomChanged = !Objects.equals(after.wecomUserId(), before.wecomUserId());
        // The WeCom user id is the login binding: moving or clearing one that is set, on an account with a role beyond employee, needs a second person (a later card); until then it is refused.
        if (wecomChanged && before.wecomUserId() != null && !access.holdsOnlyEmployeeRole(request.employeeId())) {
            throw new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_WECOM_USER_ID_CHANGE_NOT_ALLOWED, ErrorKind.FORBIDDEN,
                    "the WeCom user id of an account with a role beyond employee is not changed by one person");
        }
        if (after.wecomUserId() != null && wecomChanged && employees.wecomUserIdHeld(after.wecomUserId(), request.employeeId())) {
            throw new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_WECOM_USER_ID_TAKEN, ErrorKind.UNPROCESSABLE, "the WeCom user id belongs to another employee");
        }
        employees.updateProfile(request.employeeId(), after, now);
        if (wecomChanged) {
            access.revokeSessions(request.employeeId());
        }
        audit.record(new AuditEntry("organization.workforce.employee-profile.changed", by, null, AuditSubject.of(AuditSubjectKind.EMPLOYEE, request.employeeId().value()), "profile changed")
                .withChange("profile change", change.beforeText(), change.afterText()));
    }
}
