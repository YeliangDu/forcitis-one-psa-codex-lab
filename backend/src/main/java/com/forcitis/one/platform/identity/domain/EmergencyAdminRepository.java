package com.forcitis.one.platform.identity.domain;

import com.forcitis.one.kernel.UserId;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

public interface EmergencyAdminRepository {

    Optional<EmergencyAdmin> findByLoginName(String loginName);

    void register(EmergencyAdmin admin);

    /** One atomic statement, so concurrent guesses cannot slip past the limit. */
    void recordFailure(UserId userId, Instant now, int maxAttempts, Duration lock);

    void recordSuccess(UserId userId);

    /** False when the same or a later step was already accepted (replay). */
    boolean acceptTotpStep(UserId userId, long step);
}
