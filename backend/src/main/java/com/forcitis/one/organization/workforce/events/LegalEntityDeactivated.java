package com.forcitis.one.organization.workforce.events;

import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventEnvelope;

public record LegalEntityDeactivated(EventEnvelope envelope, LegalEntityId legalEntityId) implements DomainEvent {
}
