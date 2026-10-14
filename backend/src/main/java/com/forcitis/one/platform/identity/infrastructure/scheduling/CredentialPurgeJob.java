package com.forcitis.one.platform.identity.infrastructure.scheduling;

import com.forcitis.one.platform.identity.application.PurgeExpiredCredentials;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "forcitis.platform.scheduling.jobs-enabled", havingValue = "true", matchIfMissing = true)
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
