package com.forcitis.one.platform.integration.domain;

import com.forcitis.one.kernel.WecomUserId;
import java.util.List;

public record NewDelivery(String deduplicationKey, List<WecomUserId> recipients, List<WecomUserId> skippedRecipients, String title,
                          String description, String targetUrl, DeliveryStatus status, String reason) {

    public NewDelivery {
        recipients = List.copyOf(recipients);
        skippedRecipients = List.copyOf(skippedRecipients);
    }
}
