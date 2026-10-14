package com.forcitis.one.cost.timesheet.application;

import com.forcitis.one.delivery.execution.api.WorkPackageId;
import com.forcitis.one.kernel.Hours;
import com.forcitis.one.cost.timesheet.api.TimesheetId;
import java.util.Objects;

public record SubmitTimesheetCommand(TimesheetId timesheetId, WorkPackageId workPackageId, Hours hours) {

    public SubmitTimesheetCommand {
        Objects.requireNonNull(timesheetId, "timesheetId");
        Objects.requireNonNull(workPackageId, "workPackageId");
        Objects.requireNonNull(hours, "hours");
    }
}
