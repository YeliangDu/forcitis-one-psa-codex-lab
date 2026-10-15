package com.forcitis.one.platform.audit.application;

import com.forcitis.one.kernel.SystemActors;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.Uuids;
import com.forcitis.one.kernel.event.ActorType;
import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.kernel.event.NotAudited;
import com.forcitis.one.kernel.event.Reasoned;
import com.forcitis.one.platform.audit.api.AuditDetail;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.audit.domain.AuditEntryRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Business state changes reach the audit trail through the events their aggregates record: any domain event, unless its record
 * says @NotAudited. The event id is the idempotency key, so a redelivered event writes nothing twice.
 */
@Component
class AuditDomainEvents {
    private static final Logger LOG = LoggerFactory.getLogger(AuditDomainEvents.class);

    private final AuditEntryRepository entries;

    AuditDomainEvents(AuditEntryRepository entries) {
        this.entries = entries;
    }

    @ApplicationModuleListener
    public void on(DomainEvent event) {
        if (event.getClass().isAnnotationPresent(NotAudited.class)) {
            return;
        }
        EventEnvelope envelope = event.envelope();
        UserId actor = envelope.actorType() == ActorType.USER ? UserId.parse(envelope.actorId()) : null;
        String detail = event instanceof AuditDetail detailed ? detailed.auditDetail()
                : envelope.actorType() == ActorType.USER ? null : "by " + systemActorName(envelope);
        String reason = event instanceof Reasoned reasoned && reasoned.reason() != null ? reasoned.reason().value() : null;
        AuditEntry entry = new AuditEntry(envelope.eventType(), actor, null,
                envelope.aggregateType() + ":" + envelope.aggregateId(), detail).withChange(reason, null, null);
        if (!entries.saveForEvent(entry, envelope.occurredAt(), envelope.eventId(), envelope.correlationId())) {
            LOG.info("platform.audit event {} was audited before: ignored", envelope.eventId());
        }
    }

    private static String systemActorName(EventEnvelope envelope) {
        UUID id = Uuids.parseCanonical(envelope.actorId());
        return SystemActors.nameOf(id).orElseGet(() -> {
            LOG.error("ALERT platform.audit event {} names system actor {} that this instance does not know: audited by its id",
                    envelope.eventId(), id);
            return id.toString();
        });
    }
}
