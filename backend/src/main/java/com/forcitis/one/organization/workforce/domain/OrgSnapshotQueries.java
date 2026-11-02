package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.WecomUserId;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import java.util.List;
import java.util.Optional;

/**
 * Reading side of the org snapshot: lookups and browsing, nothing is loaded for a change. The staff lookups answer as of the business day: a person with
 * employee versions is read from the versions in force, anyone else from the snapshot (#958).
 */
public interface OrgSnapshotQueries {
    Optional<StaffEmployee> findStaff(EmployeeId employeeId);

    /** The employees row alone: no assignment is joined, unlike findStaff. */
    boolean employeeExists(EmployeeId employeeId);

    /** Prefers the employed person. */
    Optional<StaffEmployee> findStaffByWecomUser(WecomUserId wecomUserId);

    /** Only employed people carry a login name. */
    Optional<StaffEmployee> findStaffByLoginName(String loginName);

    /** Every employee, employed or not, whose login name it is. */
    List<EmployeeId> findEmployeeIdsByLoginName(String loginName);

    /** Every employee, employed or not, whose WeCom user id it is. */
    List<EmployeeId> findEmployeeIdsByWecomUser(WecomUserId wecomUserId);

    Optional<OrgUnit> findOrgUnit(OrgUnitId orgUnitId);

    List<OrgUnit> findAllOrgUnits();

    List<OrgUnitId> findAncestors(OrgUnitId orgUnitId);
}
