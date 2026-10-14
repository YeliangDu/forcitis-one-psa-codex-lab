package com.forcitis.one.platform.integration.api;

import com.forcitis.one.kernel.WecomUserId;
import java.util.Optional;

/** Sending is asynchronous and durable (outbound delivery, retry, dead letter). */
public interface WecomGateway {

    Optional<WecomUserId> resolveWecomUserId(String code, WecomEntry entry);

    /** Must be called inside the caller's transaction so it is sent only after commit. */
    void send(WecomCard card);

    void markHandled(String deduplicationKey, String handledNote);
}
