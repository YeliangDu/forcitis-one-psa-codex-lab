package com.forcitis.one.platform.identity.web;

/** A Seeyon OA ticket or a signed one-time ticket of an external system; the ticket is the only input. */
record TicketLoginRequest(String ticket) {
}
