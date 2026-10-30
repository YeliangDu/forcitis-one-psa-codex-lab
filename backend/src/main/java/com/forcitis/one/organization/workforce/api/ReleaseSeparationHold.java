package com.forcitis.one.organization.workforce.api;

import org.jspecify.annotations.Nullable;

/**
 * Releases a held separation. Only the hold and the reason are the caller's: the releaser, the time and the channel are decided by the use case
 * (ADR-0068 item 2.3). The reason is 1 to 500 characters and not blank.
 */
public record ReleaseSeparationHold(SeparationHoldId separationHoldId, @Nullable String reason) {
}
