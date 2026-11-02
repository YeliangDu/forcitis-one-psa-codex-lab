package com.forcitis.one.organization.workforce.api;

import com.forcitis.one.kernel.EmployeeId;

import java.util.Set;

/**
 * The employees who still are employed but already count as gone when available super administrators are counted (ADR-0068 item 2.2; user decision
 * 2026-10-30 「统一都算已走」). Answered by workforce, which owns personnel commands; identity asks it when a revoke or a disable has to count.
 */
public interface LeavingEmployees {

    /** Separation held, separation released and waiting for its date, or an unpaid-leave start entered and not yet started: the one count the review takes as its held set. */
    Set<EmployeeId> excluded();

    /** Separation held only: these do not receive the notice either. */
    Set<EmployeeId> held();
}
