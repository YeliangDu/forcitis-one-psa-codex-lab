package com.forcitis.one.platform.numbering.application;

import com.forcitis.one.platform.numbering.api.DocumentType;
import com.forcitis.one.platform.numbering.domain.NumberingPattern;
import com.forcitis.one.platform.numbering.domain.NumberingRule;
import com.forcitis.one.platform.numbering.domain.NumberingRuleRepository;
import com.forcitis.one.platform.numbering.api.ResetPolicy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConfigureNumberingRule {
    private final NumberingRuleRepository rules;

    ConfigureNumberingRule(NumberingRuleRepository rules) {
        this.rules = rules;
    }

    @Transactional
    public NumberingRule execute(DocumentType type, String pattern, ResetPolicy resetPolicy, long startValue) {
        NumberingRule rule = NumberingRule.confirmed(type, new NumberingPattern(pattern, resetPolicy), startValue);
        rules.save(rule);
        return rule;
    }
}
