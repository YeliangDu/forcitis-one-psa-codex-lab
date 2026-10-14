package com.forcitis.one.platform.identity.domain;

import com.forcitis.one.platform.integration.api.WecomEntry;
import java.time.Instant;
import java.util.Optional;

/**
 * Store for credentials that may be used once: OAuth state, portal tickets, signed-ticket nonces (I-9). Every consuming
 * operation is a single atomic statement (unique key / delete-returning), so N concurrent replays give exactly one winner.
 * Only hashes are stored.
 */
public interface OneTimeCredentials {

    void storeLoginState(String stateHash, WecomEntry entry, Instant expiresAt);

    /** Removes and returns the state if it exists and has not expired; empty for unknown, used or expired states. Atomic. */
    Optional<WecomEntry> consumeLoginState(String stateHash, Instant now);

    /** Marks a ticket / nonce as used; false when it was already used. Atomic (unique key). */
    boolean markTicketUsed(String ticketSource, String ticketHash, Instant retainUntil);

    /** Deletes expired states and used-ticket records; returns how many rows went. */
    int purgeExpired(Instant now);
}
