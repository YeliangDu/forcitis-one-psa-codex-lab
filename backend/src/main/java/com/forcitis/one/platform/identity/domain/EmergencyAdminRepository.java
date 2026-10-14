package com.forcitis.one.platform.identity.domain;

import com.forcitis.one.kernel.UserId;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface EmergencyAdminRepository {

    Optional<EmergencyAdmin> findByLoginName(String loginName);

    Optional<EmergencyAdmin> findByUserId(UserId userId);

    List<EmergencyAdmin> findAll();

    /** True only for the caller that created it (atomic). */
    boolean insertIfAbsent(EmergencyAdmin registered);

    /** Applies only to the version and status the admin was loaded with; otherwise a DomainException (CONFLICT). */
    void save(EmergencyAdmin admin);

    /**
     * Checks that the account is not locked and counts the attempt in one atomic statement, before the credentials are looked at, so
     * concurrent guesses cannot slip past the limit. False when it is locked. The counter is reset by recordSuccess.
     */
    boolean reserveAttempt(UserId userId, Instant now, int maxAttempts, Duration lock);

    void recordSuccess(UserId userId);

    /** False when the same or a later step was already accepted (replay). */
    boolean acceptTotpStep(UserId userId, long step);
}
