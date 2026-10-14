package com.forcitis.one.platform.numbering.application;

import com.forcitis.one.platform.numbering.api.NumberingRuleView;
import com.forcitis.one.platform.numbering.domain.NumberingRule;

final class NumberingRuleViews {

    private NumberingRuleViews() {
    }

    static NumberingRuleView of(NumberingRule rule) {
        return new NumberingRuleView(rule.documentType(), rule.documentType().code(), rule.pattern().value(),
                rule.pattern().resetPolicy(), rule.startValue(), rule.pattern().capacity(), rule.confirmation(), rule.version());
    }
}
