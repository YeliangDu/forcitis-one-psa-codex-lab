package com.forcitis.one.organization.workforce.events;

import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.Reason;
import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.kernel.event.Reasoned;
import com.forcitis.one.organization.workforce.api.AttributionSubject;
import java.time.LocalDate;

/** The period that started on periodFrom now ends on newEffectiveTo instead of previousEffectiveTo; the envelope's actor is who corrected it. */
public record LegalEntityAttributionCorrected(EventEnvelope envelope, AttributionSubject subject, LegalEntityId legalEntityId,
                                              LocalDate periodFrom, LocalDate previousEffectiveTo, LocalDate newEffectiveTo,
                                              Reason reason) implements DomainEvent, Reasoned {
}
