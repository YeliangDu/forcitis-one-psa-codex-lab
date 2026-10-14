package com.forcitis.one.platform.identity.web;

import java.time.Duration;
import org.springframework.http.ResponseCookie;

/**
 * The prefix makes the browser refuse the cookie unless it is Secure, has no Domain and has Path=/, so that a sibling site cannot plant
 * one; Secure is therefore not configurable here.
 */
final class BrowserIdentifierCookie {
    static final String NAME = "__Host-browserIdentifier";

    private BrowserIdentifierCookie() {
    }

    static ResponseCookie issue(String value, Duration lifetime) {
        return ResponseCookie.from(NAME, value).httpOnly(true).secure(true).sameSite("Lax").path("/").maxAge(lifetime).build();
    }
}
