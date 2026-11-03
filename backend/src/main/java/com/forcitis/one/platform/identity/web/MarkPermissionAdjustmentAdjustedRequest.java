package com.forcitis.one.platform.identity.web;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

/** Exactly one of the two references. Read as text so that a malformed id is a 400 of ours, not of the JSON reader. */
record MarkPermissionAdjustmentAdjustedRequest(@Schema(format = "uuid") @Nullable String authorizationRequestId, @Schema(format = "uuid") @Nullable String auditEntryId) {
}
