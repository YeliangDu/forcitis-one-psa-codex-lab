package com.forcitis.one.platform.numbering.application;

import com.forcitis.one.platform.numbering.api.NumberingRuleView;
import com.forcitis.one.platform.numbering.api.NumberingTemplateView;
import com.forcitis.one.platform.numbering.domain.NumberingRule;
import com.forcitis.one.platform.numbering.domain.TemplateVersion;
import java.util.Optional;

final class NumberingRuleViews {

    private NumberingRuleViews() {
    }

    static NumberingRuleView of(NumberingRule rule) {
        return new NumberingRuleView(rule.documentType(), rule.documentType().code(), rule.version(),
                rule.confirmation(),
                rule.requiresFinanceConfirmation(), template(rule.current()), rule.pending().map(NumberingRuleViews::template));
    }

    private static NumberingTemplateView template(TemplateVersion version) {
        return new NumberingTemplateView(version.number(), version.pattern().value(), version.pattern().resetPolicy(), version.startValue(),
                version.effectiveFrom(), version.pattern().capacity(), Optional.ofNullable(version.author()), version.authoredAt(), Optional.ofNullable(version.confirmedBy()),
                Optional.ofNullable(version.financeConfirmedBy()));
    }
}
