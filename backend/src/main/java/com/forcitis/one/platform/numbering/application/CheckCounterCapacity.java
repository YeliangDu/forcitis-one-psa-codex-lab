package com.forcitis.one.platform.numbering.application;

import com.forcitis.one.platform.numbering.domain.NumberingRule;
import com.forcitis.one.platform.numbering.domain.NumberingRuleRepository;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CheckCounterCapacity {
    private final NumberingRuleRepository rules;

    CheckCounterCapacity(NumberingRuleRepository rules) {
        this.rules = rules;
    }

    @Transactional(readOnly = true)
    public long execute(BigDecimal warningRatio) {
        long nearCapacity = 0;
        for (NumberingRule rule : rules.findAllActive()) {
            if (rule.inForce().isEmpty()) {
                continue;
            }
            BigDecimal threshold = BigDecimal.valueOf(rule.inForce().get().pattern().capacity()).multiply(warningRatio);
            if (BigDecimal.valueOf(rules.maxLastValue(rule.documentType())).compareTo(threshold) >= 0) {
                nearCapacity++;
            }
        }
        return nearCapacity;
    }
}
