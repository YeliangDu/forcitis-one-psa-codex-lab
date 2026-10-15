package com.forcitis.one.kernel;

/** Whether the organisation snapshot can be trusted to list everyone who is employed: false while it has never synced or is stale. */
public interface OrgSnapshotStatus {

    boolean isCurrent();
}
