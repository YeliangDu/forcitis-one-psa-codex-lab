package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.kernel.EmployeeId;

/** Read side of the personnel commands for one employee: what the organisation says about them when administrators are counted. */
public interface PersonnelCommandQueries {

    /** Whether this employee's separation is held at this moment. */
    boolean isHeld(EmployeeId employee);

    /** Whether this employee counts as already gone: separation held, released and waiting for its date, or an unpaid-leave start entered and not yet started. */
    boolean isLeaving(EmployeeId employee);
}
