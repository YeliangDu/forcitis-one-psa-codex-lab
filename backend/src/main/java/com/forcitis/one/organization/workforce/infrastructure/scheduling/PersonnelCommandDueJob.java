package com.forcitis.one.organization.workforce.infrastructure.scheduling;

import com.forcitis.one.kernel.BusinessZone;
import com.forcitis.one.organization.workforce.api.PersonnelCommandId;
import com.forcitis.one.organization.workforce.application.ApplyDueSeparation;
import com.forcitis.one.organization.workforce.domain.PersonnelCommandRepository;
import java.time.Clock;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Daily, shortly after midnight of the business day: executes the separations that were released before their date and now are due (ADR-0068 item
 * 1.8). The name is provisional; the due-date execution of ordinary pending commands is card #933's. One transaction per command, so one failure never
 * stops the others; a failure is an alert and the command is tried again at the next run.
 */
@Component
public class PersonnelCommandDueJob {
    private static final Logger LOG = LoggerFactory.getLogger(PersonnelCommandDueJob.class);

    private final PersonnelCommandRepository commands;
    private final ApplyDueSeparation apply;
    private final Clock clock;
    private final BusinessZone zone;

    PersonnelCommandDueJob(PersonnelCommandRepository commands, ApplyDueSeparation apply, Clock clock, BusinessZone zone) {
        this.commands = commands;
        this.apply = apply;
        this.clock = clock;
        this.zone = zone;
    }

    @Scheduled(cron = "0 5 0 * * *", zone = "Asia/Shanghai")
    @SchedulerLock(name = "organization.workforce.personnel-command.due", lockAtMostFor = "PT10M")
    public void run() {
        for (PersonnelCommandId command : commands.releasedSeparationsDue(zone.dateOf(clock.instant()))) {
            try {
                apply.execute(command);
            } catch (RuntimeException failure) {
                LOG.error("ALERT organization.workforce due separation {} not executed, retried at the next run", command.value(), failure);
            }
        }
    }
}
