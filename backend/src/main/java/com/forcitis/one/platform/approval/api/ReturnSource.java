package com.forcitis.one.platform.approval.api;

/**
 * Who ended the round as RETURNED: an approver sending it back, the initiator pulling it back (D-1a-72), or the system because the
 * round could not start for a reason the initiator can put right.
 */
public enum ReturnSource {
    APPROVER,
    INITIATOR,
    SYSTEM
}
