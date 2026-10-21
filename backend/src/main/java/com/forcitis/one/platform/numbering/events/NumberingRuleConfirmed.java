package com.forcitis.one.platform.numbering.events;

import com.forcitis.one.kernel.DocumentTypeCode;
import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.platform.numbering.api.RuleVersion;
import java.time.LocalDate;

/** The version has every confirmation it needs and from now on issues the numbers. */
public record NumberingRuleConfirmed(EventEnvelope envelope, DocumentTypeCode documentType, RuleVersion ruleVersion, LocalDate effectiveFrom) implements DomainEvent {
}
