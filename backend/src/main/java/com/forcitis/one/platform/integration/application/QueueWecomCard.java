package com.forcitis.one.platform.integration.application;

import com.forcitis.one.kernel.WecomUserId;
import com.forcitis.one.platform.integration.api.WecomCard;
import com.forcitis.one.platform.integration.domain.DeliveryStatus;
import com.forcitis.one.platform.integration.domain.NewDelivery;
import com.forcitis.one.platform.integration.domain.OutboundDeliveryRepository;
import com.forcitis.one.platform.integration.domain.RecipientPolicy;
import java.time.Clock;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QueueWecomCard {

    private static final Logger log = LoggerFactory.getLogger(QueueWecomCard.class);

    private final OutboundDeliveryRepository deliveries;
    private final RecipientPolicy policy;
    private final Clock clock;

    QueueWecomCard(OutboundDeliveryRepository deliveries, RecipientPolicy policy, ObjectProvider<Clock> clock) {
        this.deliveries = deliveries;
        this.policy = policy;
        this.clock = Clocks.from(clock);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void execute(WecomCard card) {
        List<WecomUserId> asked = card.wecomUserIds();
        if (asked.isEmpty()) {
            log.warn("WeCom card {} has no recipient with a WeCom user id: not sent, recorded as failed", card.deduplicationKey());
            store(card, List.of(), List.of(), DeliveryStatus.FAILED, "no recipient with a WeCom user id");
            return;
        }
        RecipientPolicy.Decision decision = policy.decide(asked);
        if (!decision.skipped().isEmpty()) {
            log.info("WeCom card {}: {} recipient(s) are not on the allow list and are skipped", card.deduplicationKey(),
                    decision.skipped().size());
        }
        if (decision.allowed().isEmpty()) {
            store(card, List.of(), decision.skipped(), DeliveryStatus.SKIPPED, "no recipient on the allow list");
            return;
        }
        store(card, decision.allowed(), decision.skipped(), DeliveryStatus.PENDING, null);
    }

    private void store(WecomCard card, List<WecomUserId> recipients, List<WecomUserId> skipped, DeliveryStatus status, String reason) {
        boolean stored = deliveries.insert(new NewDelivery(card.deduplicationKey(), recipients, skipped, card.title(),
                card.description(), card.targetUrl(), status, reason), clock.instant());
        if (!stored) {
            log.info("WeCom card {} was already queued: ignored", card.deduplicationKey());
        }
    }
}
