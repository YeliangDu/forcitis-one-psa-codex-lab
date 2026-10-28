package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.organization.workforce.api.PositionId;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.organization.workforce.api.SyncRunId;
import com.forcitis.one.kernel.WecomUserId;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Writing side: writes must run inside the caller's transaction. Reading for browsing and lookups is OrgSnapshotQueries. */
public interface OrgSnapshotRepository {
    /** Serialises writers; held until the surrounding transaction ends. */
    void lockForWrite();

    OrgSnapshot loadAll();

    /** The order never trips the unique keys. */
    /** snapshotDate is the day the history rows take effect from: the day the source was read. */
    void save(Reconciliation changes, SyncRunId syncRunId, Instant now, LocalDate snapshotDate);

    void saveRegisteredEmployee(RegisteredEmployee registered, Instant now);

    long findEmployeeEventSequence(EmployeeId employeeId);

    Set<OrgUnitId> findOrgUnitIds();

    Set<PositionId> findPositionIds();

    int updateShortNames(Map<OrgUnitId, String> shortNames, Instant now);
}
