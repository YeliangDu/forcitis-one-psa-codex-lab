package com.forcitis.one.platform.identity.application;

import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.platform.identity.api.IdentityError;
import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Who ran an operations command and who was present: two different people, recorded in the audit trail (ADR-0027 section 1). Names
 * are compared after compatibility normalisation, trimming, collapsing spaces and ignoring case, so "Alice" and " ａlice " are one person.
 */
public record OperationAttestation(String operator, String witness) {

    private static final Pattern NAME = Pattern.compile("[\\p{L}\\p{N}][\\p{L}\\p{N} ._-]{0,63}");

    public OperationAttestation {
        operator = normalised(operator);
        witness = normalised(witness);
        if (operator == null || witness == null || !NAME.matcher(operator).matches() || !NAME.matcher(witness).matches()
                || operator.toLowerCase(Locale.ROOT).equals(witness.toLowerCase(Locale.ROOT))) {
            throw new DomainException(IdentityError.PLATFORM_IDENTITY_OPERATION_WITNESS_REQUIRED, ErrorKind.UNPROCESSABLE,
                    "an operations command needs an operator and a different witness (letters, digits, space, dot, dash; at most 64 characters)");
        }
    }

    private static String normalised(String name) {
        return name == null ? null : Normalizer.normalize(name, Normalizer.Form.NFKC).strip().replaceAll("\\s+", " ");
    }

    String describe() {
        return "operator=" + operator + " witness=" + witness;
    }
}
