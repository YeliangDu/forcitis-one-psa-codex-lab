package com.forcitis.one.platform.numbering.domain;

import com.forcitis.one.platform.numbering.api.DocumentType;
import java.util.Objects;

public record NumberingRule(DocumentType documentType, NumberingPattern pattern, long startValue) {

    public NumberingRule {
        Objects.requireNonNull(documentType, "documentType");
        Objects.requireNonNull(pattern, "pattern");
        if (startValue < 1) {
            throw new IllegalArgumentException("startValue must be >= 1");
        }
    }
}
