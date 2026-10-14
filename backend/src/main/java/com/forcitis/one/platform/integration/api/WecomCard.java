package com.forcitis.one.platform.integration.api;

import com.forcitis.one.kernel.WecomUserId;
import java.util.List;
import java.util.Objects;

/** deduplicationKey makes sending idempotent and lets the card be replaced later (e.g. "approval-task:{taskId}"). */
public record WecomCard(List<WecomUserId> wecomUserIds, String title, String description, String targetUrl,
                        String deduplicationKey) {

    public WecomCard {
        wecomUserIds = List.copyOf(wecomUserIds);
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(targetUrl, "targetUrl");
        Objects.requireNonNull(deduplicationKey, "deduplicationKey");
    }
}
