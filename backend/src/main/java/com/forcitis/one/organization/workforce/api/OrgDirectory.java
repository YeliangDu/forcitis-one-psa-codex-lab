package com.forcitis.one.organization.workforce.api;

import com.forcitis.one.kernel.EmployeeId;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface OrgDirectory {
    Optional<EmployeeProfile> findEmployee(EmployeeId employeeId);

    /** Falls back to a single-person source lookup when unknown; empty when nobody is found or the source is unavailable. */
    Optional<EmployeeProfile> findEmployeeByLogin(LoginLookup lookup);

    /** What the snapshot can say about the employment of one employee, and whether it can be trusted to say it. */
    EmploymentVerdict employmentOf(EmployeeId employeeId);

    /** True when the employee has an employed version flagged unpaid leave on that business day (ADR-0068 section 3); a person on leave is still employed, but counts neither as available nor as retained. */
    boolean isOnUnpaidLeave(EmployeeId employeeId, LocalDate day);

    /**
     * The employment status on that business day: for an employee with versions the status of the version in force that day, empty when none covers it;
     * for one without versions the current status of the snapshot (#958).
     */
    Optional<EmploymentStatus> employmentOn(EmployeeId employeeId, LocalDate day);

    /** Empty when none or not employed. */
    Optional<EmployeeId> findSupervisor(EmployeeId employeeId);

    Optional<EmployeeId> findOrgUnitHead(OrgUnitId orgUnitId);

    /** Regional receivables manager (old system 区域 AR). */
    Optional<EmployeeId> findRegionalReceivablesManager(OrgUnitId orgUnitId);

    Optional<OrgUnitProfile> findOrgUnit(OrgUnitId orgUnitId);

    List<OrgUnitId> findAncestors(OrgUnitId orgUnitId);
}
