package com.forcitis.one.cost.timesheet.application;

import com.forcitis.one.cost.timesheet.api.TimesheetId;
import com.forcitis.one.cost.timesheet.domain.Timesheet;
import com.forcitis.one.cost.timesheet.api.TimesheetStatus;
import com.forcitis.one.platform.approval.api.ApprovalProcesses;
import com.forcitis.one.platform.approval.api.ProcessKey;
import com.forcitis.one.platform.approval.api.WithdrawCheck;
import com.forcitis.one.platform.identity.api.CurrentUser;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetTimesheet {

    private final Timesheets timesheets;
    private final ApprovalProcesses approvals;
    private final CurrentUser currentUser;

    GetTimesheet(Timesheets timesheets, ApprovalProcesses approvals, CurrentUser currentUser) {
        this.timesheets = timesheets;
        this.approvals = approvals;
        this.currentUser = currentUser;
    }

    @Transactional
    public TimesheetView execute(TimesheetId id) {
        Timesheet timesheet = timesheets.require(id);
        if (!timesheet.employee().equals(currentUser.userId())) {
            throw new AccessDeniedException("Timesheet " + id.value() + " belongs to another employee");
        }
        boolean canWithdraw = timesheet.status() == TimesheetStatus.SUBMITTED && approvals.checkWithdraw(
                ProcessKey.COST_TIMESHEET_APPROVAL, timesheet.document(), currentUser.userId()) == WithdrawCheck.ALLOWED;
        return new TimesheetView(id, timesheet.status(), timesheet.round(), timesheet.approvalInstanceId(), canWithdraw);
    }
}
