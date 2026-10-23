package com.forcitis.one.platform.file.infrastructure.scheduling;

import com.forcitis.one.platform.file.application.ExpireUnboundFiles;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "forcitis.platform.file.store", name = "enabled", havingValue = "true")
class UnboundFileExpiryJob {
    private final ExpireUnboundFiles expire;

    UnboundFileExpiryJob(ExpireUnboundFiles expire) {
        this.expire = expire;
    }

    @Scheduled(fixedDelayString = "${forcitis.platform.file.store.unbound-expiry-interval}")
    @SchedulerLock(name = "platform.file.unbound.expire", lockAtMostFor = "PT30M")
    void expireUnboundFiles() {
        expire.execute();
    }
}
