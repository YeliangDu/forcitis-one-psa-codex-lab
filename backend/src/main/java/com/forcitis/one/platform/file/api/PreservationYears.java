package com.forcitis.one.platform.file.api;

import com.forcitis.one.kernel.ValueObject;

@ValueObject
public record PreservationYears(int value) {

    public PreservationYears {
        if (value < 1) {
            throw new IllegalArgumentException("Preservation years must be at least 1");
        }
    }
}
