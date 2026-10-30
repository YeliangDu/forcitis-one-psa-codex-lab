package com.forcitis.one.organization.workforce.api;

/**
 * The single release of a held separation (ADR-0068 item 2.3). Needs organization.workforce:separation-hold:approve, checked before the content;
 * a releaser who is not retained, or a release that would leave no available super administrator, is refused.
 */
public interface SeparationHolds {

    /** Carries out the held separation at its original effective date; it is not re-run and never held again. */
    void release(ReleaseSeparationHold command);
}
