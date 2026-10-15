package com.forcitis.one.platform.integration.events;

import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.platform.integration.api.ExternalSource;
import com.forcitis.one.platform.integration.api.OwnObjectId;
import com.forcitis.one.platform.integration.api.SourceObjectKind;

/** A second person confirmed the registered source reference; from its effective day on it is the one used to talk to the external system. */
public record SourceReferenceConfirmed(EventEnvelope envelope, ExternalSource sourceSystem, SourceObjectKind sourceObjectKind, OwnObjectId ownId) implements DomainEvent {
}
