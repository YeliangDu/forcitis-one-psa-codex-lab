package com.forcitis.one.fixture.sample.events;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.kernel.event.NotAudited;
import java.util.List;
import java.util.Map;

/** Deliberately right and wrong events for the catalogue guard's injection tests. */
public final class Samples {
    private Samples() {
    }

    public record GoodEvent(EventEnvelope envelope, UserId userId, List<UserId> others) implements DomainEvent {
    }

    public record NotADomainEvent(EventEnvelope envelope, UserId userId) {
    }

    public static final class MutableEvent implements DomainEvent {
        public String name;

        @Override
        public EventEnvelope envelope() {
            return null;
        }
    }

    public record WithText(EventEnvelope envelope, String note) implements DomainEvent {
    }

    public record WithMap(EventEnvelope envelope, Map<String, UserId> byName) implements DomainEvent {
    }

    public record WithArray(EventEnvelope envelope, UserId[] users) implements DomainEvent {
    }

    public record EnvelopeNotFirst(UserId userId, EventEnvelope envelope) implements DomainEvent {
    }

    @NotAudited(reason = "")
    public record BlankReason(EventEnvelope envelope, UserId userId) implements DomainEvent {
    }

    @NotAudited(reason = "noise")
    public record Quiet(EventEnvelope envelope, UserId userId) implements DomainEvent {
    }
}
