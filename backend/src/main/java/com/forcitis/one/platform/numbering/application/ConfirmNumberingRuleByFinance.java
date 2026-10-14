package com.forcitis.one.platform.numbering.application;

import com.forcitis.one.kernel.BusinessZone;
import com.forcitis.one.kernel.DocumentType;
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

/** The finance confirmation a voucher template needs besides the business one (D-1a-157). */
@Service
public class ConfirmNumberingRuleByFinance {
    private final NumberingRuleRepository rules;
    private final DomainEvents events;
    private final Clock clock;
    private final BusinessZone zone;

    ConfirmNumberingRuleByFinance(NumberingRuleRepository rules, DomainEvents events, Clock clock, BusinessZone zone) {
        this.zone = zone;
        this.rules = rules;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public NumberingRuleView execute(DocumentType type, UserId confirmer, RuleVersion seen) {
        NumberingRule rule = rules.findActive(type).orElseThrow(() -> new DomainException(NumberingError.PLATFORM_NUMBERING_RULE_NOT_FOUND,
                ErrorKind.NOT_FOUND, "no active numbering rule for " + type));
        rule.confirmByFinance(confirmer, clock.instant(), zone.today(clock), seen);
        rules.save(rule);
        events.publish(rule);
        return NumberingRuleViews.of(rule);
    }
}
