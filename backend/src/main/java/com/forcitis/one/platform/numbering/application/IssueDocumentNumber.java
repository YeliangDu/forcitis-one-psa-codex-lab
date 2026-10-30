package com.forcitis.one.platform.numbering.application;

import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.organization.workforce.api.LegalEntityCode;
import com.forcitis.one.organization.workforce.api.LegalEntityDirectory;
import com.forcitis.one.platform.numbering.api.DocumentNumber;
import com.forcitis.one.platform.numbering.api.DocumentNumbers;
import com.forcitis.one.platform.numbering.api.IssuedNumber;
import com.forcitis.one.kernel.DocumentTypeCode;
import com.forcitis.one.platform.numbering.api.NumberingError;
import com.forcitis.one.platform.numbering.domain.CounterScope;
import com.forcitis.one.platform.numbering.domain.NumberingRule;
import com.forcitis.one.platform.numbering.domain.TemplateVersion;
import com.forcitis.one.platform.numbering.domain.NumberingRuleRepository;
import com.forcitis.one.platform.numbering.domain.ReservedValue;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.jspecify.annotations.Nullable;
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
    public IssuedNumber next(DocumentTypeCode type, LegalEntityId legalEntity, LocalDate businessDate) {
        return issue(type, legalEntity, businessDate);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public IssuedNumber nextGlobal(DocumentTypeCode type, LocalDate businessDate) {
        return issue(type, null, businessDate);
    }

    /** legalEntity is null for nextGlobal: the template must then have no {LE}. */
    private IssuedNumber issue(DocumentTypeCode type, @Nullable LegalEntityId legalEntity, LocalDate businessDate) {
        NumberingRule rule = rules.findActive(type)
                .orElseThrow(() -> new DomainException(NumberingError.PLATFORM_NUMBERING_RULE_NOT_FOUND, ErrorKind.UNPROCESSABLE,
                        "No active numbering rule for " + type));
        if (!rule.type().isInForceOn(businessDate)) {
            throw new DomainException(NumberingError.PLATFORM_NUMBERING_DOCUMENT_TYPE_UNREGISTERED, ErrorKind.UNPROCESSABLE,
                    "document type " + type + " is not in force on " + businessDate);
        }
        TemplateVersion template = rule.inForceOn(businessDate).orElseThrow(() -> notConfirmed(type, businessDate));
        if (legalEntity == null && template.pattern().usesLegalEntityCode()) {
            throw new DomainException(NumberingError.PLATFORM_NUMBERING_RULE_INVALID, ErrorKind.UNPROCESSABLE,
                    "the template of " + type + " has {LE}, but no legal entity was given: a number without a legal entity needs a template without it");
        }
        LegalEntityCode legalEntityCode = legalEntity == null ? null : legalEntities.lockActiveCode(legalEntity)
                .orElseThrow(() -> new DomainException(NumberingError.PLATFORM_NUMBERING_LEGAL_ENTITY_UNAVAILABLE, ErrorKind.UNPROCESSABLE,
                        "Legal entity " + legalEntity.value() + " does not exist or is deactivated"));
        CounterScope counterScope = legalEntity != null && template.pattern().usesLegalEntityCode() ? CounterScope.ofLegalEntity(legalEntity) : CounterScope.GLOBAL;
        String periodKey = template.pattern().resetPolicy().periodKey(businessDate);
        ReservedValue reserved = rules.reserveNext(type, counterScope, periodKey, template.startValue());
        return new IssuedNumber(new DocumentNumber(template.pattern().render(rule.type().numberCode().orElse(null), template.pattern().usesLegalEntityCode() ? legalEntityCode : null, businessDate, reserved.value())),
                reserved.counterId(), template.id());
    }

    private DomainException notConfirmed(DocumentTypeCode type, LocalDate businessDate) {
        meters.counter("forcitis.platform.numbering.issue.failed").increment();
        log.error("ALERT platform.numbering no number issued for {} on {}: the rule has no confirmed template in force", type, businessDate);
        return new DomainException(NumberingError.PLATFORM_NUMBERING_RULE_NOT_CONFIRMED, ErrorKind.UNPROCESSABLE,
                "the numbering rule of " + type + " has no confirmed template in force on " + businessDate + ", so it issues no number");
    }
}
