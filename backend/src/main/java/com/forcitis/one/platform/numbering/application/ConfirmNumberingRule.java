package com.forcitis.one.platform.numbering.application;

import com.forcitis.one.kernel.BusinessZone;
import com.forcitis.one.kernel.DocumentTypeCode;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.DomainEvents;
import com.forcitis.one.platform.numbering.api.NumberingError;
import com.forcitis.one.platform.numbering.api.NumberingRuleView;
import com.forcitis.one.platform.numbering.api.RuleVersion;
import com.forcitis.one.platform.numbering.domain.NumberingRule;
import com.forcitis.one.platform.numbering.domain.NumberingRuleRepository;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The business confirmation (four eyes: not the author of the version). */
@Service
public class ConfirmNumberingRule {
    private final NumberingRuleRepository rules;
    private final DomainEvents events;
    private final Clock clock;
    private final BusinessZone zone;

    ConfirmNumberingRule(NumberingRuleRepository rules, DomainEvents events, Clock clock, BusinessZone zone) {
        this.zone = zone;
        this.rules = rules;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public NumberingRuleView execute(DocumentTypeCode type, UserId confirmer, RuleVersion seen) {
        NumberingRule rule = rules.findActive(type).orElseThrow(() -> new DomainException(NumberingError.PLATFORM_NUMBERING_RULE_NOT_FOUND,
                ErrorKind.NOT_FOUND, "no active numbering rule for " + type));
        rule.confirm(confirmer, clock.instant(), zone.today(clock), seen);
        rules.save(rule);
        events.publish(rule);
        return NumberingRuleViews.of(rule);
    }
}
