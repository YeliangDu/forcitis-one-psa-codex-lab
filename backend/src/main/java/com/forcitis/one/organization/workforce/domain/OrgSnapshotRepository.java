package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.organization.workforce.api.PositionId;
import com.forcitis.one.kernel.EmployeeId;
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

    Set<OrgUnitId> findOrgUnitIds();

    Set<PositionId> findPositionIds();
}
