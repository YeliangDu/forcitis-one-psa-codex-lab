package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Reads the current (non-superseded) employee versions by business day: the unpaid-leave flag (ADR-0068 section 3) and the employment status the personnel
 * commands wrote. An employee the commands never touched has no version and is read from the snapshot as before.
 */
public interface EmployeeVersionQueries {

    boolean isOnLeave(EmployeeId employee, LocalDate day);

    /** The status of the version in force that day; empty when the employee has no version covering it. */
    Optional<EmploymentStatus> employmentOn(EmployeeId employee, LocalDate day);
}
