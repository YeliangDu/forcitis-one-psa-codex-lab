package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.BusinessZone;
import com.forcitis.one.kernel.CurrentUser;
import com.forcitis.one.kernel.DomainException;
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
    private final Clock clock;
    private final BusinessZone zone;

    EmployeeProfileService(PersonnelCommandRepository employees, AuditLog audit, CurrentUser currentUser, Clock clock, BusinessZone zone) {
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
        if (!employees.lockEmployee(request.employeeId())) {
            throw new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_EMPLOYEE_NOT_FOUND, ErrorKind.NOT_FOUND, "no such employee");
        }
        PersonalProfile after = new PersonalProfile(request.displayName(), request.gender(), request.email(), request.wecomUserId());
        PersonalProfile before = employees.profileOf(request.employeeId()).orElseThrow();
        PersonalProfile.Change change = after.changeFrom(before).orElse(null);
        if (change == null) {
            return;
        }
        if (after.wecomUserId() != null && employees.wecomUserIdHeld(after.wecomUserId(), request.employeeId())) {
            throw new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_WECOM_USER_ID_TAKEN, ErrorKind.UNPROCESSABLE, "the WeCom user id belongs to another employee");
        }
        employees.updateProfile(request.employeeId(), after, now);
        audit.record(new AuditEntry("organization.workforce.employee-profile.changed", by, null, AuditSubject.of(AuditSubjectKind.EMPLOYEE, request.employeeId().value()), "profile changed")
                .withChange("profile change", change.beforeText(), change.afterText()));
    }
}
