package com.forcitis.one.platform.integration.application;

import com.forcitis.one.kernel.ExternalSystemUnavailableException;
import com.forcitis.one.platform.integration.domain.DeliveryStatus;
import com.forcitis.one.platform.integration.domain.OutboundDelivery;
import com.forcitis.one.platform.integration.domain.OutboundDeliveryRepository;
import com.forcitis.one.platform.integration.domain.WecomClient;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class MarkWecomCardHandled {

    private static final Logger log = LoggerFactory.getLogger(MarkWecomCardHandled.class);

    private final OutboundDeliveryRepository deliveries;
    private final WecomClient wecom;

    MarkWecomCardHandled(OutboundDeliveryRepository deliveries, WecomClient wecom) {
        this.deliveries = deliveries;
        this.wecom = wecom;
    }

    public void execute(String deduplicationKey, String handledNote) {
        Optional<OutboundDelivery> found = deliveries.findByKey(deduplicationKey);
        if (found.isEmpty()) {
            log.warn("No WeCom delivery for {}: nothing to mark as handled", deduplicationKey);
            return;
        }
        OutboundDelivery delivery = found.get();
        if (delivery.handledNote() != null) {
            return;
        }
        if (delivery.status() != DeliveryStatus.SENT || delivery.responseCode() == null) {
            String reason = delivery.status() != DeliveryStatus.SENT ? "the card is " + delivery.status() + ", not sent"
                    : "WeCom returned no response code when the card was sent";
            log.info("WeCom card {} is not replaced: {}", deduplicationKey, reason);
            deliveries.recordHandled(deduplicationKey, handledNote, "NOT_APPLIED: " + reason);
            return;
        }
        try {
            wecom.replaceWithHandledNote(delivery, handledNote);
            deliveries.recordHandled(deduplicationKey, handledNote, "UPDATED");
        } catch (ExternalSystemUnavailableException failed) {
            log.warn("WeCom card {} could not be replaced: {}", deduplicationKey, failed.getMessage());
            deliveries.recordHandled(deduplicationKey, handledNote, "FAILED: " + failed.getMessage());
        }
    }
}
