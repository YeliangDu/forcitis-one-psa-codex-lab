package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.kernel.EmployeeId;
import java.time.LocalDate;

/** Read side of the personnel commands for one employee: what the organisation says about them when administrators are counted. */
public interface PersonnelCommandQueries {

    /** Whether this employee's separation is held at this moment. */
    boolean isHeld(EmployeeId employee);

    /** Whether this employee counts as already gone on the day: every relationship in service has a held separation, a released one waiting for its date, or an unpaid-leave start entered and not yet started. */
    boolean isLeaving(EmployeeId employee, LocalDate day);
}
