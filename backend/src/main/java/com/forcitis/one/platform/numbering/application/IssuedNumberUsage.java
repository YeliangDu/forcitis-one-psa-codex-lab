package com.forcitis.one.platform.numbering.application;

import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.organization.workforce.api.LegalEntityNumberUsage;
import com.forcitis.one.platform.numbering.domain.NumberingRuleRepository;
import org.springframework.stereotype.Component;

@Component
class IssuedNumberUsage implements LegalEntityNumberUsage {
    private final NumberingRuleRepository rules;

    IssuedNumberUsage(NumberingRuleRepository rules) {
        this.rules = rules;
    }

    @Override
    public boolean hasIssuedNumbers(LegalEntityId legalEntityId) {
        return rules.hasCounterFor(legalEntityId.value());
    }
}
