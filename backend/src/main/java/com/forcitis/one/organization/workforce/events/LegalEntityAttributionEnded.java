package com.forcitis.one.organization.workforce.events;

import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventEnvelope;
import java.time.LocalDate;
import com.forcitis.one.organization.workforce.api.AttributionKind;
import com.forcitis.one.organization.workforce.api.AttributionSubjectId;

public record LegalEntityAttributionEnded(EventEnvelope envelope, AttributionKind kind, AttributionSubjectId subjectId, LegalEntityId legalEntityId,
                                          LocalDate effectiveTo) implements DomainEvent {
}
