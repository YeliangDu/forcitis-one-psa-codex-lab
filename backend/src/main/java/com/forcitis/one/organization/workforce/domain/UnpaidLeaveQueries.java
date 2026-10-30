package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.kernel.EmployeeId;
import java.time.LocalDate;

/** Reads the unpaid-leave flag of the employee versions by business day (ADR-0068 section 3); superseded versions are no fact. */
public interface UnpaidLeaveQueries {

    boolean isOnLeave(EmployeeId employee, LocalDate day);
}
