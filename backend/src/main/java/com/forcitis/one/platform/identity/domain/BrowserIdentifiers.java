package com.forcitis.one.platform.identity.domain;

import com.forcitis.one.kernel.UserId;
import java.time.Instant;
import java.util.Optional;

/** Only hashes are stored. One browser can carry the identifier of several accounts: they share the value and are looked at one account at a time. */
public interface BrowserIdentifiers {

    record Known(Instant lastLoginAt, boolean retired) {
    }

    Optional<Known> find(String identifierHash, UserId userId);

    /** Retires the account's row of oldHash (if any), moves the other accounts of that browser to newHash and records newHash for the account. */
    void replace(UserId userId, String oldHash, String newHash, Instant now);

    int invalidateAll(UserId userId);

    int purge(Instant olderThan);
}
