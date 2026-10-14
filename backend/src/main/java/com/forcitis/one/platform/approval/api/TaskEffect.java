package com.forcitis.one.platform.approval.api;

/**
 * ADVISORY: the opinion of an added co-signer, recorded without ending the round (RULE-273). AUTOMATIC: the system passed the task
 * because the same approver had already approved the same content on the same node (RULE-302, D-1a-66).
 */
public enum TaskEffect {
    BINDING,
    ADVISORY,
    AUTOMATIC
}
