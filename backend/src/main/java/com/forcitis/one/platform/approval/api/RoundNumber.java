package com.forcitis.one.platform.approval.api;

import com.forcitis.one.kernel.ValueObject;

@ValueObject
public record RoundNumber(int value) {

    public RoundNumber {
        if (value < 1) {
            throw new IllegalArgumentException("RoundNumber starts at 1: " + value);
        }
    }
}
