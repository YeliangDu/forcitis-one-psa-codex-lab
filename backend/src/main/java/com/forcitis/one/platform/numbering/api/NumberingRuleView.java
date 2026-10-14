package com.forcitis.one.platform.numbering.api;

import com.forcitis.one.kernel.DocumentType;

import com.forcitis.one.kernel.ValueObject;
import java.util.Optional;

/** capacity is the highest sequence the template can hold. */
@ValueObject
public record NumberingRuleView(DocumentType documentType, Optional<String> typeCode, String pattern, ResetPolicy resetPolicy,
        long startValue, long capacity, ConfirmationStatus confirmation, long version) {
}
