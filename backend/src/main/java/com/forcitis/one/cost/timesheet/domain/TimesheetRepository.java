package com.forcitis.one.cost.timesheet.domain;

import com.forcitis.one.cost.timesheet.api.TimesheetId;
import com.forcitis.one.delivery.execution.api.WorkPackageId;
import com.forcitis.one.kernel.Hours;
import java.util.Optional;

public interface TimesheetRepository {

    /** Reads without a lock: for queries and for checks that change nothing. */
    Optional<Timesheet> find(TimesheetId timesheetId);

    /** The row stays locked until the transaction ends. */
    Optional<Timesheet> findForUpdate(TimesheetId timesheetId);

    /** Serialises the first submissions of one timesheet until the transaction ends. */
    void lockForCreate(TimesheetId timesheetId);

    /** Serialises the submissions that draw on one work package until the transaction ends. */
    void lockWorkPackage(WorkPackageId workPackageId);

    /** Hours of the timesheets on that work package that are submitted and not decided yet, the one named excepted. */
    Hours pendingHours(WorkPackageId workPackageId, TimesheetId except);

    /** Writes only what the aggregate holds, and only if the stored version and status are still the ones it was loaded with. */
    void save(Timesheet timesheet);
}
