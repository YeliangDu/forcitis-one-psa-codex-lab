package com.forcitis.one.organization.workforce.api;

import org.jspecify.annotations.Nullable;

/** Withdraws a pending org-structure command; the reason is 1 to 500 characters and not blank. */
public record WithdrawOrgStructureCommand(OrgStructureCommandId orgStructureCommandId, @Nullable String reason) {
}
