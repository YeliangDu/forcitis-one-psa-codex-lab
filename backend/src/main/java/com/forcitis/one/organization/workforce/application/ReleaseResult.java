package com.forcitis.one.organization.workforce.application;

public sealed interface ReleaseResult {
    record Released(SyncRunView releasedRun) implements ReleaseResult {
    }

    record NotFound() implements ReleaseResult {
    }

    record NotHeld(String currentStatus) implements ReleaseResult {
    }

    record NotApplied(String reason) implements ReleaseResult {
    }
}
