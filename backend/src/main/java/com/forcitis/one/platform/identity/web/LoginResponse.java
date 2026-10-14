package com.forcitis.one.platform.identity.web;

import java.time.Instant;

record LoginResponse(String userId, Instant absoluteExpiresAt) {
}
