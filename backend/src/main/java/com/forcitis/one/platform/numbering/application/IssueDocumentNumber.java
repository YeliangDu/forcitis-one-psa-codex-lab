package com.forcitis.one.platform.numbering.application;

import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.organization.workforce.api.LegalEntityCode;
import com.forcitis.one.organization.workforce.api.LegalEntityDirectory;
import com.forcitis.one.platform.numbering.api.DocumentNumber;
import com.forcitis.one.platform.numbering.api.DocumentNumbers;
import com.forcitis.one.platform.numbering.api.DocumentType;
import com.forcitis.one.platform.numbering.domain.NumberingRule;
import com.forcitis.one.platform.numbering.domain.NumberingRuleRepository;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
class IssueDocumentNumber implements DocumentNumbers {

    private final NumberingRuleRepository rules;
    private final LegalEntityDirectory legalEntities;

    IssueDocumentNumber(NumberingRuleRepository rules, LegalEntityDirectory legalEntities) {
        this.rules = rules;
        this.legalEntities = legalEntities;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public DocumentNumber next(DocumentType type, LegalEntityId legalEntity, LocalDate businessDate) {
        NumberingRule rule = rules.findActive(type)
                .orElseThrow(() -> new IllegalStateException("No active numbering rule for " + type));
        LegalEntityCode legalEntityCode = rule.pattern().usesLegalEntityCode()
                ? legalEntities.findCode(legalEntity).orElseThrow(() -> new IllegalStateException("Legal entity " + legalEntity.value() + " has no code"))
                : null;
        String periodKey = rule.pattern().resetPolicy().periodKey(businessDate);
        long sequence = rules.reserveNext(type, legalEntity, periodKey, rule.startValue());
        return new DocumentNumber(rule.pattern().render(type.code().orElse(null), legalEntityCode, businessDate, sequence));
    }
}
