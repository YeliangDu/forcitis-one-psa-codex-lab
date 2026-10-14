package com.forcitis.one.platform.approval.domain;

import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventDraft;
import com.forcitis.one.kernel.event.EventEnvelope;
import java.util.function.Function;

public record PendingEvent(EventDraft draft, Function<EventEnvelope, DomainEvent> assemble) {
}
