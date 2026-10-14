package com.forcitis.one.platform.integration.api;

import com.forcitis.one.kernel.WecomUserId;
import java.util.Optional;

/** The only door to WeCom. Sending is asynchronous and durable (outbound delivery, retry, dead letter). */
public interface WecomGateway {

    /** Exchange an OAuth code for the WeCom user id; empty for external contacts or invalid codes. */
    Optional<WecomUserId> resolveWecomUserId(String code, WecomEntry entry);

    /** Queue a card; must be called inside the caller's transaction so it is sent only after commit. */
    void send(WecomCard card);

    /** Replace the card identified by deduplicationKey with a handled note (Q-3: "已由某人处理"). */
    void markHandled(String deduplicationKey, String handledNote);
}
