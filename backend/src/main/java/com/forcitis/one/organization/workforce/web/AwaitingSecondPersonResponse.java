package com.forcitis.one.organization.workforce.web;

import java.util.UUID;

/** The profile change waits for a second person: the id of the request (confirm or reject it under employee-profile-change-requests). */
record AwaitingSecondPersonResponse(UUID requestId) {
}
