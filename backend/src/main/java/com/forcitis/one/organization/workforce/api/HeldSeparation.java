package com.forcitis.one.organization.workforce.api;

import com.forcitis.one.kernel.EmployeeId;

/** Somebody who is still in the merged unit on the merge day because their separation is held, and the held command: HR moves them out by hand before the merge can finish. */
public record HeldSeparation(EmployeeId employeeId, PersonnelCommandId personnelCommandId) {
}
