package com.forcitis.one.platform.audit.application;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.ActorType;
import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.kernel.event.NotAudited;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.audit.domain.AuditEntryRepository;
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
        UserId actor = envelope.actorType() == ActorType.USER ? new UserId(Long.parseLong(envelope.actorId())) : null;
        String detail = envelope.actorType() == ActorType.USER ? null : "by " + envelope.actorId();
        AuditEntry entry = new AuditEntry(envelope.eventType(), actor, null,
                envelope.aggregateType() + ":" + envelope.aggregateId(), detail);
        if (!entries.saveForEvent(entry, envelope.occurredAt(), envelope.eventId(), envelope.correlationId())) {
            LOG.info("platform.audit event {} was audited before: ignored", envelope.eventId());
        }
    }
}
