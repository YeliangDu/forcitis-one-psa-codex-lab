package com.forcitis.one.platform.numbering.application;

import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.organization.workforce.api.LegalEntityCode;
import com.forcitis.one.organization.workforce.api.LegalEntityDirectory;
import com.forcitis.one.platform.numbering.api.DocumentNumber;
import com.forcitis.one.platform.numbering.api.DocumentNumbers;
import com.forcitis.one.platform.numbering.api.NumberingError;
import com.forcitis.one.kernel.DocumentType;
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
                .orElseThrow(() -> new DomainException(NumberingError.PLATFORM_NUMBERING_RULE_NOT_FOUND, ErrorKind.UNPROCESSABLE,
                        "No active numbering rule for " + type));
        LegalEntityCode legalEntityCode = legalEntities.lockActiveCode(legalEntity)
                .orElseThrow(() -> new DomainException(NumberingError.PLATFORM_NUMBERING_LEGAL_ENTITY_UNAVAILABLE, ErrorKind.UNPROCESSABLE,
                        "Legal entity " + legalEntity.value() + " does not exist or is deactivated"));
        long counterScope = rule.pattern().usesLegalEntityCode() ? legalEntity.value() : NumberingRuleRepository.GLOBAL_COUNTER_SCOPE;
        String periodKey = rule.pattern().resetPolicy().periodKey(businessDate);
        long sequence = rules.reserveNext(type, counterScope, periodKey, rule.startValue());
        return new DocumentNumber(rule.pattern().render(type.code().orElse(null), rule.pattern().usesLegalEntityCode() ? legalEntityCode : null, businessDate, sequence));
    }
}
