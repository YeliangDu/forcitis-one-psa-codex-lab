package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Reads the current (non-superseded) employee versions by business day: the unpaid-leave flag (ADR-0068 section 3) and the employment status. An
 * employee without any version is read from the snapshot until the initial migration (#960).
 */
public interface EmployeeVersionQueries {

    boolean isOnLeave(EmployeeId employee, LocalDate day);

    /** Whether the employee has any employee version, of any channel and date: from then on every attribute is read from versions (#958). */
    boolean hasVersions(EmployeeId employee);

    /** The status of the version in force that day; empty when the employee has no version covering it. */
    Optional<EmploymentStatus> employmentOn(EmployeeId employee, LocalDate day);
}
