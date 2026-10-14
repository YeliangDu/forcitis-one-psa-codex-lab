package com.forcitis.one.platform.integration.application;

import com.forcitis.one.platform.integration.domain.OutboundDelivery;
import com.forcitis.one.platform.integration.domain.OutboundDeliveryRepository;
import com.forcitis.one.platform.integration.domain.RetrySchedule;
import com.forcitis.one.platform.integration.domain.WecomClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/** Not transactional on purpose: the WeCom call must not hold a database transaction; a lease makes a crash mean a resend, never a lost card. */
@Service
public class DeliverWecomCards {

    private static final Logger log = LoggerFactory.getLogger(DeliverWecomCards.class);
    static final int BATCH_SIZE = 20;
    static final Duration LEASE = Duration.ofMinutes(2);

    private final OutboundDeliveryRepository deliveries;
    private final WecomClient wecom;
    private final Clock clock;

    DeliverWecomCards(OutboundDeliveryRepository deliveries, WecomClient wecom, ObjectProvider<Clock> clock) {
        this.deliveries = deliveries;
        this.wecom = wecom;
        this.clock = Clocks.from(clock);
    }

    public int execute() {
        List<OutboundDelivery> due = deliveries.claimDue(clock.instant(), LEASE, BATCH_SIZE);
        due.forEach(this::deliver);
        return due.size();
    }

    private void deliver(OutboundDelivery delivery) {
        if (delivery.handledNote() != null) {
            deliveries.markSkipped(delivery.deliveryId(), "handled before it was delivered");
            return;
        }
        try {
            Optional<String> responseCode = wecom.sendCard(delivery);
            deliveries.markSent(delivery.deliveryId(), responseCode.orElse(null), clock.instant());
        } catch (RuntimeException failed) {
            String error = describe(failed);
            Optional<Instant> next = RetrySchedule.afterFailure(delivery.attemptCount(), clock.instant());
            if (next.isPresent()) {
                log.warn("WeCom card {} attempt {} failed, retry at {}: {}", delivery.deduplicationKey(), delivery.attemptCount(),
                        next.get(), error);
                deliveries.markRetry(delivery.deliveryId(), next.get(), error);
            } else {
                log.error("WeCom card {} is a dead letter after {} attempts: {}", delivery.deduplicationKey(), delivery.attemptCount(),
                        error);
                deliveries.markDeadLetter(delivery.deliveryId(), error);
            }
        }
    }

    private static String describe(RuntimeException failed) {
        String message = failed.getMessage();
        String text = failed.getClass().getSimpleName() + (message == null ? "" : ": " + message);
        return text.length() > 500 ? text.substring(0, 500) : text;
    }
}
