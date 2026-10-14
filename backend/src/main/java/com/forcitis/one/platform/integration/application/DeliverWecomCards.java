package com.forcitis.one.platform.integration.application;

import com.forcitis.one.platform.integration.domain.DeliveryKind;
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
import org.springframework.transaction.support.TransactionTemplate;

/** The WeCom call never runs inside a database transaction; delivery is at-least-once, and a stale claim cannot overwrite a newer one. */
@Service
public class DeliverWecomCards {

    private static final Logger log = LoggerFactory.getLogger(DeliverWecomCards.class);
    public static final int BATCH_SIZE = 20;
    /** Token request plus send, each repeated once after a rejected token, at the longest connect and read timeouts allowed. */
    public static final Duration WORST_CASE_PER_DELIVERY = Duration.ofMinutes(1);
    public static final Duration LEASE = WORST_CASE_PER_DELIVERY.multipliedBy(BATCH_SIZE);

    private final OutboundDeliveryRepository deliveries;
    private final MarkWecomCardHandled handled;
    private final WecomClient wecom;
    private final TransactionTemplate transaction;
    private final Clock clock;

    DeliverWecomCards(OutboundDeliveryRepository deliveries, MarkWecomCardHandled handled, WecomClient wecom,
                      TransactionTemplate transaction, ObjectProvider<Clock> clock) {
        this.deliveries = deliveries;
        this.handled = handled;
        this.wecom = wecom;
        this.transaction = transaction;
        this.clock = Clocks.from(clock);
    }

    public int execute() {
        List<OutboundDelivery> due = deliveries.claimDue(clock.instant(), LEASE, BATCH_SIZE);
        due.forEach(this::deliver);
        return due.size();
    }

    private void deliver(OutboundDelivery delivery) {
        try {
            if (delivery.kind() == DeliveryKind.HANDLED_NOTE) {
                replaceCard(delivery);
            } else {
                sendCard(delivery);
            }
        } catch (RuntimeException failed) {
            fail(delivery, failed);
        }
    }

    private void sendCard(OutboundDelivery delivery) {
        if (delivery.handledNote() != null) {
            transaction.executeWithoutResult(status -> {
                if (deliveries.markSkipped(delivery, "handled before it was delivered")) {
                    deliveries.recordHandledResult(delivery.deduplicationKey(), "NOT_APPLIED: handled before it was delivered");
                }
            });
            return;
        }
        Optional<String> responseCode = wecom.sendCard(delivery);
        transaction.executeWithoutResult(status -> {
            if (deliveries.markSent(delivery, responseCode.orElse(null), clock.instant())) {
                handled.afterSent(delivery.deduplicationKey());
            } else {
                staleClaim(delivery);
            }
        });
    }

    private void replaceCard(OutboundDelivery delivery) {
        OutboundDelivery card = deliveries.findByKey(delivery.targetKey()).orElse(null);
        if (card == null || card.responseCode() == null) {
            transaction.executeWithoutResult(status -> {
                if (deliveries.markSkipped(delivery, "the card to replace has no response code")) {
                    deliveries.recordHandledResult(delivery.targetKey(), "NOT_APPLIED: the card has no response code");
                }
            });
            return;
        }
        wecom.replaceWithHandledNote(card, delivery.description());
        transaction.executeWithoutResult(status -> {
            if (deliveries.markSent(delivery, null, clock.instant())) {
                deliveries.recordHandledResult(delivery.targetKey(), "UPDATED");
            } else {
                staleClaim(delivery);
            }
        });
    }

    private void fail(OutboundDelivery delivery, RuntimeException failed) {
        String error = describe(failed);
        Optional<Instant> next = RetrySchedule.afterFailure(delivery.attemptCount(), clock.instant());
        if (next.isPresent()) {
            log.warn("WeCom delivery {} attempt {} failed, retry at {}: {}", delivery.deduplicationKey(), delivery.attemptCount(),
                    next.get(), error);
            if (!deliveries.markRetry(delivery, next.get(), error)) {
                staleClaim(delivery);
            }
            return;
        }
        log.error("WeCom delivery {} is a dead letter after {} attempts: {}", delivery.deduplicationKey(), delivery.attemptCount(), error);
        transaction.executeWithoutResult(status -> {
            if (deliveries.markDeadLetter(delivery, error)) {
                if (delivery.kind() == DeliveryKind.HANDLED_NOTE) {
                    deliveries.recordHandledResult(delivery.targetKey(), "FAILED: " + error);
                }
            } else {
                staleClaim(delivery);
            }
        });
    }

    private static void staleClaim(OutboundDelivery delivery) {
        log.warn("WeCom delivery {} attempt {} no longer holds the claim: its result is dropped", delivery.deduplicationKey(),
                delivery.attemptCount());
    }

    private static String describe(RuntimeException failed) {
        String message = failed.getMessage();
        String text = failed.getClass().getSimpleName() + (message == null ? "" : ": " + message);
        return text.length() > 500 ? text.substring(0, 500) : text;
    }
}
