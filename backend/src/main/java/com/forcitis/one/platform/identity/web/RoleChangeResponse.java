package com.forcitis.one.platform.identity.web;

import io.swagger.v3.oas.annotations.media.Schema;

/** A role change either took effect (the role) or waits for a second holder to confirm it (the request id). */
@Schema(oneOf = {RoleResponse.class, AwaitingConfirmationResponse.class})
sealed interface RoleChangeResponse permits RoleResponse, AwaitingConfirmationResponse {
}
