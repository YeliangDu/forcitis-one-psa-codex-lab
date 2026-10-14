package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.WecomUserId;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Port of the stored snapshot. Reads are single statements; writes must run inside the caller's transaction. */
public interface OrgSnapshotRepository {

    /** Serialises writers (scheduled run, manual release): held until the surrounding transaction ends. */
    void lockForWrite();

    OrgSnapshot loadAll();

    /** Upserts exactly the changed rows of the reconciliation, in an order that never trips the unique keys. */
    void save(Reconciliation changes, SyncRunId syncRunId, Instant now);

    /** Writes one person found by the login-miss lookup. */
    void saveStaffMember(StaffMember member, SyncRunId syncRunId, Instant now);

    Optional<StaffMember> findStaff(UserId userId);

    /** Prefers the employed person; empty when the id is unknown. */
    Optional<StaffMember> findStaffByWecomUserId(WecomUserId wecomUserId);

    /** Exact login name; only employed people carry one (RULE-363). Empty when unknown. */
    Optional<StaffMember> findStaffByLoginName(String loginName);

    Optional<OrgUnit> findOrgUnit(OrgUnitId orgUnitId);

    List<OrgUnit> findAllOrgUnits();

    /** Ancestors, nearest first, excluding the unit itself. */
    List<OrgUnitId> findAncestors(OrgUnitId orgUnitId);

    Set<OrgUnitId> findOrgUnitIds();

    Set<PositionId> findPositionIds();

    /** Sets the short name of every listed unit that differs; units not listed are left alone. @return units changed */
    int updateShortNames(Map<OrgUnitId, String> shortNames, Instant now);
}
