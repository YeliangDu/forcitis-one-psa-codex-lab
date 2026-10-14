package com.forcitis.one.platform.identity.web;

import java.time.Duration;
import org.springframework.http.ResponseCookie;

/** Ties a login state to the browser that asked for it: HttpOnly, SameSite=Lax; the path covers the whole platform API, but only the login endpoints read it. */
final class LoginStateCookie {
    static final String NAME = "loginBinding";
    private static final String PATH = "/api/v1/platform";

    private LoginStateCookie() {
    }

    static ResponseCookie issue(String binding, boolean secure, Duration lifetime) {
        return ResponseCookie.from(NAME, binding).httpOnly(true).secure(secure).sameSite("Lax").path(PATH).maxAge(lifetime).build();
    }

    static ResponseCookie clear(boolean secure) {
        return ResponseCookie.from(NAME, "").httpOnly(true).secure(secure).sameSite("Lax").path(PATH).maxAge(Duration.ZERO).build();
    }
}
