package com.forcitis.one.organization.workforce.api;

/** Why an acting duty ended; the names are the values of acting_duties.end_cause. */
public enum ActingDutyEndCause {
    /** The end day passed. */
    EXPIRED,
    /** HR ended it before the end day. */
    ENDED_EARLY,
    /** The person separated. */
    SEPARATION
}
