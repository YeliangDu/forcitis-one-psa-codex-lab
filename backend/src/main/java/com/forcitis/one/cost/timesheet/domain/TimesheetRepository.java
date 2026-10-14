package com.forcitis.one.cost.timesheet.domain;

import com.forcitis.one.cost.timesheet.api.TimesheetId;
import java.util.Optional;

public interface TimesheetRepository {

    /** The row stays locked (FOR UPDATE) until the transaction ends. */
    Optional<Timesheet> find(TimesheetId timesheetId);

    /** Serialises the first submissions of one timesheet number until the transaction ends. */
    void lockForCreate(TimesheetId timesheetId);

    /** Writes only what the aggregate holds, and only if the stored version and status are still the ones it was loaded with. */
    void save(Timesheet timesheet);
}
