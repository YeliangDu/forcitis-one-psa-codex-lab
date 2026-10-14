package com.forcitis.one.kernel;

import java.time.Instant;
import java.util.Optional;

/** The stored answers to requests that carried an Idempotency-Key (kept by platform.idempotency). */
public interface IdempotentResponses {

    record Stored(String requestHash, Integer responseNumber, String responseName, String responseText) {
        public boolean finished() {
            return responseNumber != null;
        }
    }

    /** True for the request that claimed the key; false when the key was taken (and not yet expired). */
    boolean claim(long userId, String key, String requestHash, Instant now, Instant expiresAt);

    Optional<Stored> find(long userId, String key);

    void finish(long userId, String key, int responseNumber, String responseName, String responseText);

    void release(long userId, String key);
}
