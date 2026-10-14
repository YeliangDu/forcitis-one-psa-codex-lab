package com.forcitis.one.platform.web;

/**
 * Idempotency-Key is for POSTs that change something. Everything under /sessions and the login states hands out credentials or
 * session state, so the answer is never stored (it would keep a secret for 24 hours), and the key is not offered there.
 */
final class IdempotencyScope {
    private static final String SESSIONS = "/sessions";
    private static final String LOGIN_STATES = "/login-states";

    private IdempotencyScope() {
    }

    static boolean applies(String method, String path) {
        return "POST".equals(method) && !path.contains(SESSIONS) && !path.endsWith(LOGIN_STATES);
    }
}
