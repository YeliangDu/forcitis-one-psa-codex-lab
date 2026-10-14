package com.forcitis.one.cost.timesheet.application;

import com.forcitis.one.cost.timesheet.api.TimesheetError;
import com.forcitis.one.cost.timesheet.api.TimesheetId;
import com.forcitis.one.cost.timesheet.domain.Timesheet;
import com.forcitis.one.cost.timesheet.domain.TimesheetRepository;
import com.forcitis.one.platform.approval.api.ApprovalProcesses;
import com.forcitis.one.platform.approval.api.ProcessKey;
import com.forcitis.one.platform.identity.api.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Ends the whole approval; the timesheet goes back to a draft when the result event arrives (RULE-279). */
@Service
public class CancelTimesheet {

    private final TimesheetRepository timesheets;
    private final ApprovalProcesses approvals;
    private final CurrentUser currentUser;

    CancelTimesheet(TimesheetRepository timesheets, ApprovalProcesses approvals, CurrentUser currentUser) {
        this.timesheets = timesheets;
        this.approvals = approvals;
        this.currentUser = currentUser;
    }

    @Transactional
    public void execute(TimesheetId id, String reason, java.util.OptionalLong expectedVersion) {
        Timesheet timesheet = timesheets.find(id).orElseThrow(() -> TimesheetError.COST_TIMESHEET_NOT_FOUND.refusal("No timesheet " + id.value()));
        if (!timesheet.employee().equals(currentUser.userId())) {
            throw TimesheetError.COST_TIMESHEET_NOT_OWNER.refusal("Timesheet " + id.value() + " belongs to another employee");
        }
        SubmitTimesheet.requireExpectedVersion(timesheet, expectedVersion);
        Retractions.require(approvals.cancel(ProcessKey.COST_TIMESHEET_APPROVAL, timesheet.document(), currentUser.userId(), reason));
    }
}
