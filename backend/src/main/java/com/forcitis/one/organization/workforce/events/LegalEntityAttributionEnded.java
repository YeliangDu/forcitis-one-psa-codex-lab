package com.forcitis.one.organization.workforce.events;

import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.organization.workforce.api.AttributionSubject;
import java.time.LocalDate;

public record LegalEntityAttributionEnded(EventEnvelope envelope, AttributionSubject subject, LegalEntityId legalEntityId,
                                          LocalDate effectiveTo) implements DomainEvent {
}
