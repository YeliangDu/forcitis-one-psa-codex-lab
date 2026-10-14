package com.forcitis.one.organization.workforce.application;

/** Outcome of releasing a held sync run; the web layer maps each case to an HTTP status. */
public sealed interface ReleaseResult {

    /** The held run was released and the real sync succeeded as releasedRun. */
    record Released(SyncRunView releasedRun) implements ReleaseResult {
    }

    record NotFound() implements ReleaseResult {
    }

    /** Only a run in status HELD can be released; currentStatus says what it is now. */
    record NotHeld(String currentStatus) implements ReleaseResult {
    }

    /** The release was attempted but the sync itself failed or was held again; the hold stays. */
    record NotApplied(String reason) implements ReleaseResult {
    }
}
