package com.forcitis.one.cost.timesheet.application;

/** Typed outcome instead of a boolean: callers must handle every case. Failures carry their stable error code. */
public sealed interface SubmissionResult {

    record Submitted(com.forcitis.one.platform.approval.api.ApprovalInstanceId approvalInstanceId) implements SubmissionResult {
    }

    record ExceedsRemainingHours(java.math.BigDecimal remaining) implements SubmissionResult {

        public TimesheetError error() {
            return TimesheetError.COST_TIMESHEET_EXCEEDS_REMAINING_HOURS;
        }
    }

    record UnknownWorkPackage() implements SubmissionResult {

        public TimesheetError error() {
            return TimesheetError.COST_TIMESHEET_UNKNOWN_WORK_PACKAGE;
        }
    }
}
