package com.forcitis.one.platform.numbering.web;

import com.forcitis.one.kernel.DocumentType;
import com.forcitis.one.platform.numbering.api.ConfirmationStatus;
import com.forcitis.one.platform.numbering.api.NumberingRuleView;
import com.forcitis.one.platform.numbering.api.ResetPolicy;
import org.jspecify.annotations.Nullable;

record NumberingRuleResponse(DocumentType documentType, @Nullable String documentTypeCode, String pattern, ResetPolicy resetPolicy,
        long startValue, long capacity, ConfirmationStatus confirmationStatus) {

    static NumberingRuleResponse from(NumberingRuleView rule) {
        return new NumberingRuleResponse(rule.documentType(), rule.typeCode().orElse(null), rule.pattern(), rule.resetPolicy(),
                rule.startValue(), rule.capacity(), rule.confirmation());
    }
}
