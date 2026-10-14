package com.forcitis.one.platform.integration.application;

import com.forcitis.one.kernel.Uuids;
import com.forcitis.one.kernel.WorkContext;
import com.forcitis.one.platform.integration.api.WecomCard;
import com.forcitis.one.platform.integration.domain.DeliveryStatus;
import com.forcitis.one.platform.integration.domain.OutboundDelivery;
import com.forcitis.one.platform.integration.domain.OutboundDeliveryRepository;
import com.forcitis.one.platform.integration.domain.RecipientPolicy;
import java.time.Clock;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QueueWecomCard {

    private static final Logger log = LoggerFactory.getLogger(QueueWecomCard.class);

    private final OutboundDeliveryRepository deliveries;
    private final RecipientPolicy policy;
    private final Clock clock;

    QueueWecomCard(OutboundDeliveryRepository deliveries, RecipientPolicy policy, Clock clock) {
        this.deliveries = deliveries;
        this.policy = policy;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void execute(WecomCard card) {
        UUID correlationId = WorkContext.correlationId().orElseGet(() -> Uuids.timeOrdered(clock));
        OutboundDelivery delivery = OutboundDelivery.queue(card.deduplicationKey(), card.wecomUserIds(), policy, card.title(),
                card.description(), card.targetUrl(), correlationId, clock.instant());
        if (delivery.status() == DeliveryStatus.FAILED) {
            log.warn("WeCom card {} has no recipient with a WeCom user id: not sent, recorded as failed", card.deduplicationKey());
        } else if (!delivery.skippedRecipients().isEmpty()) {
            log.info("WeCom card {}: {} recipient(s) are not on the allow list and are skipped", card.deduplicationKey(),
                    delivery.skippedRecipients().size());
        }
        if (!deliveries.insert(delivery, clock.instant())) {
            log.info("WeCom card {} was already queued: ignored", card.deduplicationKey());
        }
    }
}
