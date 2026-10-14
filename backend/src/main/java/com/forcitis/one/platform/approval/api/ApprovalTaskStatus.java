package com.forcitis.one.platform.approval.api;

/** CLOSED: the round ended elsewhere (another node rejected, or the initiator withdrew) before anybody decided this task. */
public enum ApprovalTaskStatus {
    PENDING,
    CLAIMED,
    COMPLETED,
    CLOSED;

    public boolean isOpen() {
        return this == PENDING || this == CLAIMED;
    }
}
