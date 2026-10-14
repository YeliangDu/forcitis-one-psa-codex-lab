package com.forcitis.one.organization.workforce.domain;

/** HELD = aborted by the shrink guard (RULE-358/366) and waiting for an administrator; RELEASED = that hold was released. */
public enum SyncRunStatus {
    RUNNING,
    SUCCEEDED,
    HELD,
    RELEASED,
    FAILED
}
