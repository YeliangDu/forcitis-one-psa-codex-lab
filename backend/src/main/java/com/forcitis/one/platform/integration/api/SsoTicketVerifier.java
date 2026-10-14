package com.forcitis.one.platform.integration.api;

public interface SsoTicketVerifier {

    /** Never throws for an unreachable portal: answers "unavailable". */
    SsoVerdict verify(String ticket);
}
