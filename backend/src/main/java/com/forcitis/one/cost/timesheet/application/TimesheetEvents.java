package com.forcitis.one.cost.timesheet.application;

import com.forcitis.one.cost.timesheet.domain.PendingEvent;
import com.forcitis.one.kernel.event.EventEnvelopes;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/** Wraps what the aggregate recorded and hands it to the event publication registry in the caller's transaction. */
@Component
class TimesheetEvents {

    private final EventEnvelopes envelopes;
    private final ApplicationEventPublisher publisher;

    TimesheetEvents(EventEnvelopes envelopes, ApplicationEventPublisher publisher) {
        this.envelopes = envelopes;
        this.publisher = publisher;
    }

    void publish(List<PendingEvent> pending) {
        for (PendingEvent event : pending) {
            publisher.publishEvent(event.assemble().apply(envelopes.wrap(event.draft())));
        }
    }
}
