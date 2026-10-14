package com.forcitis.one.cost.timesheet.application;

import com.forcitis.one.cost.timesheet.api.TimesheetId;
import com.forcitis.one.cost.timesheet.api.TimesheetStatus;
import com.forcitis.one.kernel.AccountingPeriod;
import com.forcitis.one.platform.numbering.api.DocumentNumber;
import java.time.LocalDate;

/** canWithdraw is the same answer the withdraw itself acts on, never a separate approximation. */
public record TimesheetView(TimesheetId timesheetId, DocumentNumber number, TimesheetStatus status, int round, LocalDate workDate,
                            AccountingPeriod period, boolean canWithdraw, long version) {
}
