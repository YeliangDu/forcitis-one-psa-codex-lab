package com.forcitis.one.platform.numbering.events;

import com.forcitis.one.kernel.DocumentType;
import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.platform.numbering.api.RuleVersion;

/** The version has every confirmation it needs and from now on issues the numbers. */
public record NumberingRuleConfirmed(EventEnvelope envelope, DocumentType documentType, RuleVersion ruleVersion) implements DomainEvent {
}
