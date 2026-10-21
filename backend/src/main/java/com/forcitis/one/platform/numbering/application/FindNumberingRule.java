package com.forcitis.one.platform.numbering.application;

import com.forcitis.one.kernel.DocumentTypeCode;
import com.forcitis.one.platform.numbering.api.NumberingRuleView;
import com.forcitis.one.platform.numbering.domain.NumberingRuleRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FindNumberingRule {
    private final NumberingRuleRepository rules;

    FindNumberingRule(NumberingRuleRepository rules) {
        this.rules = rules;
    }

    @Transactional(readOnly = true)
    public Optional<NumberingRuleView> execute(DocumentTypeCode type) {
        return rules.findActive(type).map(NumberingRuleViews::of);
    }
}
