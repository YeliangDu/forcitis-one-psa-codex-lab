package com.forcitis.one.organization.workforce.web;

import org.jspecify.annotations.Nullable;

/** The administrator says why the held run is let through; the reason goes to the audit trail. */
record ReleaseSyncRunRequest(@Nullable String reason) {
}
