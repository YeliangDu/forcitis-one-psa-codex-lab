package com.forcitis.one.platform.identity.infrastructure.scheduling;

import com.forcitis.one.platform.identity.application.CheckEmergencyAdmins;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** One node a day (ShedLock). On unless the job's own switch or the global job switch says otherwise; the tests switch it off. */
@Component
@ConditionalOnProperty(name = {"forcitis.platform.scheduling.jobs-enabled", "forcitis.platform.identity.emergency.check.enabled"},
        havingValue = "true", matchIfMissing = true)
class EmergencyAdminCheckJob {

    private final CheckEmergencyAdmins check;

    EmergencyAdminCheckJob(CheckEmergencyAdmins check) {
        this.check = check;
    }

    @Scheduled(cron = "${forcitis.platform.identity.emergency.check.cron}")
    @SchedulerLock(name = "platform.identity.emergency.admin.check")
    void checkEmergencyAdmins() {
        check.execute();
    }
}
