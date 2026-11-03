package com.forcitis.one.organization.workforce.api;

import org.jspecify.annotations.Nullable;

/** Rejects one countersign request as the signed-in caller; the reason is required, 1 to 500 characters and not blank. */
public record RejectAttributionCountersign(AttributionCountersignId attributionCountersignId, @Nullable String reason) {
}
