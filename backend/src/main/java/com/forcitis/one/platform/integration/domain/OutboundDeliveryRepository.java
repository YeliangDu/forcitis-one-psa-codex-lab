package com.forcitis.one.platform.integration.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OutboundDeliveryRepository {

    boolean insert(NewDelivery delivery, Instant now);

    Optional<OutboundDelivery> findByKey(String deduplicationKey);

    /** Takes due pending deliveries for one attempt each: counts the attempt and pushes next_attempt_at out by the lease. */
    List<OutboundDelivery> claimDue(Instant now, Duration lease, int limit);

    void markSent(OutboundDeliveryId deliveryId, String responseCode, Instant now);

    void markRetry(OutboundDeliveryId deliveryId, Instant nextAttemptAt, String error);

    void markDeadLetter(OutboundDeliveryId deliveryId, String error);

    void markSkipped(OutboundDeliveryId deliveryId, String reason);

    void recordHandled(String deduplicationKey, String note, String result);

    long countPendingCreatedBefore(Instant cutoff);

    long countDeadLetters();
}
