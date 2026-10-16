package com.forcitis.one.platform.integration.api;

import com.forcitis.one.kernel.Uuids;
import com.forcitis.one.kernel.ValueObject;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * What a card is about and why it is sent: the object's kind and own id, the purpose, and, for a purpose that can recur for one
 * object, the event that caused this card. One card is sent for one subject; the recipients are not part of it.
 */
@ValueObject
public record DeliverySubject(DeliverySubjectKind kind, UUID id, DeliveryPurpose purpose, Optional<UUID> triggerEvent) {

    public DeliverySubject {
        Objects.requireNonNull(kind, "kind");
        id = Uuids.requireKey(id, "DeliverySubject id");
        Objects.requireNonNull(purpose, "purpose");
        Objects.requireNonNull(triggerEvent, "triggerEvent");
        triggerEvent.ifPresent(event -> Uuids.requireKey(event, "DeliverySubject trigger event id"));
        if (purpose.subjectKind() != kind) {
            throw new IllegalArgumentException(purpose + " is about " + purpose.subjectKind() + ", not " + kind);
        }
        if (purpose.perEvent() != triggerEvent.isPresent()) {
            throw new IllegalArgumentException(purpose + (purpose.perEvent() ? " needs" : " takes no") + " trigger event id");
        }
    }

    public static DeliverySubject of(DeliveryPurpose purpose, UUID id) {
        return new DeliverySubject(purpose.subjectKind(), id, purpose, Optional.empty());
    }

    public static DeliverySubject of(DeliveryPurpose purpose, UUID id, UUID triggerEventId) {
        return new DeliverySubject(purpose.subjectKind(), id, purpose, Optional.of(triggerEventId));
    }

    @Override
    public String toString() {
        return kind + ":" + id + ":" + purpose + triggerEvent.map(event -> ":" + event).orElse("");
    }
}
