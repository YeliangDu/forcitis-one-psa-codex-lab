package com.forcitis.one.platform.integration.domain;

import com.forcitis.one.kernel.WecomUserId;
import java.util.List;

/** A HANDLED_NOTE delivery replaces the card of targetKey; its description is the note. */
public record OutboundDelivery(OutboundDeliveryId deliveryId, String deduplicationKey, List<WecomUserId> recipients, String title,
                               String description, String targetUrl, DeliveryStatus status, int attemptCount, String responseCode,
                               String handledNote, DeliveryKind kind, String targetKey) {

    public OutboundDelivery {
        recipients = List.copyOf(recipients);
    }
}
