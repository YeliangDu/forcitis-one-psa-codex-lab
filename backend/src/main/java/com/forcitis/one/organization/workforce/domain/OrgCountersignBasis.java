package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.LegalEntityId;

/** One employee whose cost legal entity on the effective date an org-structure command changes: the entity before and the entity after (ADR-0073 item 3.4). */
public record OrgCountersignBasis(EmployeeId employeeId, LegalEntityId previous, LegalEntityId next) {
}
