package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.organization.workforce.domain.SyncRunId;
import com.forcitis.one.organization.workforce.domain.SyncRunStatus;

public record SyncResult(SyncRunId syncRunId, SyncRunStatus status, String note) {
}
