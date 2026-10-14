package com.forcitis.one.platform.numbering.events;

import com.forcitis.one.kernel.DocumentType;
import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.platform.numbering.api.RuleVersion;

/** A new version of the template was saved and waits for confirmation. */
public record NumberingRuleReconfigured(EventEnvelope envelope, DocumentType documentType, RuleVersion ruleVersion) implements DomainEvent {
}
