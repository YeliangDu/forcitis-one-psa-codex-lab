package com.forcitis.one.platform.identity.web;

import java.util.UUID;

/** Nothing has changed yet: another holder of account-role:update confirms the request with this id. */
record AwaitingConfirmationResponse(UUID requestId) implements RoleChangeResponse {
}
