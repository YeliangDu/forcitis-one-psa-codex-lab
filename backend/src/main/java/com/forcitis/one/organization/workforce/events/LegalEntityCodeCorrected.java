package com.forcitis.one.organization.workforce.events;

import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.organization.workforce.api.LegalEntityCode;

public record LegalEntityCodeCorrected(EventEnvelope envelope, LegalEntityId legalEntityId, LegalEntityCode previousCode,
                                       LegalEntityCode newCode) implements DomainEvent {
}
