package com.forcitis.one.platform.numbering.domain;

import com.forcitis.one.platform.numbering.api.ConfirmationStatus;
import com.forcitis.one.kernel.DocumentType;
import java.util.Objects;

public record NumberingRule(DocumentType documentType, NumberingPattern pattern, long startValue, ConfirmationStatus confirmation) {
    public NumberingRule {
        Objects.requireNonNull(documentType, "documentType");
        Objects.requireNonNull(pattern, "pattern");
        Objects.requireNonNull(confirmation, "confirmation");
        if (startValue < 1) {
            throw new IllegalArgumentException("startValue must be >= 1");
        }
        if (pattern.usesTypeCode() && documentType.code().isEmpty()) {
            throw new IllegalArgumentException("Document type " + documentType
                    + " has no registered code, so its pattern cannot use {" + NumberingPattern.TYPE_TOKEN + "}");
        }
    }

    public static NumberingRule confirmed(DocumentType documentType, NumberingPattern pattern, long startValue) {
        return new NumberingRule(documentType, pattern, startValue, ConfirmationStatus.CONFIRMED);
    }
}
