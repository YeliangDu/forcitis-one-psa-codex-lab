package com.forcitis.one.cost.timesheet.application;

import com.forcitis.one.cost.timesheet.api.TimesheetError;
import com.forcitis.one.kernel.KernelError;
import com.forcitis.one.platform.approval.api.RetractionResult;

final class Retractions {

    private Retractions() {
    }

    static void require(RetractionResult result) {
        switch (result) {
            case DONE -> {
            }
            case REASON_REQUIRED -> throw TimesheetError.COST_TIMESHEET_REASON_REQUIRED.refusal("A reason is required");
            case NOT_RUNNING -> throw KernelError.KERNEL_TRANSITION_ILLEGAL.refusal("The timesheet is not in a running approval");
            case CUTOFF_PASSED -> throw TimesheetError.COST_TIMESHEET_CUTOFF_PASSED.refusal(
                    "An approval node that ends the right to retract has already finished a task");
        }
    }
}
