package com.forcitis.one.platform.identity.infrastructure.scheduling;

import com.forcitis.one.platform.identity.application.PurgeExpiredSessions;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class SessionPurgeJob {

    private final PurgeExpiredSessions purge;

    SessionPurgeJob(PurgeExpiredSessions purge) {
        this.purge = purge;
    }

    @Scheduled(cron = "${forcitis.platform.identity.session-purge.cron}")
    @SchedulerLock(name = "platform.identity.session.purge", lockAtMostFor = "PT10M")
    void purgeExpiredSessions() {
        purge.execute();
    }
}
