package com.forcitis.one.organization.workforce.web;

import org.jspecify.annotations.Nullable;

record ReleaseResponse(String outcome, @Nullable String message, @Nullable SyncRunResponse syncRun) {
}
