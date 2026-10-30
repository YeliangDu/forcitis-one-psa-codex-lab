package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.organization.workforce.api.WorkforceError;
import org.jspecify.annotations.Nullable;

/**
 * Who ran a workforce operations command and who was present: two different employees named by their own ids, recorded only in the audit entry
 * (ADR-0068 item 2.4). Workforce may not use identity's OperationAttestation (module-dependencies.txt), so it has its own; unlike identity's there is
 * no single operator mode here, the witness is always required (ADR-0030 row 1).
 */
record WorkforceOperationAttestation(EmployeeId operator, EmployeeId witness) {

    WorkforceOperationAttestation {
        if (operator == null || witness == null || operator.equals(witness)) {
            throw required();
        }
    }

    /** The two command-line values must each be an employee id in the lower-case canonical form. */
    static WorkforceOperationAttestation of(@Nullable String operator, @Nullable String witness) {
        try {
            return new WorkforceOperationAttestation(operator == null ? null : EmployeeId.parse(operator), witness == null ? null : EmployeeId.parse(witness));
        } catch (IllegalArgumentException notAnId) {
            throw required();
        }
    }

    static DomainException required() {
        return new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_SEPARATION_HOLD_REFUSED, ErrorKind.UNPROCESSABLE,
                "the command needs an operator and a witness who are two different existing employees, each given as an employee id");
    }
}
