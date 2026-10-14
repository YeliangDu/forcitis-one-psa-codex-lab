package com.forcitis.one.organization.workforce.api;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.WecomUserId;
import java.util.List;
import java.util.Optional;

/** Read side of the organisation snapshot. Identity and approval resolve people only through this contract. */
public interface OrgDirectory {

    Optional<EmployeeProfile> findEmployee(UserId userId);

    /** Exact match on the A8 WeCom user id (RULE-322/324); falls back to a single-person A8 lookup when unknown. */
    Optional<EmployeeProfile> findEmployeeByWecomUserId(WecomUserId wecomUserId);

    /** Person-level direct leader (RULE-372); empty when none or not employed. */
    Optional<UserId> findDirectLeader(UserId userId);

    /** Org unit head from A8 (RULE-356); empty when none. */
    Optional<UserId> findOrgUnitHead(OrgUnitId orgUnitId);

    /** Regional receivables manager (老系统「区域 AR」) from A8 (RULE-255/356); empty when none. */
    Optional<UserId> findRegionalReceivablesManager(OrgUnitId orgUnitId);

    Optional<OrgUnitProfile> findOrgUnit(OrgUnitId orgUnitId);

    /** Ancestors of the org unit, nearest first, excluding the unit itself. */
    List<OrgUnitId> findAncestors(OrgUnitId orgUnitId);
}
