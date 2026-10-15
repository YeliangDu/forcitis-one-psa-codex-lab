package com.forcitis.one.platform.integration.events;

import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.platform.integration.api.ExternalSource;
import com.forcitis.one.platform.integration.api.OwnObjectId;
import com.forcitis.one.platform.integration.api.SourceObjectKind;

/** A person registered a source reference; it waits for another person to confirm it. The source id stays in the table, not in the event. */
public record SourceReferenceRegistered(EventEnvelope envelope, ExternalSource sourceSystem, SourceObjectKind sourceObjectKind, OwnObjectId ownId) implements DomainEvent {
}
