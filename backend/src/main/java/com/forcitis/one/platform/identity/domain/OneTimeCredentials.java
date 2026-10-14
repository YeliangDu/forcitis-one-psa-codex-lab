package com.forcitis.one.platform.identity.domain;

import com.forcitis.one.platform.integration.api.WecomEntry;
import java.time.Instant;
import java.util.Optional;

/** Every consuming operation is a single atomic statement, so N concurrent replays give exactly one winner; only hashes are stored. */
public interface OneTimeCredentials {

    void storeLoginState(String stateHash, WecomEntry entry, Instant expiresAt);

    Optional<WecomEntry> consumeLoginState(String stateHash, Instant now);

    boolean markTicketUsed(String ticketSource, String ticketHash, Instant retainUntil);

    int purgeExpired(Instant now);
}
