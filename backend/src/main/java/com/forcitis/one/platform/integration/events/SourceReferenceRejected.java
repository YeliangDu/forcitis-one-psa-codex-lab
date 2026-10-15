package com.forcitis.one.platform.integration.events;

import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.platform.integration.api.ExternalSource;
import com.forcitis.one.platform.integration.api.OwnObjectId;
import com.forcitis.one.platform.integration.api.SourceObjectKind;

/** A second person rejected the registered source reference; nothing that was in effect changed. */
public record SourceReferenceRejected(EventEnvelope envelope, ExternalSource sourceSystem, SourceObjectKind sourceObjectKind, OwnObjectId ownId) implements DomainEvent {
}
