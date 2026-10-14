package com.forcitis.one.platform.integration.domain;

import com.forcitis.one.kernel.WecomUserId;
import java.util.List;

public record OutboundDelivery(OutboundDeliveryId deliveryId, String deduplicationKey, List<WecomUserId> recipients, String title,
                               String description, String targetUrl, DeliveryStatus status, int attemptCount, String responseCode,
                               String handledNote) {

    public OutboundDelivery {
        recipients = List.copyOf(recipients);
    }
}
