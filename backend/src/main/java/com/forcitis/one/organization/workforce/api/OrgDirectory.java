package com.forcitis.one.organization.workforce.api;

import com.forcitis.one.kernel.EmployeeId;
import java.util.List;
import java.util.Optional;

public interface OrgDirectory {
    Optional<EmployeeProfile> findEmployee(EmployeeId employeeId);

    /** Falls back to a single-person source lookup when unknown; empty when nobody is found or the source is unavailable. */
    Optional<EmployeeProfile> findEmployeeByLogin(LoginLookup lookup);

    /** What the snapshot can say about the employment of one employee, and whether it can be trusted to say it. */
    EmploymentEvidence employmentOf(EmployeeId employeeId);

    /** Empty when none or not employed. */
    Optional<EmployeeId> findSupervisor(EmployeeId employeeId);

    Optional<EmployeeId> findOrgUnitHead(OrgUnitId orgUnitId);

    /** Regional receivables manager (old system 区域 AR). */
    Optional<EmployeeId> findRegionalReceivablesManager(OrgUnitId orgUnitId);

    Optional<OrgUnitProfile> findOrgUnit(OrgUnitId orgUnitId);

    List<OrgUnitId> findAncestors(OrgUnitId orgUnitId);
}
