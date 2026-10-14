package com.forcitis.one.platform.numbering.application;

import com.forcitis.one.kernel.CursorPage;
import com.forcitis.one.kernel.PageRequest;
import com.forcitis.one.platform.numbering.api.NumberingRuleView;
import com.forcitis.one.platform.numbering.domain.NumberingRuleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListNumberingRules {
    private final NumberingRuleRepository rules;

    ListNumberingRules(NumberingRuleRepository rules) {
        this.rules = rules;
    }

    @Transactional(readOnly = true)
    public CursorPage<NumberingRuleView> execute(PageRequest page) {
        return page.slice(rules.findAllActive().stream().map(NumberingRuleViews::of).toList(), view -> view.documentType().name(),
                "numbering-rules");
    }
}
