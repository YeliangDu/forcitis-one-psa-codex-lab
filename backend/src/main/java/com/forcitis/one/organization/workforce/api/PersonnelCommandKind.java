package com.forcitis.one.organization.workforce.api;

/** What a personnel command does (ADR-0068 item 1.3); the names are the values of personnel_commands.command_kind. */
public enum PersonnelCommandKind {
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
    ACTING_START,
    ACTING_END,
    ACTING_EXTEND
}
