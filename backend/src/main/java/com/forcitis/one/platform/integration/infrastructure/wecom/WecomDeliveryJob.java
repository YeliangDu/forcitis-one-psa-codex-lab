package com.forcitis.one.platform.integration.infrastructure.wecom;

import com.forcitis.one.platform.integration.application.DeliverWecomCards;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "forcitis.platform.integration.wecom", name = "enabled", havingValue = "true")
class WecomDeliveryJob {

    private final DeliverWecomCards deliver;

    WecomDeliveryJob(DeliverWecomCards deliver) {
        this.deliver = deliver;
    }

    @Scheduled(initialDelayString = "${forcitis.platform.integration.wecom.delivery.interval}",
            fixedDelayString = "${forcitis.platform.integration.wecom.delivery.interval}")
    @SchedulerLock(name = "platform.integration.wecom.delivery.send", lockAtMostFor = "PT5M")
    void deliverDueCards() {
        deliver.execute();
    }
}
