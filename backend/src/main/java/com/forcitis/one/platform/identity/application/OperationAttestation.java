package com.forcitis.one.platform.identity.application;

import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.platform.identity.api.IdentityError;
import java.util.regex.Pattern;

/** Who ran an operations command and who was present: two different people, recorded in the audit trail (ADR-0027 section 1). */
public record OperationAttestation(String operator, String witness) {

    private static final Pattern NAME = Pattern.compile("[\\p{L}\\p{N}][\\p{L}\\p{N} ._-]{0,63}");

    public OperationAttestation {
        if (operator == null || witness == null || !NAME.matcher(operator).matches() || !NAME.matcher(witness).matches()
                || operator.equalsIgnoreCase(witness)) {
            throw new DomainException(IdentityError.PLATFORM_IDENTITY_OPERATION_WITNESS_REQUIRED, ErrorKind.UNPROCESSABLE,
                    "an operations command needs an operator and a different witness (letters, digits, space, dot, dash; at most 64 characters)");
        }
    }

    String describe() {
        return "operator=" + operator + " witness=" + witness;
    }
}
