package com.forcitis.one.cost.timesheet.api;

import com.forcitis.one.kernel.ValueObject;

@ValueObject
public record TimesheetId(long value) {

    public TimesheetId {
        if (value <= 0) {
            throw new IllegalArgumentException("TimesheetId must be positive: " + value);
        }
    }
}
