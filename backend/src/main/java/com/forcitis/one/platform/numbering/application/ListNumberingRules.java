package com.forcitis.one.platform.numbering.application;

import com.forcitis.one.platform.numbering.api.NumberingRuleView;
import com.forcitis.one.platform.numbering.domain.NumberingRuleRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListNumberingRules {
    private final NumberingRuleRepository rules;

    ListNumberingRules(NumberingRuleRepository rules) {
        this.rules = rules;
    }

    @Transactional(readOnly = true)
    public List<NumberingRuleView> execute() {
        return rules.findAllActive().stream().map(NumberingRuleViews::of).toList();
    }
}
