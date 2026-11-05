package com.forcitis.one.organization.attendance.api;

import com.forcitis.one.kernel.ValueObject;

/** Minutes of one standard working day, 1 to 1440. */
@ValueObject
public record StandardHoursMinutes(int value) {

    public StandardHoursMinutes {
        if (value < 1 || value > 1440) {
            throw new IllegalArgumentException("standard-hours minutes are 1..1440: " + value);
        }
    }
}
