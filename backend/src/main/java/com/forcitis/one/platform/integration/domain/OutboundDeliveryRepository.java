package com.forcitis.one.platform.integration.domain;

import com.forcitis.one.platform.integration.api.DeliverySubject;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * A change of a claimed delivery applies only while the delivery is still in the status it was claimed in and at the claimed
 * attempt; false means the claim went stale.
 */
public interface OutboundDeliveryRepository {

    /** False when the same card (same subject) or the same note (same replaced delivery) was queued before. */
    boolean insert(OutboundDelivery unsaved, Instant now);

    Optional<OutboundDelivery> findById(UUID deliveryId);

    /** Locks the row until the surrounding transaction ends. */
    Optional<OutboundDelivery> lockById(UUID deliveryId);

    /** The card queued for the subject; a handled note is not a card. */
    Optional<OutboundDelivery> findCard(DeliverySubject subject);

    /** Locks the card of the subject until the surrounding transaction ends. */
    Optional<OutboundDelivery> lockCard(DeliverySubject subject);

    /** Takes due pending deliveries for one attempt each: counts the attempt and pushes next_attempt_at out by the lease. */
    List<OutboundDelivery> claimDue(Instant now, Duration lease, int limit);

    boolean save(OutboundDelivery claimed);

    /** Writes the handled note and the handled result; applies only to the status and attempt the delivery was loaded with. */
    boolean saveHandled(OutboundDelivery delivery);

    long countPendingCreatedBefore(Instant cutoff);

    long countDeadLetters();
}
