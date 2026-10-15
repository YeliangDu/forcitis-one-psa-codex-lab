package com.forcitis.one.organization.workforce.events;

import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.Reason;
import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.kernel.event.Reasoned;
import java.time.LocalDate;
import com.forcitis.one.organization.workforce.api.AttributionKind;
import com.forcitis.one.organization.workforce.api.AttributionSubjectId;

/** The period that started on periodFrom now ends on newEffectiveTo instead of previousEffectiveTo; the envelope's actor is who corrected it. */
public record LegalEntityAttributionCorrected(EventEnvelope envelope, AttributionKind kind, AttributionSubjectId subjectId, LegalEntityId legalEntityId,
                                              LocalDate periodFrom, LocalDate previousEffectiveTo, LocalDate newEffectiveTo,
                                              Reason reason) implements DomainEvent, Reasoned {
}
