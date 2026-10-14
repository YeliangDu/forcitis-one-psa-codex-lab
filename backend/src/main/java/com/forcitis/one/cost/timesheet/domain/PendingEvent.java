package com.forcitis.one.cost.timesheet.domain;

import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventDraft;
import com.forcitis.one.kernel.event.EventEnvelope;
import java.util.function.Function;

/** An event the aggregate recorded; the application layer wraps the draft into an envelope and publishes what assemble builds. */
public record PendingEvent(EventDraft draft, Function<EventEnvelope, DomainEvent> assemble) {
}
