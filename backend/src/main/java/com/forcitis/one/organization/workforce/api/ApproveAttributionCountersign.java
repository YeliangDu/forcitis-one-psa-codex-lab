package com.forcitis.one.organization.workforce.api;

import org.jspecify.annotations.Nullable;

/** Approves one countersign request as the signed-in caller; the reason is optional (1 to 500 characters when given). */
public record ApproveAttributionCountersign(AttributionCountersignId attributionCountersignId, @Nullable String reason) {
}
