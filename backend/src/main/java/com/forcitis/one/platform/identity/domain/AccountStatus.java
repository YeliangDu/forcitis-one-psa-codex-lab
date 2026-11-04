package com.forcitis.one.platform.identity.domain;

import java.util.Optional;

/** Employment status is owned by the organisation. */
public enum AccountStatus {
    ENABLED,
    DISABLED;

    /** Only the two stored spellings; lower case or anything else is empty. */
    public static Optional<AccountStatus> ofStoredName(String name) {
        for (AccountStatus status : values()) {
            if (status.name().equals(name)) {
                return Optional.of(status);
            }
        }
        return Optional.empty();
    }
}
