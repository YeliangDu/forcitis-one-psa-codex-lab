package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.organization.workforce.api.SyncRunId;
import com.forcitis.one.organization.workforce.domain.SyncRunStatus;
import java.util.List;

/** separated are the employees this run turned into separated ones. */
public record SyncResult(SyncRunId syncRunId, SyncRunStatus status, String note, List<EmployeeId> separated) {
    public SyncResult(SyncRunId syncRunId, SyncRunStatus status, String note) {
        this(syncRunId, status, note, List.of());
    }
}
