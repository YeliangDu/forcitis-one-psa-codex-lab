package com.forcitis.one.cost.timesheet.application;

import com.forcitis.one.cost.timesheet.api.TimesheetError;
import com.forcitis.one.cost.timesheet.api.TimesheetId;
import com.forcitis.one.kernel.Hours;
import com.forcitis.one.kernel.UserId;
import java.time.LocalDate;
import java.util.Objects;
import java.util.OptionalLong;

/** workDate is the day the hours were worked, as the employee entered it; expectedVersion is the ETag the caller read (If-Match), if it sent one. */
public record SubmitTimesheetCommand(TimesheetId timesheetId, LocalDate workDate, Hours hours, UserId projectManager,
                                     OptionalLong expectedVersion) {

    public SubmitTimesheetCommand {
        Objects.requireNonNull(timesheetId, "timesheetId");
        if (workDate == null) {
            throw TimesheetError.COST_TIMESHEET_WORK_DATE_REQUIRED.refusal("The work date is required");
        }
        Objects.requireNonNull(hours, "hours");
        Objects.requireNonNull(projectManager, "projectManager");
        Objects.requireNonNull(expectedVersion, "expectedVersion");
    }

    public SubmitTimesheetCommand(TimesheetId timesheetId, LocalDate workDate, Hours hours, UserId projectManager) {
        this(timesheetId, workDate, hours, projectManager, OptionalLong.empty());
    }
}
