package com.forcitis.one.platform.integration.api;

import com.forcitis.one.kernel.WecomUserId;
import java.util.List;
import java.util.Objects;

/** subject makes sending idempotent and lets the card be replaced later by a note about the same subject. */
public record WecomCard(List<WecomUserId> wecomUserIds, String title, String description, String targetUrl, DeliverySubject subject) {

    public WecomCard {
        wecomUserIds = List.copyOf(wecomUserIds);
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(targetUrl, "targetUrl");
        Objects.requireNonNull(subject, "subject");
    }
}
