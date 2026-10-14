package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.WecomUserId;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Writes must run inside the caller's transaction. */
public interface OrgSnapshotRepository {
    /** Serialises writers; held until the surrounding transaction ends. */
    void lockForWrite();

    OrgSnapshot loadAll();

    /** The order never trips the unique keys. */
    void save(Reconciliation changes, SyncRunId syncRunId, Instant now);

    void saveStaffEmployee(StaffEmployee member, SyncRunId syncRunId, Instant now);

    Optional<StaffEmployee> findStaff(UserId userId);

    /** Prefers the employed person. */
    Optional<StaffEmployee> findStaffByWecomUserId(WecomUserId wecomUserId);

    /** Only employed people carry a login name. */
    Optional<StaffEmployee> findStaffByLoginName(String loginName);

    Optional<OrgUnit> findOrgUnit(OrgUnitId orgUnitId);

    List<OrgUnit> findAllOrgUnits();

    List<OrgUnitId> findAncestors(OrgUnitId orgUnitId);

    Set<OrgUnitId> findOrgUnitIds();

    Set<PositionId> findPositionIds();

    int updateShortNames(Map<OrgUnitId, String> shortNames, Instant now);
}
