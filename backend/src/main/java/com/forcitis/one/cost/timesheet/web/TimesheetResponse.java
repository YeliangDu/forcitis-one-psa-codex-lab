package com.forcitis.one.cost.timesheet.web;

import com.forcitis.one.cost.timesheet.application.TimesheetView;
import org.jspecify.annotations.Nullable;

record TimesheetResponse(String timesheetId, String status, int round, @Nullable String approvalInstanceId, boolean canWithdraw) {

    static TimesheetResponse of(TimesheetView view) {
        return new TimesheetResponse(String.valueOf(view.timesheetId().value()), view.status().name(), view.round(),
                view.approvalInstanceId().map(id -> id.value()).orElse(null), view.canWithdraw());
    }
}
