package com.forcitis.one.platform.identity.domain;

import com.forcitis.one.kernel.UserId;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

public interface EmergencyAdminRepository {

    Optional<EmergencyAdmin> findByLoginName(String loginName);

    /** Registers (or replaces) the credentials of an existing user account; used by operations tooling, never over HTTP. */
    void register(EmergencyAdmin admin);

    /**
     * Counts one failure and, when it reaches maxAttempts, locks until now + lock. One atomic statement, so concurrent
     * guesses cannot slip past the limit.
     */
    void recordFailure(UserId userId, Instant now, int maxAttempts, Duration lock);

    /** Clears the failure counter after a successful login. */
    void recordSuccess(UserId userId);

    /** Accepts a TOTP step once: false when the same or a later step was already accepted (replay). Atomic. */
    boolean acceptTotpStep(UserId userId, long step);
}
