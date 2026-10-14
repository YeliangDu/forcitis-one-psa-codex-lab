package com.forcitis.one.cost.timesheet.application;

import com.forcitis.one.cost.timesheet.api.TimesheetId;
import com.forcitis.one.cost.timesheet.domain.Timesheet;
import com.forcitis.one.platform.approval.api.ApprovalProcesses;
import com.forcitis.one.platform.approval.api.ProcessKey;
import com.forcitis.one.platform.identity.api.CurrentUser;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Ends the whole approval; the timesheet goes back to a draft when the result event arrives (RULE-279). */
@Service
public class CancelTimesheet {

    private final Timesheets timesheets;
    private final ApprovalProcesses approvals;
    private final CurrentUser currentUser;

    CancelTimesheet(Timesheets timesheets, ApprovalProcesses approvals, CurrentUser currentUser) {
        this.timesheets = timesheets;
        this.approvals = approvals;
        this.currentUser = currentUser;
    }

    @Transactional
    public void execute(TimesheetId id, String reason) {
        Timesheet timesheet = timesheets.require(id);
        if (!timesheet.employee().equals(currentUser.userId())) {
            throw new AccessDeniedException("Timesheet " + id.value() + " belongs to another employee");
        }
        Transitions.require(approvals.cancel(ProcessKey.COST_TIMESHEET_APPROVAL, timesheet.document(), currentUser.userId(), reason));
    }
}
