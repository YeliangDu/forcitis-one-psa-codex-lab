package com.forcitis.one.platform.integration.application;

import com.forcitis.one.kernel.Uuids;
import com.forcitis.one.kernel.WorkContext;
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
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
                      TransactionTemplate transaction, Clock clock) {
        this.deliveries = deliveries;
        this.handled = handled;
        this.wecom = wecom;
        this.transaction = transaction;
        this.clock = clock;
    }

    public int execute() {
        List<OutboundDelivery> due = deliveries.claimDue(clock.instant(), LEASE, BATCH_SIZE);
        due.forEach(this::deliver);
        return due.size();
    }

    private void deliver(OutboundDelivery delivery) {
        UUID correlationId = delivery.correlation() != null ? delivery.correlation() : Uuids.timeOrdered(clock);
        try (WorkContext.Scope scope = WorkContext.enter(correlationId, null, WorkContext.systemActor().orElse("platform.integration.wecom.delivery"))) {
            deliverInContext(delivery);
        }
    }

    private void deliverInContext(OutboundDelivery delivery) {
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
            delivery.skip("handled before it was delivered");
            transaction.executeWithoutResult(status -> {
                if (deliveries.save(delivery)) {
                    recordResultOn(delivery.deliveryId().value(), "NOT_APPLIED: handled before it was delivered");
                }
            });
            return;
        }
        Optional<String> responseCode = wecom.sendCard(delivery);
        delivery.markSent(responseCode.orElse(null), clock.instant());
        transaction.executeWithoutResult(status -> {
            if (deliveries.save(delivery)) {
                handled.afterSent(delivery.deliveryId());
            } else {
                staleClaim(delivery);
            }
        });
    }

    private void replaceCard(OutboundDelivery delivery) {
        OutboundDelivery card = deliveries.findById(delivery.target().value()).orElse(null);
        if (card == null || card.responseCode() == null) {
            delivery.skip("the card to replace has no response code");
            transaction.executeWithoutResult(status -> {
                if (deliveries.save(delivery)) {
                    recordResultOn(delivery.target().value(), "NOT_APPLIED: the card has no response code");
                }
            });
            return;
        }
        wecom.replaceWithHandledNote(card, delivery);
        delivery.markSent(null, clock.instant());
        transaction.executeWithoutResult(status -> {
            if (deliveries.save(delivery)) {
                recordResultOn(delivery.target().value(), "UPDATED");
            } else {
                staleClaim(delivery);
            }
        });
    }

    private void fail(OutboundDelivery delivery, RuntimeException failed) {
        String error = errorText(failed);
        Optional<Instant> next = RetrySchedule.afterFailure(delivery.attemptCount(), clock.instant());
        if (next.isPresent()) {
            log.warn("WeCom delivery {} attempt {} failed, retry at {}: {}", delivery.subject(), delivery.attemptCount(),
                    next.get(), error);
            delivery.scheduleRetry(next.get(), error);
            if (!deliveries.save(delivery)) {
                staleClaim(delivery);
            }
            return;
        }
        log.error("WeCom delivery {} is a dead letter after {} attempts: {}", delivery.subject(), delivery.attemptCount(), error);
        delivery.deadLetter(error);
        transaction.executeWithoutResult(status -> {
            if (deliveries.save(delivery)) {
                if (delivery.kind() == DeliveryKind.HANDLED_NOTE) {
                    recordResultOn(delivery.target().value(), "FAILED: " + error);
                }
            } else {
                staleClaim(delivery);
            }
        });
    }

    private void recordResultOn(UUID cardId, String result) {
        deliveries.lockById(cardId).ifPresent(card -> {
            card.recordHandledResult(result);
            deliveries.saveHandled(card);
        });
    }

    private static void staleClaim(OutboundDelivery delivery) {
        log.warn("WeCom delivery {} attempt {} no longer holds the claim: its result is dropped", delivery.subject(),
                delivery.attemptCount());
    }

    private static String errorText(RuntimeException failed) {
        String message = failed.getMessage();
        String text = failed.getClass().getSimpleName() + (message == null ? "" : ": " + message);
        return text.length() > 500 ? text.substring(0, 500) : text;
    }
}
