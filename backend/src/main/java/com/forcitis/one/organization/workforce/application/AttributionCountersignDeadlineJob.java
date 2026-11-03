package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.BusinessZone;
import com.forcitis.one.organization.workforce.api.AttributionCountersignId;
import com.forcitis.one.organization.workforce.domain.AttributionCountersignRepository;
import java.time.Clock;
import java.time.LocalDate;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Daily, after the due personnel commands: raises every open countersign request past its due date to the escalation level due on the business day and notifies for that level
 * (ADR-0073 item 3.6, one-design rulings on #896). One transaction per request, so one failure never stops the others; a failure is an alert and the request is tried again at
 * the next run. Decided and lapsed requests are not read; the job never decides, approves, withdraws or lapses anything.
 */
@Component
public class AttributionCountersignDeadlineJob {
    private static final Logger LOG = LoggerFactory.getLogger(AttributionCountersignDeadlineJob.class);

    private final AttributionCountersignRepository countersigns;
    private final EscalateCountersign escalate;
    private final Clock clock;
    private final BusinessZone zone;

    AttributionCountersignDeadlineJob(AttributionCountersignRepository countersigns, EscalateCountersign escalate, Clock clock, BusinessZone zone) {
        this.countersigns = countersigns;
        this.escalate = escalate;
        this.clock = clock;
        this.zone = zone;
    }

    @Scheduled(cron = "0 10 0 * * *", zone = "Asia/Shanghai")
    @SchedulerLock(name = "organization.workforce.attribution-countersign.deadline", lockAtMostFor = "PT10M")
    public void run() {
        runOnce();
    }

    public void runOnce() {
        LocalDate today = zone.today(clock);
        for (AttributionCountersignId id : countersigns.overdueBelowLastLevel(today)) {
            try {
                escalate.execute(id, today);
            } catch (RuntimeException failure) {
                LOG.error("ALERT organization.workforce countersign request {} not escalated, retried at the next run", id.value(), failure);
            }
        }
    }
}
