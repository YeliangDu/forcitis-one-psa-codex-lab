package com.forcitis.one.cost.timesheet.web;

import java.math.BigDecimal;

/** outcome: SUBMITTED | EXCEEDS_REMAINING_HOURS | UNKNOWN_WORK_PACKAGE; errorCode: TimesheetError code when not submitted. */
record SubmitTimesheetResponse(String outcome, String errorCode, String approvalInstanceId, BigDecimal remainingHours) {
}
