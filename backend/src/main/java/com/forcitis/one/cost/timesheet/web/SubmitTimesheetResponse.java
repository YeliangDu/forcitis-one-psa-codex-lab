package com.forcitis.one.cost.timesheet.web;

import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

/** outcome: SUBMITTED | EXCEEDS_REMAINING_HOURS | UNKNOWN_WORK_PACKAGE; errorCode: TimesheetError code when not submitted. */
record SubmitTimesheetResponse(String outcome, @Nullable String errorCode, @Nullable String approvalInstanceId, @Nullable BigDecimal remainingHours) {
}
