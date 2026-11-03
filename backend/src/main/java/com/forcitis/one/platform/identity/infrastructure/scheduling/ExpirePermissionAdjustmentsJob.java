package com.forcitis.one.platform.identity.infrastructure.scheduling;

import com.forcitis.one.platform.identity.application.ExpirePermissionAdjustments;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** One node a day, shortly after midnight of the business day (ShedLock). */
@Component
@ConditionalOnProperty(name = "forcitis.platform.scheduling.jobs-enabled", havingValue = "true", matchIfMissing = true)
class ExpirePermissionAdjustmentsJob {

    private final ExpirePermissionAdjustments job;

    ExpirePermissionAdjustmentsJob(ExpirePermissionAdjustments job) {
        this.job = job;
    }

    @Scheduled(cron = "0 5 0 * * *", zone = "Asia/Shanghai")
    @SchedulerLock(name = "platform.identity.permission.adjustment.expire", lockAtMostFor = "PT10M")
    void run() {
        job.execute();
    }
}
