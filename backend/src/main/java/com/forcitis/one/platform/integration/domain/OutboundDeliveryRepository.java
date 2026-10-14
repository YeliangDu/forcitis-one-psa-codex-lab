package com.forcitis.one.platform.integration.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Every state change of a claimed delivery applies only while it is still PENDING at the claimed attempt; false means the claim went stale. */
public interface OutboundDeliveryRepository {

    boolean insert(NewDelivery delivery, Instant now);

    Optional<OutboundDelivery> findByKey(String deduplicationKey);

    /** Locks the row until the surrounding transaction ends. */
    Optional<OutboundDelivery> lockByKey(String deduplicationKey);

    /** Takes due pending deliveries for one attempt each: counts the attempt and pushes next_attempt_at out by the lease. */
    List<OutboundDelivery> claimDue(Instant now, Duration lease, int limit);

    boolean markSent(OutboundDelivery claimed, String responseCode, Instant now);

    boolean markRetry(OutboundDelivery claimed, Instant nextAttemptAt, String error);

    boolean markDeadLetter(OutboundDelivery claimed, String error);

    boolean markSkipped(OutboundDelivery claimed, String reason);

    void recordHandledNote(String deduplicationKey, String note);

    void recordHandledResult(String deduplicationKey, String result);

    /** Queues the replacement of a sent card as a delivery of its own; false when it was queued already. */
    boolean queueHandledNote(OutboundDelivery sentCard, String note, Instant now);

    long countPendingCreatedBefore(Instant cutoff);

    long countDeadLetters();
}
