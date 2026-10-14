package com.forcitis.one.cost.timesheet.application;

import com.forcitis.one.cost.timesheet.domain.TimesheetTransitionException;
import com.forcitis.one.platform.approval.api.RetractionResult;
import org.springframework.security.access.AccessDeniedException;

final class Transitions {

    private Transitions() {
    }

    static void require(RetractionResult result) {
        switch (result) {
            case DONE -> {
            }
            case REASON_REQUIRED -> throw new TimesheetOperationException(TimesheetError.COST_TIMESHEET_REASON_REQUIRED, "A reason is required");
            case NOT_RUNNING -> throw new TimesheetOperationException(TimesheetError.COST_TIMESHEET_TRANSITION_ILLEGAL,
                    "The timesheet is not in a running approval");
            case CUTOFF_PASSED -> throw new TimesheetOperationException(TimesheetError.COST_TIMESHEET_CUTOFF_PASSED,
                    "An approval node that ends the right to retract has already finished a task");
        }
    }

    static void run(Runnable transition) {
        try {
            transition.run();
        } catch (TimesheetTransitionException refused) {
            switch (refused.violation()) {
                case NOT_OWNER -> throw new AccessDeniedException(refused.getMessage());
                case ILLEGAL_TRANSITION -> throw new TimesheetOperationException(TimesheetError.COST_TIMESHEET_TRANSITION_ILLEGAL,
                        refused.getMessage());
            }
        }
    }
}
