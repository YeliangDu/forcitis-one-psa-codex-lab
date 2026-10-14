package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.organization.workforce.domain.SyncRunId;
import com.forcitis.one.organization.workforce.domain.SyncRunStatus;

/** Outcome of one sync attempt: SUCCEEDED, HELD (shrink guard) or FAILED (note says why). */
public record SyncResult(SyncRunId syncRunId, SyncRunStatus status, String note) {
}
