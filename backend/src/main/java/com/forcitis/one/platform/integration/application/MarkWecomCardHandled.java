package com.forcitis.one.platform.integration.application;

import com.forcitis.one.platform.integration.domain.DeliveryKind;
import com.forcitis.one.platform.integration.domain.DeliveryStatus;
import com.forcitis.one.platform.integration.domain.OutboundDelivery;
import com.forcitis.one.platform.integration.domain.OutboundDeliveryRepository;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.Uuids;
import com.forcitis.one.platform.integration.api.DeliverySubject;
import com.forcitis.one.platform.integration.api.IntegrationError;
import com.forcitis.one.platform.integration.domain.OutboundDeliveryId;
import com.forcitis.one.kernel.WorkContext;
import java.time.Clock;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Only records the note and queues the replacement; the WeCom call is a delivery of its own, retried like a card. */
@Service
public class MarkWecomCardHandled {

    private static final Logger log = LoggerFactory.getLogger(MarkWecomCardHandled.class);

    private final OutboundDeliveryRepository deliveries;
    private final Clock clock;

    MarkWecomCardHandled(OutboundDeliveryRepository deliveries, Clock clock) {
        this.deliveries = deliveries;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void execute(DeliverySubject subject, String handledNote) {
        OutboundDelivery delivery = deliveries.lockCard(subject).orElse(null);
        if (delivery == null) {
            log.warn("No WeCom delivery for {}: nothing to mark as handled", subject);
            return;
        }
        if (delivery.kind() != DeliveryKind.CARD || delivery.handledNote() != null) {
            return;
        }
        delivery.noteHandled(handledNote);
        if (delivery.status() != DeliveryStatus.SENT && delivery.status() != DeliveryStatus.PENDING) {
            String reason = "NOT_APPLIED: the card is " + delivery.status() + ", not sent";
            log.info("WeCom card {} is not replaced: {}", subject, reason);
            delivery.recordHandledResult(reason);
        }
        saveHandled(delivery);
        if (delivery.status() == DeliveryStatus.SENT) {
            queueReplacement(delivery, handledNote);
        }
    }

    /** Called in the transaction that marked the card sent: a note that arrived while the card was in flight is applied now. */
    void afterSent(OutboundDeliveryId deliveryId) {
        deliveries.lockById(deliveryId.value()).filter(d -> d.handledNote() != null)
                .ifPresent(sent -> queueReplacement(sent, sent.handledNote()));
    }

    private void saveHandled(OutboundDelivery delivery) {
        if (!deliveries.saveHandled(delivery)) {
            throw new DomainException(IntegrationError.PLATFORM_INTEGRATION_CONCURRENT_UPDATE, ErrorKind.CONFLICT,
                    "delivery " + delivery.subject() + " changed while it was being marked handled");
        }
    }

    private void queueReplacement(OutboundDelivery sent, String handledNote) {
        if (sent.responseCode() == null) {
            String reason = "NOT_APPLIED: WeCom returned no response code when the card was sent";
            log.info("WeCom card {} is not replaced: {}", sent.subject(), reason);
            sent.recordHandledResult(reason);
            saveHandled(sent);
            return;
        }
        UUID correlationId = WorkContext.correlationId().orElseGet(() -> Uuids.timeOrdered(clock));
        deliveries.insert(OutboundDelivery.handledNoteFor(sent, handledNote, correlationId, clock.instant()), clock.instant());
    }
}
