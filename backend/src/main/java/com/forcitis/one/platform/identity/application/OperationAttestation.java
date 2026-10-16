package com.forcitis.one.platform.identity.application;

import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.platform.identity.api.IdentityError;

/**
 * Who ran an operations command and who was present: two different employees, recorded by their own ids in the audit trail (ADR-0027 section 1,
 * ADR-0043 U-7b); a name is resolved from the id when the entry is read.
 */
public record OperationAttestation(EmployeeId operator, EmployeeId witness) {

    public OperationAttestation {
        if (operator == null || witness == null || operator.equals(witness)) {
            throw required();
        }
    }

    /** The two command-line values must each be an employee id in the lower-case canonical form. */
    public static OperationAttestation of(String operator, String witness) {
        try {
            return new OperationAttestation(operator == null ? null : EmployeeId.parse(operator), witness == null ? null : EmployeeId.parse(witness));
        } catch (IllegalArgumentException notAnId) {
            throw required();
        }
    }

    private static DomainException required() {
        return new DomainException(IdentityError.PLATFORM_IDENTITY_OPERATION_WITNESS_REQUIRED, ErrorKind.UNPROCESSABLE,
                "an operations command needs an operator and a witness who are two different existing employees, each given as an employee id");
    }

    String describe() {
        return "operatorId=" + operator + " witnessId=" + witness;
    }
}
