package com.forcitis.one.kernel;

/** Typed id of a person (employee). Shared because every module refers to people. */
@ValueObject
public record UserId(long value) {

    public UserId {
        if (value == 0) {
            throw new IllegalArgumentException("UserId must not be 0");
        }
    }
}
