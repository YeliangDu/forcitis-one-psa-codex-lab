package com.forcitis.one.platform.identity.application;

import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.platform.identity.api.IdentityError;
import org.jspecify.annotations.Nullable;

/**
 * Who ran an operations command and who was present: two different employees, recorded by their own ids in the audit trail (ADR-0027 section 1,
 * ADR-0043 U-7b); a name is resolved from the id when the entry is read.
 */
public record OperationAttestation(EmployeeId operator, @Nullable EmployeeId witness) {

    public OperationAttestation {
        if (operator == null || operator.equals(witness)) {
            throw required();
        }
    }

    /** The two command-line values must each be an employee id in the lower-case canonical form. */
    public static OperationAttestation of(String operator, String witness) {
        try {
            if (witness == null) {
                throw required();
            }
            return new OperationAttestation(operator == null ? null : EmployeeId.parse(operator), EmployeeId.parse(witness));
        } catch (IllegalArgumentException notAnId) {
            throw required();
        }
    }

    /** Only for the single operator mode and only when no witness was given at all; the caller has checked both. */
    static OperationAttestation withoutWitness(String operator) {
        try {
            return new OperationAttestation(operator == null ? null : EmployeeId.parse(operator), null);
        } catch (IllegalArgumentException notAnId) {
            throw required();
        }
    }

    boolean witnessOmitted() {
        return witness == null;
    }

    private static DomainException required() {
        return new DomainException(IdentityError.PLATFORM_IDENTITY_OPERATION_WITNESS_REQUIRED, ErrorKind.UNPROCESSABLE,
                "an operations command needs an operator and a different witness, each given as an employee id");
    }

    String describe() {
        return "operatorId=" + operator + " witnessId=" + (witness == null ? "none" : witness);
    }
}
