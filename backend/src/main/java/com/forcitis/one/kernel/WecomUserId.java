package com.forcitis.one.kernel;

import java.util.Objects;

/** WeCom user id as stored in the organisation source; the only key for WeCom login and messages. */
@ValueObject
public record WecomUserId(String value) {
    public WecomUserId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("WecomUserId must not be blank");
        }
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
