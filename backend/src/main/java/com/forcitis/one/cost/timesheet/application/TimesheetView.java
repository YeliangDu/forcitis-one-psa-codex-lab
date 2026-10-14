package com.forcitis.one.cost.timesheet.application;

import com.forcitis.one.cost.timesheet.api.TimesheetId;
import com.forcitis.one.cost.timesheet.api.TimesheetStatus;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import java.util.Optional;

/** canWithdraw is the same answer the withdraw itself acts on, never a separate approximation. */
public record TimesheetView(TimesheetId timesheetId, TimesheetStatus status, int round, Optional<ApprovalInstanceId> approvalInstanceId,
                            boolean canWithdraw) {
}
