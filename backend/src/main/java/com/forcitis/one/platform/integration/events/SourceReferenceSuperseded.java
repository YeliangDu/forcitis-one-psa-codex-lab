package com.forcitis.one.platform.integration.events;

import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.platform.integration.api.ExternalSource;
import com.forcitis.one.platform.integration.api.OwnObjectId;
import com.forcitis.one.platform.integration.api.SourceObjectKind;
import java.time.LocalDate;

/** The confirmed source reference in effect ended on the day its confirmed successor starts. */
public record SourceReferenceSuperseded(EventEnvelope envelope, ExternalSource sourceSystem, SourceObjectKind sourceObjectKind, OwnObjectId ownId,
                                        LocalDate effectiveTo) implements DomainEvent {
}
