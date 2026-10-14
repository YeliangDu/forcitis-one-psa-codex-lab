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
            case REASON_REQUIRED -> throw TimesheetError.COST_TIMESHEET_REASON_REQUIRED.refusal("A reason is required");
            case NOT_RUNNING -> throw TimesheetError.COST_TIMESHEET_TRANSITION_ILLEGAL.refusal("The timesheet is not in a running approval");
            case CUTOFF_PASSED -> throw TimesheetError.COST_TIMESHEET_CUTOFF_PASSED.refusal("An approval node that ends the right to retract has already finished a task");
        }
    }

    static void run(Runnable transition) {
        try {
            transition.run();
        } catch (TimesheetTransitionException refused) {
            switch (refused.violation()) {
                case NOT_OWNER -> throw new AccessDeniedException(refused.getMessage());
                case ILLEGAL_TRANSITION -> throw TimesheetError.COST_TIMESHEET_TRANSITION_ILLEGAL.refusal(refused.getMessage());
            }
        }
    }
}
