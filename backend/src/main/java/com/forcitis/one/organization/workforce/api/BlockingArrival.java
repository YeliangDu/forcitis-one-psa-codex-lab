package com.forcitis.one.organization.workforce.api;

import com.forcitis.one.kernel.EmployeeId;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/** Somebody who joins the org unit after the merge day, and the personnel command that put them there (none for a migrated row). */
public record BlockingArrival(EmployeeId employeeId, LocalDate arrivesOn, @Nullable PersonnelCommandId personnelCommandId) {
}
