package com.forcitis.one.organization.workforce.api;

/** How a countersign request was closed; an open request has no decision. */
public enum CountersignDecisionKind {
    APPROVED,
    REJECTED,
    LAPSED
}
