package com.forcitis.one.platform.numbering.api;

import com.forcitis.one.kernel.DocumentTypeCode;
import com.forcitis.one.kernel.ValueObject;
import java.util.Optional;

/**
 * template is the version in force; while none is confirmed yet it is the version waiting for confirmation, and confirmation says so.
 * pending is the version waiting for confirmation, if any (a confirmed rule keeps issuing with its version in force meanwhile).
 */
@ValueObject
public record NumberingRuleView(DocumentTypeCode documentType, Optional<String> numberCode, long version, ConfirmationStatus confirmation,
        boolean requiresFinanceConfirmation, NumberingTemplateView template, Optional<NumberingTemplateView> pending) {
}
