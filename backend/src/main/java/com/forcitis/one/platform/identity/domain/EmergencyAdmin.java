package com.forcitis.one.platform.identity.domain;

import com.forcitis.one.kernel.UserId;
import java.time.Instant;
import java.util.Objects;

public record EmergencyAdmin(UserId userId, String loginName, String passwordHash, String totpSecretEncrypted,
                             long lastTotpStep, int failedAttempts, Instant lockedUntil, boolean enabled) {

    public EmergencyAdmin {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(loginName, "loginName");
        Objects.requireNonNull(passwordHash, "passwordHash");
        Objects.requireNonNull(totpSecretEncrypted, "totpSecretEncrypted");
    }

    public boolean isLockedAt(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }
}
