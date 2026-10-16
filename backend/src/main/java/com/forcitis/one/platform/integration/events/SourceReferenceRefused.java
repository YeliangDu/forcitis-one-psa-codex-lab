package com.forcitis.one.platform.integration.events;

import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.platform.integration.api.ExternalSource;
import com.forcitis.one.platform.integration.api.SourceObjectKind;

/** Someone tried to register by hand a source reference of a kind that only the sync or the import writes; nothing was stored. */
public record SourceReferenceRefused(EventEnvelope envelope, ExternalSource sourceSystem, SourceObjectKind sourceObjectKind) implements DomainEvent {
}
