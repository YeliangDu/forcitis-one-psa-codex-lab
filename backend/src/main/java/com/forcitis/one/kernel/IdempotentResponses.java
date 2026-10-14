package com.forcitis.one.kernel;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/** The stored answers to requests that carried an Idempotency-Key (kept by platform.idempotency). */
public interface IdempotentResponses {

    record Stored(String requestHash, Integer responseNumber, String responseName, String responseText, Map<String, String> responseHeaders) {
        public boolean finished() {
            return responseNumber != null;
        }
    }

    /**
     * True for the request that claimed the key; false when the key was taken. An expired answer, and a claim whose request never
     * finished within reclaimAfter (the node died), count as not taken.
     */
    boolean claim(long userId, String key, String requestHash, Instant now, Instant expiresAt, Duration reclaimAfter);

    Optional<Stored> find(long userId, String key);

    void finish(long userId, String key, int responseNumber, String responseName, String responseText, Map<String, String> responseHeaders);

    void release(long userId, String key);

    int purgeExpired(Instant now);
}
