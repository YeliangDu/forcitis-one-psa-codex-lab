package com.forcitis.one.organization.workforce.api;

/** The kind of the command a legal-entity countersign belongs to: a personnel command kind or an org-structure command kind (the two sets share no name). */
public enum CountersignCommandKind {
    HIRE,
    REGULARIZE,
    TRANSFER,
    SEPARATE,
    REINSTATE,
    CORRECTION,
    UNPAID_LEAVE_START,
    UNPAID_LEAVE_END,
    CHANGE_LEGAL_ENTITY,
    START_CONCURRENT,
    END_CONCURRENT,
    CREATE,
    DISSOLVE,
    SET_ATTRIBUTES,
    MERGE,
    REPARENT,
    ASSIGN_HEAD,
    ASSIGN_RECEIVABLES_MANAGER,
    ASSIGN_LEGAL_ENTITY,
    DISABLE
}
