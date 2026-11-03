package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.organization.workforce.api.AssignmentId;

/** One post of an employed person that a merge has to move out of the merged unit: the root it belongs to and whether it is the primary post or a concurrent one. */
public record HeldPost(EmployeeId employee, AssignmentId assignment, AssignmentKind kind) {
}
