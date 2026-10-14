package com.forcitis.one.platform.integration.infrastructure.wecom;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.platform.integration.application.DeliverWecomCards;
import java.time.Duration;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WecomDeliveryJobTest {

    @Test
    @DisplayName("ARCH 3.7 the job lock outlives the claim lease of a full batch, so no second node starts while claims are still valid")
    void lockAtMostForCoversTheLease() throws Exception {
        SchedulerLock lock = WecomDeliveryJob.class.getDeclaredMethod("deliverDueCards").getAnnotation(SchedulerLock.class);

        assertThat(Duration.parse(lock.lockAtMostFor())).isGreaterThanOrEqualTo(DeliverWecomCards.LEASE);
    }
}
