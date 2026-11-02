package com.forcitis.one.organization.workforce.api;

import org.jspecify.annotations.Nullable;

/** Withdraws a pending or held command; the reason is 1 to 500 characters and not blank (ADR-0068 item 1.6). */
public record WithdrawPersonnelCommand(PersonnelCommandId personnelCommandId, @Nullable String reason) {
}
