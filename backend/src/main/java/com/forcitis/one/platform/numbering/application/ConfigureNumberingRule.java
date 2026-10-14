package com.forcitis.one.platform.numbering.application;

import com.forcitis.one.kernel.DocumentType;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.event.DomainEvents;
import com.forcitis.one.platform.numbering.api.NumberingError;
import com.forcitis.one.platform.numbering.api.NumberingRuleView;
import com.forcitis.one.platform.numbering.api.ResetPolicy;
import com.forcitis.one.platform.numbering.domain.NumberingPattern;
import com.forcitis.one.platform.numbering.domain.NumberingRule;
import com.forcitis.one.platform.numbering.domain.NumberingRuleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConfigureNumberingRule {
    private final NumberingRuleRepository rules;
    private final DomainEvents events;

    ConfigureNumberingRule(NumberingRuleRepository rules, DomainEvents events) {
        this.rules = rules;
        this.events = events;
    }

    @Transactional
    public NumberingRuleView execute(DocumentType type, String pattern, ResetPolicy resetPolicy, long startValue) {
        NumberingRule rule = rules.findActive(type).orElseThrow(() -> new DomainException(NumberingError.PLATFORM_NUMBERING_RULE_NOT_FOUND,
                ErrorKind.NOT_FOUND, "no active numbering rule for " + type));
        try {
            rule.reconfigure(new NumberingPattern(pattern, resetPolicy), startValue);
        } catch (IllegalArgumentException | NullPointerException invalid) {
            throw new DomainException(NumberingError.PLATFORM_NUMBERING_RULE_INVALID, ErrorKind.UNPROCESSABLE, invalid.getMessage());
        }
        rules.save(rule);
        events.publish(rule);
        return NumberingRuleViews.of(rule);
    }
}
