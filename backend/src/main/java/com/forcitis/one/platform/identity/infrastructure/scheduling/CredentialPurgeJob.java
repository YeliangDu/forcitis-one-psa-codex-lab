package com.forcitis.one.platform.identity.infrastructure.scheduling;

import com.forcitis.one.platform.identity.application.PurgeExpiredCredentials;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Every 10 minutes (single node, ShedLock): removes expired OAuth states and used-ticket records. */
@Component
class CredentialPurgeJob {

    private final PurgeExpiredCredentials purge;

    CredentialPurgeJob(PurgeExpiredCredentials purge) {
        this.purge = purge;
    }

    @Scheduled(cron = "${forcitis.platform.identity.credential-purge.cron:0 */10 * * * *}")
    @SchedulerLock(name = "platform.identity.credential.purge")
    void purgeExpiredCredentials() {
        purge.execute();
    }
}
