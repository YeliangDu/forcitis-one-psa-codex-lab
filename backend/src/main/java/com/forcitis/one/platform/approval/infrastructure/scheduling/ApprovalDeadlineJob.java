package com.forcitis.one.platform.approval.infrastructure.scheduling;

import com.forcitis.one.platform.approval.application.EnforceDeadlines;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** One node at a time (ShedLock). A task that cannot be escalated stays due and is tried again on the next run. */
@Component
@ConditionalOnProperty(prefix = "forcitis.platform.approval.deadline", name = "enabled", havingValue = "true")
class ApprovalDeadlineJob {

    private final EnforceDeadlines enforceDeadlines;

    ApprovalDeadlineJob(EnforceDeadlines enforceDeadlines) {
        this.enforceDeadlines = enforceDeadlines;
    }

    @Scheduled(cron = "${forcitis.platform.approval.deadline.cron}")
    @SchedulerLock(name = "platform.approval.deadline.enforce", lockAtMostFor = "PT4M")
    void run() {
        enforceDeadlines.execute();
    }
}
