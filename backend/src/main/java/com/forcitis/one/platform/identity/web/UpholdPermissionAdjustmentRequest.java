package com.forcitis.one.platform.identity.web;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

/** The expiry is never the caller's to give: a sensitive-role review expires at the next quarter start, as the server works it out. */
record UpholdPermissionAdjustmentRequest(@Nullable String reason, @Schema(format = "uuid") @Nullable String businessOwnerEmployeeId) {
}
