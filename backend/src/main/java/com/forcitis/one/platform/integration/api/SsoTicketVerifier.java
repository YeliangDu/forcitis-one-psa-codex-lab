package com.forcitis.one.platform.integration.api;

/** Server-to-server check of a single-sign-on ticket handed over by the company's portal. */
public interface SsoTicketVerifier {

    /** Ticket to login name, or "invalid" / "unavailable"; never throws for an unreachable portal. */
    SsoVerdict verify(String ticket);
}
