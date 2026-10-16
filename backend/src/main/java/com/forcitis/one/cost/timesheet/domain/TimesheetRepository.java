package com.forcitis.one.cost.timesheet.domain;

import com.forcitis.one.cost.timesheet.api.TimesheetId;
import com.forcitis.one.kernel.EmployeeId;
import java.time.LocalDate;
import java.util.Optional;

public interface TimesheetRepository {

    /** Reads without a lock: for queries and for checks that change nothing. */
    Optional<Timesheet> find(TimesheetId timesheetId);

    /** Whether the employee already has a timesheet for the work date, in any status. */
    boolean existsFor(EmployeeId employee, LocalDate workDate);

    /** The row stays locked until the transaction ends. */
    Optional<Timesheet> findForUpdate(TimesheetId timesheetId);

    /** Serialises the first submissions of one timesheet until the transaction ends. */
    void lockForCreate(TimesheetId timesheetId);

    /** Writes only what the aggregate holds, and only if the stored version and status are still the ones it was loaded with. */
    void save(Timesheet timesheet);
}
