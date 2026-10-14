package com.forcitis.one.platform.identity.domain;

import com.forcitis.one.kernel.UserId;
import java.util.Objects;

public record UserAccount(UserId userId, AccountStatus status) {

    public UserAccount {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(status, "status");
    }

    public boolean isEnabled() {
        return status == AccountStatus.ENABLED;
    }
}
