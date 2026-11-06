package com.forcitis.one.organization.recruitment.api;

/** FILLED has no trigger yet (PG-08): only a person closes a requisition as filled. */
public enum CloseReasonKind {
    REJECTED,
    WITHDRAWN,
    CANCELLED,
    FILLED
}
