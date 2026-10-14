package com.forcitis.one.platform.approval.application;

import com.forcitis.one.kernel.event.EventEnvelopes;
import com.forcitis.one.platform.approval.domain.PendingEvent;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
class ApprovalEvents {

    private final EventEnvelopes envelopes;
    private final ApplicationEventPublisher publisher;

    ApprovalEvents(EventEnvelopes envelopes, ApplicationEventPublisher publisher) {
        this.envelopes = envelopes;
        this.publisher = publisher;
    }

    void publish(List<PendingEvent> pending) {
        for (PendingEvent event : pending) {
            publisher.publishEvent(event.assemble().apply(envelopes.wrap(event.draft())));
        }
    }
}
