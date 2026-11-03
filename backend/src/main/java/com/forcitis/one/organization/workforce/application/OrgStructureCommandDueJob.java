package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.BusinessZone;
import com.forcitis.one.organization.workforce.domain.OrgStructureCommand;
import com.forcitis.one.organization.workforce.domain.OrgStructureCommandRepository;
import com.forcitis.one.organization.workforce.api.OrgStructureCommandKind;
import java.time.Clock;
import java.time.LocalDate;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Daily, after the due personnel commands and before the countersign deadline job: turns the pending org-structure commands that are now due effective and starts the due merges
 * (ADR-0068 items 1.8 and 15.2). The job judges nothing and reads never depend on it: the versions are in force on their day whether it ran or not, a missed run only delays the
 * events. One transaction per command (a merge runs its own steps), so one failure never stops the others; a failure is an alert, and a command still pending is tried again at the next run. A merge that
 * started and then failed to move somebody stays in progress and is continued by {@code executeMerge}, not by this job. A command found past its day is alerted and applied in the same run. The run is safe beside the countersign deadline job and a second run of itself because both take the same row locks ({@link ApplyDueOrgCommand}).
 */
@Component
public class OrgStructureCommandDueJob {
    private static final Logger LOG = LoggerFactory.getLogger(OrgStructureCommandDueJob.class);

    private final OrgStructureCommandRepository commands;
    private final ApplyDueOrgCommand apply;
    private final OrgMergeExecution merges;
    private final Clock clock;
    private final BusinessZone zone;

    OrgStructureCommandDueJob(OrgStructureCommandRepository commands, ApplyDueOrgCommand apply, OrgMergeExecution merges, Clock clock, BusinessZone zone) {
        this.commands = commands;
        this.apply = apply;
        this.merges = merges;
        this.clock = clock;
        this.zone = zone;
    }

    @Scheduled(cron = "0 7 0 * * *", zone = "Asia/Shanghai")
    @SchedulerLock(name = "organization.workforce.org.structure.command.due", lockAtMostFor = "PT10M")
    public void run() {
        LocalDate today = zone.dateOf(clock.instant());
        for (OrgStructureCommand command : commands.due(today)) {
            if (command.effectiveDate().isBefore(today)) {
                LOG.error("ALERT organization.workforce org-structure command {} due on {} was found pending on {}: the job missed its day, it takes effect now", command.id().value(),
                        command.effectiveDate(), today);
            }
            try {
                if (command.kind() == OrgStructureCommandKind.MERGE) {
                    merges.execute(command.id());
                } else {
                    apply.execute(command.id());
                }
            } catch (RuntimeException failure) {
                LOG.error("ALERT organization.workforce due org-structure command {} not fully executed; if it is still pending the next run tries again", command.id().value(), failure);
            }
        }
    }
}
