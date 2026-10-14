package com.forcitis.one.platform.identity.web;

import java.time.Instant;

/** The session cookie is set by the server (HttpOnly); the body only says who is in and when the absolute cap ends it. */
record LoginResponse(String userId, Instant absoluteExpiresAt) {
}
