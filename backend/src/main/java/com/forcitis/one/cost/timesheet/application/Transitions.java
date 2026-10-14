package com.forcitis.one.cost.timesheet.application;

import com.forcitis.one.cost.timesheet.domain.TimesheetTransitionException;
import org.springframework.security.access.AccessDeniedException;

/** Domain refusals become application errors here, so the web layer only ever sees TimesheetOperationException. */
final class Transitions {

    private Transitions() {
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
