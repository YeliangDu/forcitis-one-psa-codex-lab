package com.forcitis.one.cost.timesheet.api;

import com.forcitis.one.kernel.Uuids;
import com.forcitis.one.kernel.ValueObject;
import java.util.UUID;

@ValueObject
public record TimesheetId(UUID value) {

    public TimesheetId {
        value = Uuids.requireKey(value, "TimesheetId");
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
