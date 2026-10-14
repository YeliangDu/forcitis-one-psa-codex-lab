package com.forcitis.one.platform.numbering.application;

import com.forcitis.one.platform.numbering.api.DocumentType;
import com.forcitis.one.platform.numbering.api.NumberingRuleView;
import com.forcitis.one.platform.numbering.domain.NumberingRuleRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Query side of the numbering rule page: the active rule of one document type. */
@Service
public class FindNumberingRule {

    private final NumberingRuleRepository rules;

    FindNumberingRule(NumberingRuleRepository rules) {
        this.rules = rules;
    }

    @Transactional(readOnly = true)
    public Optional<NumberingRuleView> execute(DocumentType type) {
        return rules.findActive(type).map(NumberingRuleViews::of);
    }
}
