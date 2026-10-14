package com.forcitis.one.platform.numbering.api;

import com.forcitis.one.kernel.ValueObject;

@ValueObject
public record RuleVersion(long value) {
    public RuleVersion {
        if (value < 1) {
            throw new IllegalArgumentException("RuleVersion starts at 1: " + value);
        }
    }

    public RuleVersion next() {
        return new RuleVersion(value + 1);
    }
}
