package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.WecomUserId;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import java.util.List;
import java.util.Optional;

/** Reading side of the org snapshot: lookups and browsing, nothing is loaded for a change. */
public interface OrgSnapshotQueries {
    Optional<StaffEmployee> findStaff(UserId userId);

    /** Prefers the employed person. */
    Optional<StaffEmployee> findStaffByWecomUserId(WecomUserId wecomUserId);

    /** Only employed people carry a login name. */
    Optional<StaffEmployee> findStaffByLoginName(String loginName);

    Optional<OrgUnit> findOrgUnit(OrgUnitId orgUnitId);

    List<OrgUnit> findAllOrgUnits();

    List<OrgUnitId> findAncestors(OrgUnitId orgUnitId);
}
