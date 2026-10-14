package com.forcitis.one.kernel;

@ValueObject
public record UserId(long value) {
    public UserId {
        if (value == 0) {
            throw new IllegalArgumentException("UserId must not be 0");
        }
    }
}
