package com.forcitis.one.platform.numbering.application;

import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.organization.workforce.api.LegalEntityCode;
import com.forcitis.one.organization.workforce.api.LegalEntityDirectory;
import com.forcitis.one.platform.numbering.api.DocumentNumber;
import com.forcitis.one.platform.numbering.api.DocumentNumbers;
import com.forcitis.one.kernel.DocumentType;
import com.forcitis.one.platform.numbering.api.NumberingError;
import com.forcitis.one.platform.numbering.domain.NumberingRule;
import com.forcitis.one.platform.numbering.domain.TemplateVersion;
import com.forcitis.one.platform.numbering.domain.NumberingRuleRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
class IssueDocumentNumber implements DocumentNumbers {

    private static final Logger log = LoggerFactory.getLogger(IssueDocumentNumber.class);

    private final NumberingRuleRepository rules;
    private final LegalEntityDirectory legalEntities;
    private final MeterRegistry meters;

    IssueDocumentNumber(NumberingRuleRepository rules, LegalEntityDirectory legalEntities, MeterRegistry meters) {
        this.rules = rules;
        this.legalEntities = legalEntities;
        this.meters = meters;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public DocumentNumber next(DocumentType type, LegalEntityId legalEntity, LocalDate businessDate) {
        NumberingRule rule = rules.findActive(type)
                .orElseThrow(() -> new IllegalStateException("No active numbering rule for " + type));
        TemplateVersion template = rule.inForceOn(businessDate).orElseThrow(() -> notConfirmed(type, businessDate));
        LegalEntityCode legalEntityCode = legalEntities.findActiveCode(legalEntity)
                .orElseThrow(() -> new IllegalStateException("Legal entity " + legalEntity.value() + " does not exist or is deactivated"));
        long counterScope = template.pattern().usesLegalEntityCode() ? legalEntity.value() : NumberingRuleRepository.GLOBAL_COUNTER_SCOPE;
        String periodKey = template.pattern().resetPolicy().periodKey(businessDate);
        long sequence = rules.reserveNext(type, counterScope, periodKey, template.startValue());
        return new DocumentNumber(template.pattern().render(type.code().orElse(null), template.pattern().usesLegalEntityCode() ? legalEntityCode : null, businessDate, sequence));
    }

    private DomainException notConfirmed(DocumentType type, LocalDate businessDate) {
        meters.counter("forcitis.platform.numbering.issue.failed").increment();
        log.error("ALERT platform.numbering no number issued for {} on {}: the rule has no confirmed template in force", type, businessDate);
        return new DomainException(NumberingError.PLATFORM_NUMBERING_RULE_NOT_CONFIRMED, ErrorKind.UNPROCESSABLE,
                "the numbering rule of " + type + " has no confirmed template in force on " + businessDate + ", so it issues no number");
    }
}
