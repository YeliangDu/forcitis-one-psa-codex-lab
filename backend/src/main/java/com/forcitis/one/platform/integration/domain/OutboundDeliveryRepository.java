package com.forcitis.one.platform.integration.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * A change of a claimed delivery applies only while the delivery is still in the status it was claimed in and at the claimed
 * attempt; false means the claim went stale.
 */
public interface OutboundDeliveryRepository {

    /** False when a delivery with the same deduplication key was queued before. */
    boolean insert(OutboundDelivery unsaved, Instant now);

    Optional<OutboundDelivery> findByKey(String deduplicationKey);

    /** Locks the row until the surrounding transaction ends. */
    Optional<OutboundDelivery> lockByKey(String deduplicationKey);

    /** Takes due pending deliveries for one attempt each: counts the attempt and pushes next_attempt_at out by the lease. */
    List<OutboundDelivery> claimDue(Instant now, Duration lease, int limit);

    boolean save(OutboundDelivery claimed);

    /** Writes the handled note and the handled result; applies only to the status and attempt the delivery was loaded with. */
    boolean saveHandled(OutboundDelivery delivery);

    long countPendingCreatedBefore(Instant cutoff);

    long countDeadLetters();
}
