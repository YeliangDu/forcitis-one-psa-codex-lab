package com.forcitis.one.organization.workforce.web;

/** Result of releasing a held run: outcome RELEASED, NOT_FOUND, NOT_HELD or NOT_APPLIED; syncRun is set only when released. */
record ReleaseResponse(String outcome, String message, SyncRunResponse syncRun) {
}
