package com.forcitis.one.cost.timesheet.api;

import com.forcitis.one.kernel.ValueObject;
import java.util.Objects;
import java.util.UUID;

@ValueObject
public record TimesheetId(UUID value) {

    public TimesheetId {
        Objects.requireNonNull(value, "value");
    }
}
