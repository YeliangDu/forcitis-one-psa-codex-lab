package com.forcitis.one.platform.numbering.application;

import com.forcitis.one.platform.numbering.api.DocumentType;
import com.forcitis.one.platform.numbering.domain.NumberingPattern;
import com.forcitis.one.platform.numbering.domain.NumberingRule;
import com.forcitis.one.platform.numbering.domain.NumberingRuleRepository;
import com.forcitis.one.platform.numbering.api.ResetPolicy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Admin use case: define or change how a document type is numbered. Invalid patterns are rejected by NumberingPattern. */
@Service
public class ConfigureNumberingRule {

    private final NumberingRuleRepository rules;

    ConfigureNumberingRule(NumberingRuleRepository rules) {
        this.rules = rules;
    }

    @Transactional
    public NumberingRule execute(DocumentType type, String pattern, ResetPolicy resetPolicy, long startValue) {
        NumberingRule rule = new NumberingRule(type, new NumberingPattern(pattern, resetPolicy), startValue);
        rules.save(rule);
        return rule;
    }
}
