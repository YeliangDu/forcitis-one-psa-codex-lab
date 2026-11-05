package com.forcitis.one.organization.attendance.api;

import com.forcitis.one.kernel.ValueObject;

/** Position of a standard-hours version in its scope, from 1; voided versions keep their number. */
@ValueObject
public record StandardHoursVersionNumber(int value) {

    public StandardHoursVersionNumber {
        if (value < 1) {
            throw new IllegalArgumentException("a standard-hours version number starts at 1: " + value);
        }
    }
}
