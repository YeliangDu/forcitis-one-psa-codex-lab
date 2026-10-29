package com.forcitis.one.cost.timesheet.application;

import com.forcitis.one.cost.timesheet.api.TimesheetApprovalProcess;
import com.forcitis.one.cost.timesheet.api.TimesheetError;
import com.forcitis.one.cost.timesheet.api.TimesheetId;
import com.forcitis.one.cost.timesheet.api.TimesheetStatus;
import com.forcitis.one.cost.timesheet.domain.Timesheet;
import com.forcitis.one.cost.timesheet.domain.TimesheetRepository;
import com.forcitis.one.platform.approval.api.ApprovalProcesses;
import com.forcitis.one.platform.approval.api.WithdrawCheck;
import com.forcitis.one.kernel.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Takes no lock: looking at a timesheet never waits for the approver who is deciding it. */
@Service
public class GetTimesheet {

    private final TimesheetRepository timesheets;
    private final ApprovalProcesses approvals;
    private final CurrentUser currentUser;

    GetTimesheet(TimesheetRepository timesheets, ApprovalProcesses approvals, CurrentUser currentUser) {
        this.timesheets = timesheets;
        this.approvals = approvals;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public TimesheetView execute(TimesheetId id) {
        Timesheet timesheet = timesheets.find(id).orElseThrow(() -> TimesheetError.COST_TIMESHEET_NOT_FOUND.refusal("No such timesheet"));
        if (!timesheet.employee().equals(currentUser.employeeId())) {
            throw TimesheetError.COST_TIMESHEET_NOT_FOUND.refusal("No such timesheet");
        }
        boolean canWithdraw = timesheet.status() == TimesheetStatus.SUBMITTED && approvals.checkWithdraw(
                TimesheetApprovalProcess.KEY, timesheet.document(), currentUser.userId()) == WithdrawCheck.ALLOWED;
        return new TimesheetView(id, timesheet.number(), timesheet.status(), timesheet.round(), timesheet.workDate(), timesheet.periodOfWork(),
                canWithdraw, timesheet.version());
    }
}
