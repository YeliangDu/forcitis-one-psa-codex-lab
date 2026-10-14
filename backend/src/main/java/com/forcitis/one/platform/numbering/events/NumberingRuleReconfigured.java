package com.forcitis.one.platform.numbering.events;

import com.forcitis.one.kernel.DocumentType;
import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.platform.numbering.api.RuleVersion;
import java.time.LocalDate;

/** ruleVersion is the template version in force from effectiveFrom, which is also the envelope's businessDate. */
public record NumberingRuleReconfigured(EventEnvelope envelope, DocumentType documentType, RuleVersion ruleVersion, LocalDate effectiveFrom)
        implements DomainEvent {
}
