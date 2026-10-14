package com.forcitis.one.platform.numbering.web;

import com.forcitis.one.platform.numbering.api.NumberingRuleView;

record NumberingRuleResponse(String documentType, String documentTypeCode, String pattern, String resetPolicy, long startValue,
        long capacity, String confirmationStatus) {

    static NumberingRuleResponse from(NumberingRuleView rule) {
        return new NumberingRuleResponse(rule.documentType().name(), rule.typeCode().orElse(null), rule.pattern(),
                rule.resetPolicy().name(), rule.startValue(), rule.capacity(), rule.confirmation().name());
    }
}
