package com.forcitis.one.platform.numbering.events;

import com.forcitis.one.kernel.DocumentType;
import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventEnvelope;

public record NumberingRuleReconfigured(EventEnvelope envelope, DocumentType documentType) implements DomainEvent {
}
